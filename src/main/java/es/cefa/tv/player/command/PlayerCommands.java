package es.cefa.tv.player.command;

import com.fasterxml.jackson.databind.JsonNode;
import es.cefa.tv.player.browser.BrowserController;
import es.cefa.tv.player.display.DisplayService;
import es.cefa.tv.player.enrollment.StateStore;
import es.cefa.tv.player.remote.RemoteService;
import es.cefa.tv.player.shell.ShellService;
import es.cefa.tv.player.status.PlayerStatus;
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
  private final ShellService shell;
  private final DisplayService display;
  private final PlayerStatus status;
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
   * @param shell terminal remota
   * @param display pantallas y resolución
   * @param status estado compartido, donde se anotan los errores de las órdenes
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
      ShellService shell,
      DisplayService display,
      PlayerStatus status,
      ConfigurableApplicationContext context) {
    this.socket = socket;
    this.remote = remote;
    this.sync = sync;
    this.browser = browser;
    this.state = state;
    this.manifests = manifests;
    this.files = files;
    this.shell = shell;
    this.display = display;
    this.status = status;
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
      case "SHELL_OPEN" ->
          shell.open(
              message.path("sessionId").asText(),
              message.path("cols").asInt(0),
              message.path("rows").asInt(0));
      case "SHELL_INPUT" ->
          shell.input(message.path("sessionId").asText(), message.path("data").asText(null));
      case "SHELL_RESIZE" ->
          shell.resize(
              message.path("sessionId").asText(),
              message.path("cols").asInt(0),
              message.path("rows").asInt(0));
      case "SHELL_CLOSE" -> shell.close(message.path("sessionId").asText());
      default ->
          execute(
              type,
              message.path("output").asText(null),
              message.path("width").asInt(0),
              message.path("height").asInt(0));
    }
  }

  /**
   * Ejecuta una orden simple.
   *
   * @param type RELOAD, RESTART, UNENROLL o DISPLAY_REDETECT
   */
  public void execute(String type) {
    execute(type, null, 0, 0);
  }

  /**
   * Ejecuta una orden con sus parámetros.
   *
   * @param type RELOAD, RESTART, UNENROLL, DISPLAY_REDETECT o SET_RESOLUTION
   * @param output salida de vídeo, solo en SET_RESOLUTION
   * @param width ancho pedido, solo en SET_RESOLUTION
   * @param height alto pedido, solo en SET_RESOLUTION
   */
  public void execute(String type, String output, int width, int height) {
    switch (type) {
      case "RELOAD" -> browser.reload();
      case "RESTART" -> exit(0);
      case "UNENROLL" -> unenroll();
      case "DISPLAY_REDETECT" -> display.redetect();
      case "SET_RESOLUTION" -> setResolution(output, width, height);
      default -> log.debug("Orden desconocida: {}", type);
    }
  }

  private void setResolution(String output, int width, int height) {
    try {
      display.setResolution(output, width, height);
      status.error(null);
    } catch (IllegalArgumentException e) {
      log.warn("Resolución rechazada: {}", e.getMessage());
      status.error(e.getMessage());
    }
  }

  private void unenroll() {
    log.warn("Baja del reproductor: se borra el estado local");
    remote.stop();
    shell.closeAll();
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
