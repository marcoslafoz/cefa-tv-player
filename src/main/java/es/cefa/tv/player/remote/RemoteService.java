package es.cefa.tv.player.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.browser.CdpClient;
import es.cefa.tv.player.playback.PlaybackHold;
import es.cefa.tv.player.shared.config.PlayerProperties;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Sesión de control remoto: retransmite la pantalla de Chromium como fotogramas JPEG y aplica el
 * ratón y el teclado recibidos.
 */
@Slf4j
@Component
public class RemoteService {

  private static final double FRAMES_PER_SECOND_SOURCE = 60.0;
  private static final long RESTART_DELAY_SECONDS = 2;
  private static final long VIEWPORT_TTL_MS = 2000;
  private static final long IDLE_FRAME_MS = 250;
  private static final long FRAME_FRESH_MS = 200;
  private static final long INPUT_CAPTURE_DELAY_MS = 80;
  private static final long CURSOR_INTERVAL_MS = 100;

  private final CdpClient cdp;
  private final PlayerProperties properties;
  private final InputMapper inputMapper;
  private final PlaybackHold playbackHold;
  private final AtomicBoolean captureQueued = new AtomicBoolean();
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "remote");
            thread.setDaemon(true);
            return thread;
          });
  private volatile Predicate<byte[]> frameSink = frame -> false;
  private volatile Consumer<Object> messageSink = message -> {};
  private volatile long lastCursorAt;
  private volatile String lastCursor;
  private volatile boolean active;
  private volatile int fps = 5;
  private volatile int quality = 60;
  private volatile int maxWidth = 1280;
  private volatile double viewportWidth = 1920;
  private volatile double viewportHeight = 1080;
  private volatile long viewportCheckedAt;
  private volatile long lastFrameAt;
  private ScheduledFuture<?> idleFrames;

  /**
   * Crea el servicio y se suscribe a los eventos de DevTools.
   *
   * @param cdp cliente DevTools
   * @param properties configuración del reproductor
   * @param mapper serializador JSON
   * @param playbackHold pausa de la reproducción durante la sesión
   */
  public RemoteService(
      CdpClient cdp, PlayerProperties properties, ObjectMapper mapper, PlaybackHold playbackHold) {
    this.cdp = cdp;
    this.properties = properties;
    this.playbackHold = playbackHold;
    this.inputMapper = new InputMapper(mapper);
    cdp.addEventListener(this::onCdpEvent);
  }

  /**
   * Fija el destino de los fotogramas.
   *
   * @param sink recibe cada JPEG y devuelve {@code false} si lo descarta por estar ocupado
   */
  public void setFrameSink(Predicate<byte[]> sink) {
    this.frameSink = sink;
  }

  /**
   * Fija el destino de los mensajes de texto hacia el servidor, como el cursor.
   *
   * @param sink recibe cada mensaje serializable
   */
  public void setMessageSink(Consumer<Object> sink) {
    this.messageSink = sink;
  }

  /**
   * Inicia la retransmisión.
   *
   * @param fps fotogramas por segundo deseados
   * @param quality calidad JPEG de 1 a 100
   * @param maxWidth ancho máximo del fotograma
   */
  public synchronized void start(int fps, int quality, int maxWidth) {
    this.fps = Math.max(1, Math.min(fps, 30));
    this.quality = Math.max(10, Math.min(quality, 100));
    this.maxWidth = Math.max(320, maxWidth);
    this.active = true;
    playbackHold.hold();
    this.lastCursor = null;
    startScreencast();
    if (idleFrames != null) {
      idleFrames.cancel(false);
    }
    idleFrames =
        scheduler.scheduleWithFixedDelay(
            this::sendIdleFrame, IDLE_FRAME_MS, IDLE_FRAME_MS, TimeUnit.MILLISECONDS);
  }

  /** Detiene la retransmisión. */
  public synchronized void stop() {
    if (!active) {
      return;
    }
    active = false;
    playbackHold.release();
    if (idleFrames != null) {
      idleFrames.cancel(false);
      idleFrames = null;
    }
    if (properties.chromium().enabled()) {
      try {
        cdp.call("Page.stopScreencast", null);
      } catch (IOException e) {
        log.debug("No se pudo detener el screencast: {}", e.getMessage());
      }
    }
  }

  /**
   * Indica si hay una sesión remota activa.
   *
   * @return {@code true} mientras se retransmite
   */
  public boolean isActive() {
    return active;
  }

  /**
   * Aplica un mensaje de entrada del operador.
   *
   * @param input mensaje {@code INPUT}
   */
  public void input(JsonNode input) {
    if (!active) {
      return;
    }
    refreshViewport();
    for (CdpCommand command : inputMapper.map(input, viewportWidth, viewportHeight)) {
      try {
        cdp.call(command.method(), command.params());
      } catch (IOException e) {
        log.debug("Entrada remota no aplicada: {}", e.getMessage());
      }
    }
    if ("MOUSE_MOVE".equals(input.path("kind").asText())) {
      reportCursor(input.path("x").asDouble(0), input.path("y").asDouble(0));
    } else {
      queueCapture();
    }
  }

  private void queueCapture() {
    if (captureQueued.compareAndSet(false, true)) {
      scheduler.schedule(
          () -> {
            captureQueued.set(false);
            captureFrame(true);
          },
          INPUT_CAPTURE_DELAY_MS,
          TimeUnit.MILLISECONDS);
    }
  }

  private void reportCursor(double x, double y) {
    long now = System.currentTimeMillis();
    if (now - lastCursorAt < CURSOR_INTERVAL_MS || !properties.chromium().enabled()) {
      return;
    }
    lastCursorAt = now;
    try {
      var params = cdp.params();
      params.put(
          "expression",
          CursorProbe.expression(
              Math.max(0, Math.min(1, x)) * viewportWidth,
              Math.max(0, Math.min(1, y)) * viewportHeight));
      params.put("returnByValue", true);
      String css =
          CursorProbe.normalize(
              cdp.call("Runtime.evaluate", params).path("result").path("value").asText(null));
      if (!css.equals(lastCursor)) {
        lastCursor = css;
        messageSink.accept(Map.of("type", "CURSOR", "css", css));
      }
    } catch (IOException e) {
      log.debug("No se pudo leer el cursor: {}", e.getMessage());
    }
  }

  private void refreshViewport() {
    long now = System.currentTimeMillis();
    if (now - viewportCheckedAt < VIEWPORT_TTL_MS) {
      return;
    }
    viewportCheckedAt = now;
    try {
      JsonNode viewport = cdp.call("Page.getLayoutMetrics", null).path("cssVisualViewport");
      if (viewport.path("clientWidth").asDouble(0) > 0) {
        viewportWidth = viewport.get("clientWidth").asDouble();
        viewportHeight = viewport.path("clientHeight").asDouble(viewportHeight);
      }
    } catch (IOException e) {
      log.debug("No se pudo leer el viewport: {}", e.getMessage());
    }
  }

  private void startScreencast() {
    if (!properties.chromium().enabled()) {
      return;
    }
    try {
      var params = cdp.params();
      params.put("format", "jpeg");
      params.put("quality", quality);
      params.put("maxWidth", maxWidth);
      params.put("everyNthFrame", Math.max(1, (int) Math.round(FRAMES_PER_SECOND_SOURCE / fps)));
      cdp.call("Page.startScreencast", params);
    } catch (IOException e) {
      log.debug("No se pudo iniciar el screencast: {}", e.getMessage());
      scheduleRestart();
    }
  }

  private void sendIdleFrame() {
    captureFrame(false);
  }

  private void captureFrame(boolean force) {
    if (!active || !properties.chromium().enabled()) {
      return;
    }
    if (!force && System.currentTimeMillis() - lastFrameAt < FRAME_FRESH_MS) {
      return;
    }
    try {
      var params = cdp.params();
      params.put("format", "jpeg");
      params.put("quality", quality);
      String data = cdp.call("Page.captureScreenshot", params).path("data").asText("");
      if (!data.isEmpty()) {
        lastFrameAt = System.currentTimeMillis();
        frameSink.test(Base64.getDecoder().decode(data));
      }
    } catch (IOException e) {
      log.debug("No se pudo capturar el fotograma en reposo: {}", e.getMessage());
    }
  }

  private void scheduleRestart() {
    scheduler.schedule(
        () -> {
          if (active) {
            startScreencast();
          }
        },
        RESTART_DELAY_SECONDS,
        TimeUnit.SECONDS);
  }

  private void onCdpEvent(String method, JsonNode params) {
    if ("Cefa.disconnected".equals(method)) {
      if (active) {
        scheduleRestart();
      }
      return;
    }
    if (!"Page.screencastFrame".equals(method)) {
      return;
    }
    int sessionId = params.path("sessionId").asInt();
    scheduler.execute(
        () -> {
          try {
            cdp.call("Page.screencastFrameAck", cdp.params().put("sessionId", sessionId));
          } catch (IOException e) {
            log.debug("Ack de fotograma fallido: {}", e.getMessage());
          }
        });
    if (active) {
      lastFrameAt = System.currentTimeMillis();
      frameSink.test(Base64.getDecoder().decode(params.path("data").asText("")));
    }
  }
}
