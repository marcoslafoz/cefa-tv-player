package es.cefa.tv.player.preview;

import com.fasterxml.jackson.databind.JsonNode;
import es.cefa.tv.player.browser.CdpClient;
import es.cefa.tv.player.remote.RemoteService;
import es.cefa.tv.player.shared.config.PlayerProperties;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Vista previa en vivo muy ligera: mientras el servidor la pide captura la pantalla cada pocos
 * segundos con una sola llamada a Chromium, ya reducida en origen, y envía un JPEG pequeño. No usa
 * screencast continuo, no reescala en Java y nunca captura sin una petición activa.
 */
@Slf4j
@Component
public class PreviewService {

  /** Intervalo por defecto entre capturas, en segundos. */
  public static final int DEFAULT_INTERVAL_SEC = 5;

  /** Ancho por defecto de la imagen, en píxeles. */
  public static final int DEFAULT_MAX_WIDTH = 320;

  /** Calidad JPEG por defecto. */
  public static final int DEFAULT_QUALITY = 50;

  /** Tamaño máximo del JPEG enviado, en bytes. */
  public static final int MAX_FRAME_BYTES = 25 * 1024;

  /** Segundos tras los que se reenvía aunque la imagen no haya cambiado. */
  public static final long KEEPALIVE_SEC = 60;

  private static final int MAX_INTERVAL_SEC = 300;
  private static final int MAX_WIDTH = 1280;
  private static final int MIN_QUALITY = 20;
  private static final int MIN_WIDTH = 120;
  private static final int QUALITY_STEP = 15;
  private static final double WIDTH_STEP = 0.75;
  private static final int MAX_SHRINK_ATTEMPTS = 6;

  private final CdpClient cdp;
  private final PlayerProperties properties;
  private final RemoteService remote;
  private final Clock clock;
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "preview");
            thread.setDaemon(true);
            return thread;
          });
  private volatile Predicate<Object> messageSink = message -> false;
  private volatile boolean active;
  private int quality = DEFAULT_QUALITY;
  private int maxWidth = DEFAULT_MAX_WIDTH;
  private String lastHash = "";
  private Instant lastSentAt = Instant.MIN;
  private ScheduledFuture<?> task;

  /**
   * Crea el servicio.
   *
   * @param cdp cliente DevTools
   * @param properties configuración del reproductor
   * @param remote control remoto, que ya envía fotogramas por su cuenta
   * @param clock reloj del reproductor
   */
  public PreviewService(
      CdpClient cdp, PlayerProperties properties, RemoteService remote, Clock clock) {
    this.cdp = cdp;
    this.properties = properties;
    this.remote = remote;
    this.clock = clock;
  }

  /**
   * Fija el destino de los mensajes hacia el servidor.
   *
   * @param sink recibe cada mensaje y devuelve {@code false} si lo descarta por estar ocupado
   */
  public void setMessageSink(Predicate<Object> sink) {
    this.messageSink = sink;
  }

  /**
   * Inicia la vista previa.
   *
   * @param intervalSec segundos entre capturas, de 1 a 300
   * @param maxWidth ancho máximo de la imagen en píxeles
   * @param quality calidad JPEG de 10 a 90
   */
  public synchronized void start(int intervalSec, int maxWidth, int quality) {
    int interval = configure(intervalSec, maxWidth, quality);
    task = scheduler.scheduleWithFixedDelay(this::capture, 0, interval, TimeUnit.SECONDS);
  }

  synchronized int configure(int intervalSec, int maxWidth, int quality) {
    cancelTask();
    this.maxWidth =
        maxWidth <= 0 ? DEFAULT_MAX_WIDTH : Math.max(MIN_WIDTH, Math.min(maxWidth, MAX_WIDTH));
    this.quality = quality <= 0 ? DEFAULT_QUALITY : Math.max(10, Math.min(quality, 90));
    this.lastHash = "";
    this.lastSentAt = Instant.MIN;
    this.active = true;
    return intervalSec <= 0 ? DEFAULT_INTERVAL_SEC : Math.min(intervalSec, MAX_INTERVAL_SEC);
  }

  /** Detiene la vista previa. */
  public synchronized void stop() {
    active = false;
    cancelTask();
  }

  /**
   * Indica si hay una vista previa pedida.
   *
   * @return {@code true} entre {@code PREVIEW_START} y {@code PREVIEW_STOP}
   */
  public boolean isActive() {
    return active;
  }

  private void cancelTask() {
    if (task != null) {
      task.cancel(false);
      task = null;
    }
  }

  void capture() {
    if (!active || remote.isActive() || !properties.chromium().enabled()) {
      return;
    }
    try {
      JsonNode viewport = layoutViewport();
      double viewportWidth = positive(viewport.path("clientWidth").asDouble(0), 1920);
      double viewportHeight = positive(viewport.path("clientHeight").asDouble(0), 1080);
      int currentQuality = quality;
      int currentWidth = maxWidth;
      for (int attempt = 0; attempt <= MAX_SHRINK_ATTEMPTS; attempt++) {
        String data = screenshot(viewportWidth, viewportHeight, currentWidth, currentQuality);
        if (data.isEmpty()) {
          return;
        }
        int size = data.length() / 4 * 3;
        if (size <= MAX_FRAME_BYTES || attempt == MAX_SHRINK_ATTEMPTS) {
          quality = currentQuality;
          maxWidth = currentWidth;
          send(data, currentWidth, (int) Math.round(viewportHeight * currentWidth / viewportWidth));
          return;
        }
        if (currentQuality > MIN_QUALITY) {
          currentQuality = Math.max(MIN_QUALITY, currentQuality - QUALITY_STEP);
        } else {
          currentWidth = Math.max(MIN_WIDTH, (int) (currentWidth * WIDTH_STEP));
        }
      }
    } catch (IOException e) {
      log.debug("No se pudo capturar la vista previa: {}", e.getMessage());
    }
  }

  private static double positive(double value, double fallback) {
    return value > 0 ? value : fallback;
  }

  private JsonNode layoutViewport() throws IOException {
    return cdp.call("Page.getLayoutMetrics", null).path("cssVisualViewport");
  }

  private String screenshot(double viewportWidth, double viewportHeight, int width, int jpegQuality)
      throws IOException {
    var params = cdp.params();
    params.put("format", "jpeg");
    params.put("quality", jpegQuality);
    params.put("optimizeForSpeed", true);
    var clip = params.putObject("clip");
    clip.put("x", 0);
    clip.put("y", 0);
    clip.put("width", viewportWidth);
    clip.put("height", viewportHeight);
    clip.put("scale", Math.min(1.0, width / viewportWidth));
    return cdp.call("Page.captureScreenshot", params).path("data").asText("");
  }

  private void send(String data, int width, int height) {
    Instant now = clock.instant();
    String hash = hash(data);
    boolean unchanged = hash.equals(lastHash);
    boolean keepAlive = now.isAfter(lastSentAt.plusSeconds(KEEPALIVE_SEC));
    if (unchanged && !keepAlive) {
      return;
    }
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("type", "PREVIEW_FRAME");
    message.put("w", width);
    message.put("h", height);
    message.put("jpegBase64", data);
    message.put("at", now.toString());
    if (messageSink.test(message)) {
      lastHash = hash;
      lastSentAt = now;
    }
  }

  private static String hash(String data) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(data)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
