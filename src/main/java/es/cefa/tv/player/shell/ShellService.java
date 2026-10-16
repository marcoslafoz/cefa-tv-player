package es.cefa.tv.player.shell;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import es.cefa.tv.player.shared.config.PlayerProperties;
import es.cefa.tv.player.sync.PlayerSocket;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Terminal remota: mantiene como máximo una sesión de shell con pseudoterminal y la comunica con el
 * servidor por el WebSocket del reproductor.
 */
@Slf4j
@Service
public class ShellService {

  private static final int OUTPUT_BUFFER = 8192;
  private static final int DEFAULT_COLS = 80;
  private static final int DEFAULT_ROWS = 24;

  private final PlayerProperties properties;
  private final PlayerSocket socket;
  private final ShellLauncher launcher;
  private Session session;

  /**
   * Crea el servicio.
   *
   * @param properties configuración del reproductor
   * @param socket WebSocket con el servidor
   * @param launcher creador de procesos de shell
   */
  public ShellService(PlayerProperties properties, PlayerSocket socket, ShellLauncher launcher) {
    this.properties = properties;
    this.socket = socket;
    this.launcher = launcher;
  }

  /** Corta la sesión si se cae la conexión con el servidor. */
  @PostConstruct
  public void wire() {
    socket.onDisconnect(this::closeAll);
  }

  /**
   * Abre una sesión de terminal.
   *
   * @param sessionId identificador que asigna el servidor
   * @param cols columnas del terminal
   * @param rows filas del terminal
   */
  public synchronized void open(String sessionId, int cols, int rows) {
    if (!properties.shell().enabled()) {
      reject(sessionId, "SHELL_DISABLED");
      return;
    }
    if (session != null) {
      reject(sessionId, "SHELL_BUSY");
      return;
    }
    try {
      PtyProcess process =
          launcher.launch(cols > 0 ? cols : DEFAULT_COLS, rows > 0 ? rows : DEFAULT_ROWS);
      session = new Session(sessionId, process);
      log.info("Sesión de terminal {} abierta (modo {})", sessionId, properties.shell().mode());
      Thread pump = new Thread(session::pump, "shell-" + sessionId);
      pump.setDaemon(true);
      pump.start();
    } catch (IOException | RuntimeException e) {
      log.warn("No se pudo abrir la terminal: {}", e.getMessage());
      reject(sessionId, "SHELL_LAUNCH_FAILED");
    }
  }

  /**
   * Escribe en la entrada de la shell.
   *
   * @param sessionId sesión destino
   * @param data texto tecleado
   */
  public synchronized void input(String sessionId, String data) {
    if (session == null || !session.id.equals(sessionId) || data == null) {
      return;
    }
    try {
      session.process.getOutputStream().write(data.getBytes(StandardCharsets.UTF_8));
      session.process.getOutputStream().flush();
    } catch (IOException e) {
      log.debug("Entrada de terminal descartada: {}", e.getMessage());
    }
  }

  /**
   * Cambia el tamaño del terminal.
   *
   * @param sessionId sesión destino
   * @param cols columnas
   * @param rows filas
   */
  public synchronized void resize(String sessionId, int cols, int rows) {
    if (session != null && session.id.equals(sessionId) && cols > 0 && rows > 0) {
      session.process.setWinSize(new WinSize(cols, rows));
    }
  }

  /**
   * Cierra una sesión.
   *
   * @param sessionId sesión a cerrar
   */
  public synchronized void close(String sessionId) {
    if (session != null && session.id.equals(sessionId)) {
      session.process.destroyForcibly();
    }
  }

  /** Cierra la sesión activa, si la hay. */
  @PreDestroy
  public synchronized void closeAll() {
    if (session != null) {
      session.process.destroyForcibly();
    }
  }

  private void reject(String sessionId, String error) {
    sendExit(sessionId, -1, error);
  }

  private void sendExit(String sessionId, int code, String error) {
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("type", "SHELL_EXIT");
    message.put("sessionId", sessionId);
    message.put("code", code);
    if (error != null) {
      message.put("error", error);
    }
    socket.sendText(message);
  }

  private synchronized void finished(Session finished, int code) {
    if (session == finished) {
      session = null;
    }
    log.info("Sesión de terminal {} cerrada con código {}", finished.id, code);
    sendExit(finished.id, code, null);
  }

  private final class Session {

    private final String id;
    private final PtyProcess process;

    Session(String id, PtyProcess process) {
      this.id = id;
      this.process = process;
    }

    void pump() {
      char[] buffer = new char[OUTPUT_BUFFER];
      try (Reader reader =
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
        int read;
        while ((read = reader.read(buffer)) != -1) {
          Map<String, Object> message = new LinkedHashMap<>();
          message.put("type", "SHELL_OUTPUT");
          message.put("sessionId", id);
          message.put("data", new String(buffer, 0, read));
          socket.sendText(message);
        }
      } catch (IOException e) {
        log.debug("Lectura de terminal terminada: {}", e.getMessage());
      }
      int code;
      try {
        code = process.waitFor();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        code = -1;
      }
      finished(this, code);
    }
  }
}
