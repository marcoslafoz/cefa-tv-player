package es.cefa.tv.player.browser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.cefa.tv.player.shared.config.PlayerProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Cliente mínimo del protocolo Chrome DevTools sobre la pestaña principal de Chromium. */
@Slf4j
@Component
public class CdpClient {

  private static final Duration CALL_TIMEOUT = Duration.ofSeconds(10);

  private final PlayerProperties properties;
  private final ObjectMapper mapper;
  private final HttpClient http =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
  private final AtomicInteger ids = new AtomicInteger();
  private final Map<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
  private final CopyOnWriteArrayList<BiConsumer<String, JsonNode>> listeners =
      new CopyOnWriteArrayList<>();
  private volatile WebSocket socket;

  /**
   * Crea el cliente.
   *
   * @param properties configuración del reproductor
   * @param mapper serializador JSON
   */
  public CdpClient(PlayerProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
  }

  /**
   * Registra un oyente de eventos del navegador.
   *
   * @param listener recibe el nombre del evento y sus parámetros
   */
  public void addEventListener(BiConsumer<String, JsonNode> listener) {
    listeners.add(listener);
  }

  /**
   * Indica si hay una conexión abierta con la pestaña.
   *
   * @return {@code true} si está conectado
   */
  public boolean isConnected() {
    return socket != null;
  }

  /**
   * Ejecuta un comando CDP, conectando antes si hace falta.
   *
   * @param method nombre del método, por ejemplo {@code Page.navigate}
   * @param params parámetros, o nulo
   * @return campo {@code result} de la respuesta
   * @throws IOException si no hay navegador o el comando falla
   */
  public JsonNode call(String method, ObjectNode params) throws IOException {
    ensureConnected();
    int id = ids.incrementAndGet();
    ObjectNode message = mapper.createObjectNode().put("id", id).put("method", method);
    message.set("params", params == null ? mapper.createObjectNode() : params);
    CompletableFuture<JsonNode> future = new CompletableFuture<>();
    pending.put(id, future);
    try {
      WebSocket current = socket;
      if (current == null) {
        throw new IOException("Sin conexión CDP");
      }
      current.sendText(mapper.writeValueAsString(message), true).get(5, TimeUnit.SECONDS);
      return future.get(CALL_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrumpido", e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IOException("Fallo en " + method + ": " + e.getMessage(), e);
    } finally {
      pending.remove(id);
    }
  }

  /**
   * Parámetros vacíos reutilizables por quien llama.
   *
   * @return nodo objeto nuevo
   */
  public ObjectNode params() {
    return mapper.createObjectNode();
  }

  private synchronized void ensureConnected() throws IOException {
    if (socket != null) {
      return;
    }
    try {
      HttpRequest request =
          HttpRequest.newBuilder(
                  URI.create(
                      "http://127.0.0.1:" + properties.chromium().debugPort() + "/json/list"))
              .timeout(Duration.ofSeconds(3))
              .GET()
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      String wsUrl = null;
      for (JsonNode target : mapper.readTree(response.body())) {
        if ("page".equals(target.path("type").asText())) {
          wsUrl = target.path("webSocketDebuggerUrl").asText(null);
          break;
        }
      }
      if (wsUrl == null) {
        throw new IOException("Chromium no tiene ninguna pestaña abierta");
      }
      socket =
          http.newWebSocketBuilder()
              .connectTimeout(Duration.ofSeconds(5))
              .buildAsync(URI.create(wsUrl), new Listener())
              .get(6, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrumpido", e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IOException("No se pudo conectar con Chromium: " + e.getMessage(), e);
    }
  }

  private void handle(String text) {
    try {
      JsonNode node = mapper.readTree(text);
      if (node.has("id")) {
        CompletableFuture<JsonNode> future = pending.get(node.get("id").asInt());
        if (future == null) {
          return;
        }
        if (node.has("error")) {
          future.completeExceptionally(new IOException(node.get("error").toString()));
        } else {
          future.complete(node.path("result"));
        }
      } else if (node.has("method")) {
        String method = node.get("method").asText();
        JsonNode params = node.path("params");
        listeners.forEach(listener -> listener.accept(method, params));
      }
    } catch (IOException | RuntimeException e) {
      log.debug("Mensaje CDP ignorado: {}", e.getMessage());
    }
  }

  private void disconnected() {
    socket = null;
    pending.values().forEach(f -> f.completeExceptionally(new IOException("CDP desconectado")));
    pending.clear();
    listeners.forEach(listener -> listener.accept("Cefa.disconnected", mapper.createObjectNode()));
  }

  private final class Listener implements WebSocket.Listener {

    private final StringBuilder buffer = new StringBuilder();

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
        handle(text);
      }
      webSocket.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      disconnected();
      return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      disconnected();
    }
  }
}
