package es.cefa.tv.player.sync;

import es.cefa.tv.player.command.PlayerCommands;
import es.cefa.tv.player.status.PlayerStatus;
import es.cefa.tv.player.storage.FileStore;
import es.cefa.tv.player.storage.ManifestStore;
import es.cefa.tv.player.sync.dto.HeartbeatRequest;
import es.cefa.tv.player.sync.dto.HeartbeatResponse;
import jakarta.annotation.PreDestroy;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Latido periódico por HTTP: informa del estado, recibe la versión deseada y las órdenes. */
@Slf4j
@Service
public class HeartbeatService {

  private final ServerApi api;
  private final ManifestStore manifests;
  private final FileStore files;
  private final PlayerStatus status;
  private final SyncService sync;
  private final PlayerCommands commands;
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "heartbeat");
            thread.setDaemon(true);
            return thread;
          });
  private boolean started;

  /**
   * Crea el servicio.
   *
   * @param api cliente del servidor
   * @param manifests manifest aplicado
   * @param files almacén de ficheros
   * @param status estado compartido
   * @param sync servicio de sincronización
   * @param commands ejecutor de órdenes
   */
  public HeartbeatService(
      ServerApi api,
      ManifestStore manifests,
      FileStore files,
      PlayerStatus status,
      SyncService sync,
      PlayerCommands commands) {
    this.api = api;
    this.manifests = manifests;
    this.files = files;
    this.status = status;
    this.sync = sync;
    this.commands = commands;
  }

  /**
   * Programa los latidos.
   *
   * @param intervalSec segundos entre latidos
   */
  public synchronized void start(int intervalSec) {
    if (started) {
      return;
    }
    started = true;
    scheduler.scheduleWithFixedDelay(this::beat, 0, Math.max(5, intervalSec), TimeUnit.SECONDS);
  }

  /** Detiene los latidos. */
  @PreDestroy
  public void stop() {
    scheduler.shutdownNow();
  }

  /** Envía un latido y procesa la respuesta; los errores de red solo marcan el estado. */
  public void beat() {
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
            status.lastError());
    try {
      HeartbeatResponse response = api.heartbeat(request);
      status.connected(true);
      sync.onDesiredVersion(response.desiredVersion());
      response.commandsOrEmpty().forEach(command -> commands.execute(command.type()));
    } catch (ServerException e) {
      status.connected(true);
      log.warn("Latido rechazado: {}", e.getMessage());
      if (e.getStatus() == 401) {
        commands.execute("UNENROLL");
      }
    } catch (RuntimeException e) {
      status.connected(false);
      log.info("Latido sin respuesta: {}", e.getMessage());
    }
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
