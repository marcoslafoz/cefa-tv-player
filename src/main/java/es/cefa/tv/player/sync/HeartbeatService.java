package es.cefa.tv.player.sync;

import es.cefa.tv.player.command.PlayerCommands;
import es.cefa.tv.player.discovery.ServerLocator;
import es.cefa.tv.player.pairing.Backoff;
import es.cefa.tv.player.pairing.PlayerPhase;
import es.cefa.tv.player.pairing.PlayerState;
import es.cefa.tv.player.pairing.StateStore;
import es.cefa.tv.player.status.PlayerStatus;
import es.cefa.tv.player.storage.FileStore;
import es.cefa.tv.player.storage.ManifestStore;
import es.cefa.tv.player.sync.dto.HeartbeatRequest;
import es.cefa.tv.player.sync.dto.HeartbeatResponse;
import es.cefa.tv.player.telemetry.Telemetry;
import es.cefa.tv.player.telemetry.TelemetryService;
import jakarta.annotation.PreDestroy;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Latido periódico por HTTP: informa del estado, recibe la versión deseada y las órdenes. Si el
 * servidor no responde reintenta con espera creciente y, cada pocos fallos, vuelve a descubrirlo
 * por si ha cambiado de dirección.
 */
@Slf4j
@Service
public class HeartbeatService {

  private static final int FAILURES_BEFORE_REDISCOVERY = 3;

  private final ServerApi api;
  private final ManifestStore manifests;
  private final FileStore files;
  private final PlayerStatus status;
  private final SyncService sync;
  private final PlayerCommands commands;
  private final TelemetryService telemetry;
  private final ServerLocator locator;
  private final StateStore stateStore;
  private final PlayerSocket socket;
  private final Clock clock;
  private final Backoff backoff = new Backoff();
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "heartbeat");
            thread.setDaemon(true);
            return thread;
          });
  private volatile boolean active;
  private volatile int intervalSec = 30;
  private int consecutiveFailures;

  /**
   * Crea el servicio.
   *
   * @param api cliente del servidor
   * @param manifests manifest aplicado
   * @param files almacén de ficheros
   * @param status estado compartido
   * @param sync servicio de sincronización
   * @param commands ejecutor de órdenes
   * @param telemetry telemetría del equipo y las pantallas
   * @param locator localizador del servidor, para redescubrirlo
   * @param stateStore estado persistente, donde se guarda la nueva dirección
   * @param socket WebSocket con el servidor, que se reconecta al cambiar de dirección
   * @param clock reloj
   */
  public HeartbeatService(
      ServerApi api,
      ManifestStore manifests,
      FileStore files,
      PlayerStatus status,
      SyncService sync,
      PlayerCommands commands,
      TelemetryService telemetry,
      ServerLocator locator,
      StateStore stateStore,
      PlayerSocket socket,
      Clock clock) {
    this.api = api;
    this.manifests = manifests;
    this.files = files;
    this.status = status;
    this.sync = sync;
    this.commands = commands;
    this.telemetry = telemetry;
    this.locator = locator;
    this.stateStore = stateStore;
    this.socket = socket;
    this.clock = clock;
  }

  /**
   * Arranca los latidos si no estaban en marcha.
   *
   * @param intervalSec segundos entre latidos cuando todo va bien
   */
  public synchronized void start(int intervalSec) {
    if (active) {
      return;
    }
    active = true;
    this.intervalSec = Math.max(5, intervalSec);
    consecutiveFailures = 0;
    backoff.reset();
    scheduler.execute(this::tick);
  }

  /** Detiene los latidos; pueden volver a arrancarse con {@link #start(int)}. */
  public synchronized void stop() {
    active = false;
  }

  /** Cierra el planificador al apagar la aplicación. */
  @PreDestroy
  public void shutdown() {
    active = false;
    scheduler.shutdownNow();
  }

  private void tick() {
    if (!active) {
      return;
    }
    boolean ok = beat();
    if (!active) {
      return;
    }
    long delayMs;
    if (ok) {
      consecutiveFailures = 0;
      backoff.reset();
      delayMs = intervalSec * 1000L;
    } else {
      consecutiveFailures++;
      delayMs = backoff.nextDelayMs();
      status.connectionFailure(
          "Sin comunicación con el servidor", clock.instant().plusMillis(delayMs));
      if (consecutiveFailures % FAILURES_BEFORE_REDISCOVERY == 0) {
        rediscover();
      }
    }
    scheduler.schedule(this::tick, delayMs, TimeUnit.MILLISECONDS);
  }

  /**
   * Envía un latido y aplica la respuesta.
   *
   * @return {@code true} si el servidor ha respondido
   */
  public boolean beat() {
    Telemetry sample = telemetry.snapshot();
    HeartbeatRequest request =
        new HeartbeatRequest(
            manifests.appliedVersion(),
            status.downloadingVersion(),
            status.heartbeatState(),
            status.currentPlaylistId(),
            status.currentAssetId(),
            files.freeMegabytes(),
            status.uptimeSec(),
            localIp(),
            status.lastError(),
            sample.system(),
            sample.displays(),
            status.version(),
            status.build());
    try {
      HeartbeatResponse response = api.heartbeat(request);
      status.connected(true);
      status.phase(PlayerPhase.ONLINE, "Conectado con " + status.serverUrl());
      sync.onDesiredVersion(response.desiredVersion());
      if (response.config() != null) {
        commands.applyPreferredOutput(response.config().preferredOutput());
        if (response.config().idleLogo() != null) {
          commands.applyIdleLogo(response.config().idleLogo());
        }
      }
      response
          .commandsOrEmpty()
          .forEach(
              command ->
                  commands.execute(
                      command.type(),
                      command.output(),
                      command.width() == null ? 0 : command.width(),
                      command.height() == null ? 0 : command.height()));
      return true;
    } catch (ServerException e) {
      status.connected(true);
      log.warn("Latido rechazado: {}", e.getMessage());
      if (e.getStatus() == 401) {
        commands.execute("UNENROLL");
      }
      return true;
    } catch (RuntimeException e) {
      status.connected(false);
      status.phase(PlayerPhase.CONNECTING, "Reconectando con " + status.serverUrl());
      log.info("Latido sin respuesta: {}", e.getMessage());
      return false;
    }
  }

  private void rediscover() {
    if (locator.isFixed()) {
      return;
    }
    Optional<String> found = locator.discoverOnce();
    if (found.isEmpty() || found.get().equals(api.baseUrl())) {
      return;
    }
    Optional<PlayerState> saved = stateStore.load();
    if (saved.isEmpty()) {
      return;
    }
    PlayerState old = saved.get();
    log.warn("El servidor ha cambiado de dirección: {} -> {}", old.serverUrl(), found.get());
    stateStore.save(
        new PlayerState(
            found.get(), old.playerId(), old.deviceToken(), old.name(), old.pollIntervalSec()));
    api.configure(found.get(), old.deviceToken());
    status.paired(found.get(), old.playerId(), old.name());
    socket.stop();
    socket.start(found.get(), old.deviceToken());
  }

  static String localIp() {
    try {
      for (NetworkInterface nic : NetworkInterface.networkInterfaces().toList()) {
        if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
          continue;
        }
        for (InetAddress address : nic.inetAddresses().toList()) {
          if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
            return address.getHostAddress();
          }
        }
      }
    } catch (java.net.SocketException e) {
      log.debug("No se pudo determinar la IP local: {}", e.getMessage());
    }
    return null;
  }
}
