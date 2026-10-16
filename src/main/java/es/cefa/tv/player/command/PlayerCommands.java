package es.cefa.tv.player.command;

import com.fasterxml.jackson.databind.JsonNode;
import es.cefa.tv.player.browser.BrowserController;
import es.cefa.tv.player.enrollment.StateStore;
import es.cefa.tv.player.remote.RemoteService;
import es.cefa.tv.player.storage.FileStore;
import es.cefa.tv.player.storage.ManifestStore;
import es.cefa.tv.player.sync.PlayerSocket;
import es.cefa.tv.player.sync.SyncService;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/** Ejecuta las órdenes del servidor recibidas por WebSocket o en la respuesta de los latidos. */
@Slf4j
@Component
public class PlayerCommands {

  private static final int DEFAULT_FPS = 5;
  private static final int DEFAULT_QUALITY = 60;
  private static final int DEFAULT_MAX_WIDTH = 1280;

  private final PlayerSocket socket;
  private final RemoteService remote;
  private final SyncService sync;
  private final BrowserController browser;
  private final StateStore state;
  private final ManifestStore manifests;
  private final FileStore files;
  private final ConfigurableApplicationContext context;
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "commands");
            thread.setDaemon(true);
            return thread;
          });

  /**
   * Crea el ejecutor de órdenes.
   *
   * @param socket conexión con el servidor
   * @param remote control remoto
   * @param sync sincronización
   * @param browser control del navegador
   * @param state estado de alta
   * @param manifests manifest aplicado
   * @param files almacén de ficheros
   * @param context contexto de Spring, para el cierre
   */
  public PlayerCommands(
      PlayerSocket socket,
      RemoteService remote,
      SyncService sync,
      BrowserController browser,
      StateStore state,
      ManifestStore manifests,
      FileStore files,
      ConfigurableApplicationContext context) {
    this.socket = socket;
    this.remote = remote;
    this.sync = sync;
    this.browser = browser;
    this.state = state;
    this.manifests = manifests;
    this.files = files;
    this.context = context;
  }

  /** Conecta los mensajes del WebSocket y los fotogramas del control remoto. */
  @PostConstruct
  public void wire() {
    socket.onMessage(message -> executor.execute(() -> handle(message)));
    remote.setFrameSink(socket::sendBinary);
  }

  /**
   * Procesa un mensaje del servidor.
   *
   * @param message mensaje JSON con campo {@code type}
   */
  public void handle(JsonNode message) {
    String type = message.path("type").asText("");
    switch (type) {
      case "SYNC" -> sync.requestSync(true);
      case "REMOTE_START" ->
          remote.start(
              message.path("fps").asInt(DEFAULT_FPS),
              message.path("quality").asInt(DEFAULT_QUALITY),
              message.path("maxWidth").asInt(DEFAULT_MAX_WIDTH));
      case "REMOTE_STOP" -> remote.stop();
      case "INPUT" -> remote.input(message);
      default -> execute(type);
    }
  }

  /**
   * Ejecuta una orden simple.
   *
   * @param type RELOAD, RESTART o UNENROLL
   */
  public void execute(String type) {
    switch (type) {
      case "RELOAD" -> browser.reload();
      case "RESTART" -> exit(0);
      case "UNENROLL" -> unenroll();
      default -> log.debug("Orden desconocida: {}", type);
    }
  }

  private void unenroll() {
    log.warn("Baja del reproductor: se borra el estado local");
    remote.stop();
    state.clear();
    manifests.clear();
    files.clear();
    exit(0);
  }

  private void exit(int code) {
    Thread exiter =
        new Thread(() -> System.exit(SpringApplication.exit(context, () -> code)), "exit");
    exiter.start();
  }
}
