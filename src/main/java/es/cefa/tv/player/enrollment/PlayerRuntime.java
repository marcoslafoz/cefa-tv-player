package es.cefa.tv.player.enrollment;

import com.sun.management.OperatingSystemMXBean;
import es.cefa.tv.player.discovery.DiscoveryClient;
import es.cefa.tv.player.shared.config.PlayerProperties;
import es.cefa.tv.player.status.PlayerStatus;
import es.cefa.tv.player.sync.HeartbeatService;
import es.cefa.tv.player.sync.PlayerSocket;
import es.cefa.tv.player.sync.ServerApi;
import es.cefa.tv.player.sync.ServerException;
import es.cefa.tv.player.sync.SyncService;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ciclo de vida del reproductor: obtiene el alta (descubriendo el servidor si hace falta) y, una
 * vez dado de alta, pone en marcha la sincronización, los latidos y el WebSocket.
 */
@Slf4j
@Component
public class PlayerRuntime {

  private static final long WAIT_KEY_MS = 15000;
  private static final long WAIT_NOT_FOUND_MS = 10000;
  private static final long WAIT_INVALID_KEY_MS = 30000;
  private static final long WAIT_ERROR_MS = 15000;
  private static final int DISCOVERY_ATTEMPTS = 3;
  private static final Duration DISCOVERY_WAIT = Duration.ofSeconds(3);

  private final PlayerProperties properties;
  private final StateStore stateStore;
  private final DiscoveryClient discovery;
  private final ServerApi api;
  private final PlayerStatus status;
  private final SyncService sync;
  private final HeartbeatService heartbeat;
  private final PlayerSocket socket;
  private final String version;

  /**
   * Crea el ciclo de vida.
   *
   * @param properties configuración del reproductor
   * @param stateStore estado persistente
   * @param discovery cliente de descubrimiento
   * @param api cliente del servidor
   * @param status estado compartido
   * @param sync sincronización
   * @param heartbeat latidos
   * @param socket WebSocket con el servidor
   * @param version versión del reproductor
   */
  public PlayerRuntime(
      PlayerProperties properties,
      StateStore stateStore,
      DiscoveryClient discovery,
      ServerApi api,
      PlayerStatus status,
      SyncService sync,
      HeartbeatService heartbeat,
      PlayerSocket socket,
      @Value("${info.app.version:dev}") String version) {
    this.properties = properties;
    this.stateStore = stateStore;
    this.discovery = discovery;
    this.api = api;
    this.status = status;
    this.sync = sync;
    this.heartbeat = heartbeat;
    this.socket = socket;
    this.version = version;
  }

  /** Arranca el proceso de alta en un hilo propio cuando la aplicación está lista. */
  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    Thread thread = new Thread(this::run, "runtime");
    thread.setDaemon(true);
    thread.start();
  }

  private void run() {
    try {
      PlayerState state = stateStore.load().orElse(null);
      while (state == null) {
        state = tryEnroll();
      }
      begin(state);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private PlayerState tryEnroll() throws InterruptedException {
    if (!properties.hasEnrollmentKey()) {
      status.phase("WAITING_KEY", "Falta la clave de alta (CEFA_TV_ENROLLMENT_KEY)");
      Thread.sleep(WAIT_KEY_MS);
      return null;
    }
    Optional<String> server = resolveServer();
    if (server.isEmpty()) {
      status.phase("DISCOVERING", "Buscando el servidor de CEFA TV en la red");
      Thread.sleep(WAIT_NOT_FOUND_MS);
      return null;
    }
    status.phase("ENROLLING", "Dando de alta en " + server.get());
    try {
      EnrollResponse response = api.enroll(server.get(), buildRequest());
      PlayerState state =
          new PlayerState(
              server.get(),
              response.playerId(),
              response.deviceToken(),
              response.name(),
              response.pollIntervalSec());
      stateStore.save(state);
      log.info("Alta correcta como {} ({})", state.name(), state.playerId());
      return state;
    } catch (ServerException e) {
      boolean invalid = "INVALID_ENROLLMENT_KEY".equals(e.getErrorCode()) || e.getStatus() == 401;
      status.phase(invalid ? "INVALID_KEY" : "ENROLL_FAILED", "Alta rechazada: " + e.getMessage());
      Thread.sleep(invalid ? WAIT_INVALID_KEY_MS : WAIT_ERROR_MS);
    } catch (RuntimeException e) {
      status.phase("ENROLL_FAILED", "No se pudo contactar con el servidor: " + e.getMessage());
      Thread.sleep(WAIT_ERROR_MS);
    }
    return null;
  }

  private Optional<String> resolveServer() {
    if (properties.hasServer()) {
      return Optional.of(DiscoveryClient.stripTrailingSlash(properties.server()));
    }
    status.phase("DISCOVERING", "Buscando el servidor de CEFA TV en la red");
    return discovery.discoverOnLan(DISCOVERY_ATTEMPTS, DISCOVERY_WAIT);
  }

  private void begin(PlayerState state) {
    api.configure(state.serverUrl(), state.deviceToken());
    status.enrolled(state.serverUrl(), state.playerId(), state.name());
    status.phase("RUNNING", "En funcionamiento");
    sync.requestSync(false);
    int interval =
        state.pollIntervalSec() > 0 ? state.pollIntervalSec() : properties.heartbeatSec();
    heartbeat.start(Math.min(interval, properties.heartbeatSec()));
    socket.start(state.serverUrl(), state.deviceToken());
  }

  EnrollRequest buildRequest() {
    String hostname =
        properties.playerName() != null && !properties.playerName().isBlank()
            ? properties.playerName()
            : localHostname();
    Map<String, Object> hardware = new LinkedHashMap<>();
    hardware.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
    hardware.put("arch", System.getProperty("os.arch"));
    hardware.put("cpus", Runtime.getRuntime().availableProcessors());
    if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os) {
      hardware.put("ramMb", os.getTotalMemorySize() / (1024 * 1024));
    }
    return new EnrollRequest(
        properties.enrollmentKey().trim(),
        hostname,
        hardware,
        resolution(),
        version,
        ZoneId.systemDefault().getId());
  }

  private static String resolution() {
    String value = System.getenv("CEFA_TV_RESOLUTION");
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    String[] parts = value.split("x");
    return parts.length >= 2 ? parts[0] + "x" + parts[1] : value;
  }

  private static String localHostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return "cefa-tv-player";
    }
  }
}
