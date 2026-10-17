package es.cefa.tv.player.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.storage.ManifestStore;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Conexión WebSocket permanente con el servidor ({@code /ws/player}) con reconexión exponencial.
 * Recibe avisos y órdenes y envía el estado y los fotogramas del control remoto.
 */
@Slf4j
@Component
public class PlayerSocket {

  private static final long MIN_BACKOFF_MS = 1000;
  private static final long MAX_BACKOFF_MS = 60000;

  private final ObjectMapper mapper;
  private final ManifestStore manifests;
  private final HttpClient http = HttpClient.newHttpClient();
  private volatile Consumer<JsonNode> handler = message -> {};
  private final List<Runnable> disconnectListeners = new CopyOnWriteArrayList<>();
  private volatile boolean running;
  private volatile WebSocket socket;
  private CompletableFuture<?> tail = CompletableFuture.completedFuture(null);
  private Thread thread;

  /**
   * Crea la conexión.
   *
   * @param mapper serializador JSON
   * @param manifests manifest aplicado, informado en el saludo
   */
  public PlayerSocket(ObjectMapper mapper, ManifestStore manifests) {
    this.mapper = mapper;
    this.manifests = manifests;
  }

  /**
   * Registra quién procesa los mensajes de texto recibidos.
   *
   * @param handler recibe cada mensaje JSON
   */
  public void onMessage(Consumer<JsonNode> handler) {
    this.handler = handler;
  }

  /**
   * Registra qué se ejecuta cuando se pierde la conexión con el servidor.
   *
   * @param listener acción a ejecutar en cada desconexión
   */
  public void onDisconnect(Runnable listener) {
    disconnectListeners.add(listener);
  }

  /**
   * Abre la conexión y la mantiene viva.
   *
   * @param serverUrl URL base del core
   * @param token token de dispositivo
   */
  public synchronized void start(String serverUrl, String token) {
    if (running) {
      return;
    }
    running = true;
    URI uri = toWebSocketUri(serverUrl, token);
    thread = new Thread(() -> maintain(uri), "player-socket");
    thread.setDaemon(true);
    thread.start();
  }

  /** Cierra la conexión y deja de reconectar. */
  @PreDestroy
  public synchronized void stop() {
    running = false;
    WebSocket current = socket;
    if (current != null) {
      current.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
    }
    if (thread != null) {
      thread.interrupt();
    }
  }

  /**
   * Indica si la conexión está abierta.
   *
   * @return {@code true} si hay WebSocket
   */
  public boolean isOpen() {
    return socket != null;
  }

  /**
   * Envía un mensaje JSON.
   *
   * @param message objeto a serializar
   */
  public synchronized void sendText(Object message) {
    WebSocket current = socket;
    if (current == null) {
      return;
    }
    try {
      String json = mapper.writeValueAsString(message);
      tail = tail.handle((result, error) -> null).thenCompose(v -> current.sendText(json, true));
    } catch (IOException e) {
      log.warn("Mensaje no serializable: {}", e.getMessage());
    }
  }

  /**
   * Envía un mensaje JSON solo si el canal está libre.
   *
   * @param message objeto a serializar
   * @return {@code false} si se descarta por estar ocupado, desconectado o no serializable
   */
  public synchronized boolean trySendText(Object message) {
    WebSocket current = socket;
    if (current == null || !tail.isDone()) {
      return false;
    }
    try {
      tail = current.sendText(mapper.writeValueAsString(message), true);
      return true;
    } catch (IOException e) {
      log.warn("Mensaje no serializable: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Envía un fotograma binario si el canal está libre.
   *
   * @param data bytes del JPEG
   * @return {@code false} si se descarta por estar ocupado o desconectado
   */
  public synchronized boolean sendBinary(byte[] data) {
    WebSocket current = socket;
    if (current == null || !tail.isDone()) {
      return false;
    }
    tail = current.sendBinary(ByteBuffer.wrap(data), true);
    return true;
  }

  /** Envía un ping periódico para mantener la conexión a través de proxies. */
  @Scheduled(fixedDelay = 25000)
  public void ping() {
    WebSocket current = socket;
    if (current != null) {
      current.sendPing(ByteBuffer.allocate(0));
    }
  }

  static URI toWebSocketUri(String serverUrl, String token) {
    String base = serverUrl.replaceFirst("^http", "ws");
    return URI.create(
        base + "/ws/player?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
  }

  private void maintain(URI uri) {
    long backoff = MIN_BACKOFF_MS;
    while (running) {
      CompletableFuture<Void> closed = new CompletableFuture<>();
      try {
        WebSocket opened =
            http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(uri, new Listener(closed))
                .get(15, TimeUnit.SECONDS);
        socket = opened;
        backoff = MIN_BACKOFF_MS;
        log.info("WebSocket con el servidor abierto");
        sendText(Map.of("type", "HELLO", "appliedVersion", manifests.appliedVersion()));
        closed.get();
        disconnectListeners.forEach(Runnable::run);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (ExecutionException | TimeoutException e) {
        log.debug("WebSocket no disponible: {}", e.getMessage());
      }
      socket = null;
      try {
        Thread.sleep(backoff);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
    }
  }

  private final class Listener implements WebSocket.Listener {

    private final CompletableFuture<Void> closed;
    private final StringBuilder buffer = new StringBuilder();

    Listener(CompletableFuture<Void> closed) {
      this.closed = closed;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      buffer.append(data);
      if (last) {
        String text = buffer.toString();
        buffer.setLength(0);
        try {
          handler.accept(mapper.readTree(text));
        } catch (IOException | RuntimeException e) {
          log.warn("Mensaje del servidor no procesable: {}", e.getMessage());
        }
      }
      webSocket.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
      webSocket.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      closed.complete(null);
      return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      closed.complete(null);
    }
  }
}
