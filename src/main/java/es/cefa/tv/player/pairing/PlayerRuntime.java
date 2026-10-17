package es.cefa.tv.player.pairing;

import es.cefa.tv.player.shared.config.PlayerProperties;
import es.cefa.tv.player.shared.time.Sleeper;
import es.cefa.tv.player.status.PlayerStatus;
import es.cefa.tv.player.sync.HeartbeatService;
import es.cefa.tv.player.sync.PlayerSocket;
import es.cefa.tv.player.sync.ServerApi;
import es.cefa.tv.player.sync.SyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ciclo de vida del reproductor: si no está emparejado lo empareja y, una vez emparejado, pone en
 * marcha la sincronización, los latidos y el WebSocket. Tras una baja vuelve a emparejarse sin
 * reiniciar el proceso.
 */
@Slf4j
@Component
public class PlayerRuntime {

  private final PlayerProperties properties;
  private final StateStore stateStore;
  private final PairingService pairing;
  private final ServerApi api;
  private final PlayerStatus status;
  private final SyncService sync;
  private final HeartbeatService heartbeat;
  private final PlayerSocket socket;
  private final Sleeper sleeper;
  private Thread thread;

  /**
   * Crea el ciclo de vida.
   *
   * @param properties configuración del reproductor
   * @param stateStore estado persistente
   * @param pairing emparejamiento con el servidor
   * @param api cliente del servidor
   * @param status estado compartido
   * @param sync sincronización
   * @param heartbeat latidos
   * @param socket WebSocket con el servidor
   * @param sleeper espera interrumpible
   */
  public PlayerRuntime(
      PlayerProperties properties,
      StateStore stateStore,
      PairingService pairing,
      ServerApi api,
      PlayerStatus status,
      SyncService sync,
      HeartbeatService heartbeat,
      PlayerSocket socket,
      Sleeper sleeper) {
    this.properties = properties;
    this.stateStore = stateStore;
    this.pairing = pairing;
    this.api = api;
    this.status = status;
    this.sync = sync;
    this.heartbeat = heartbeat;
    this.socket = socket;
    this.sleeper = sleeper;
  }

  /** Arranca el ciclo de vida en un hilo propio cuando la aplicación está lista. */
  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    startLoop();
  }

  /**
   * Detiene la comunicación con el servidor y vuelve a emparejar tras una baja.
   *
   * @param event baja del reproductor ya aplicada en local
   */
  @EventListener
  public void onUnenrolled(UnenrolledEvent event) {
    heartbeat.stop();
    socket.stop();
    status.reset();
    startLoop();
  }

  private synchronized void startLoop() {
    if (thread != null) {
      thread.interrupt();
    }
    thread = new Thread(this::run, "runtime");
    thread.setDaemon(true);
    thread.start();
  }

  private void run() {
    Backoff failures = new Backoff();
    while (!Thread.currentThread().isInterrupted()) {
      try {
        PlayerState state = stateStore.load().orElse(null);
        if (state == null) {
          state = pairing.pair();
        } else {
          pairing.discardPending();
        }
        begin(state);
        return;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (RuntimeException e) {
        log.error("Fallo en el arranque del reproductor: {}", e.getMessage(), e);
        try {
          sleeper.sleep(failures.nextDelayMs());
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }
  }

  private void begin(PlayerState state) {
    api.configure(state.serverUrl(), state.deviceToken());
    status.paired(state.serverUrl(), state.playerId(), state.name());
    status.phase(PlayerPhase.CONNECTING, "Conectando con " + state.serverUrl());
    int interval =
        state.pollIntervalSec() > 0 ? state.pollIntervalSec() : properties.heartbeatSec();
    heartbeat.start(Math.min(interval, properties.heartbeatSec()));
    socket.start(state.serverUrl(), state.deviceToken());
    sync.requestSync(false);
  }
}
