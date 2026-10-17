package es.cefa.tv.player.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.pairing.PairingOffer;
import es.cefa.tv.player.pairing.PairingPoll;
import es.cefa.tv.player.pairing.PairingRequest;
import es.cefa.tv.player.shared.model.Manifest;
import es.cefa.tv.player.sync.dto.HeartbeatRequest;
import es.cefa.tv.player.sync.dto.HeartbeatResponse;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

/** Cliente HTTP de la API del reproductor ({@code /player-api}). */
@Component
public class ServerApi {

  private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);
  private static final int BUFFER_SIZE = 64 * 1024;

  private final HttpClient http =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();
  private final ObjectMapper mapper;
  private volatile String baseUrl;
  private volatile String token;

  /**
   * Crea el cliente sin credenciales.
   *
   * @param mapper serializador JSON
   */
  public ServerApi(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * Fija el servidor y el token con los que se harán las llamadas autenticadas.
   *
   * @param baseUrl URL base del core
   * @param token token de dispositivo
   */
  public void configure(String baseUrl, String token) {
    this.baseUrl = baseUrl;
    this.token = token;
  }

  /**
   * Pide al servidor un código de emparejamiento.
   *
   * @param serverUrl URL base del core
   * @param request datos del reproductor
   * @return emparejamiento pendiente con su código y su secreto
   */
  public PairingOffer requestPairing(String serverUrl, PairingRequest request) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(serverUrl + "/player-api/pairing/request"))
            .timeout(CALL_TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(toJson(request)));
    return read(send(builder, HttpResponse.BodyHandlers.ofByteArray()), PairingOffer.class);
  }

  /**
   * Consulta el estado de un emparejamiento pendiente.
   *
   * @param serverUrl URL base del core
   * @param pairingId identificador del emparejamiento
   * @param secret secreto devuelto al pedirlo
   * @return estado, con las credenciales si ya está aprobado
   */
  public PairingPoll pairingStatus(String serverUrl, String pairingId, String secret) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(serverUrl + "/player-api/pairing/" + pairingId))
            .timeout(CALL_TIMEOUT)
            .header("X-Pairing-Secret", secret)
            .GET();
    return read(send(builder, HttpResponse.BodyHandlers.ofByteArray()), PairingPoll.class);
  }

  /**
   * URL base del servidor con el que se hacen las llamadas autenticadas.
   *
   * @return URL base o nula si aún no se ha configurado
   */
  public String baseUrl() {
    return baseUrl;
  }

  /**
   * Descarga el manifest si es distinto del conocido.
   *
   * @param knownVersion versión que ya se tiene, o 0
   * @return manifest nuevo, o vacío si no ha cambiado
   */
  public Optional<Manifest> fetchManifest(long knownVersion) {
    HttpRequest.Builder builder = authorized("/player-api/manifest").GET();
    if (knownVersion > 0) {
      builder.header("If-None-Match", "\"" + knownVersion + "\"");
    }
    HttpResponse<byte[]> response = send(builder, HttpResponse.BodyHandlers.ofByteArray());
    if (response.statusCode() == 304) {
      return Optional.empty();
    }
    checkOk(response);
    try {
      byte[] body = response.body();
      boolean gzip =
          response
              .headers()
              .firstValue("Content-Encoding")
              .map(v -> v.equalsIgnoreCase("gzip"))
              .orElse(false);
      if (gzip) {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
          body = in.readAllBytes();
        }
      }
      return Optional.of(mapper.readValue(body, Manifest.class));
    } catch (IOException e) {
      throw new IllegalStateException("Manifest ilegible: " + e.getMessage(), e);
    }
  }

  /**
   * Descarga varios ficheros en un zip y entrega cada entrada a medida que llega.
   *
   * @param shas huellas solicitadas
   * @param handler recibe la huella y el contenido de cada entrada
   * @param abort se consulta durante la lectura para cancelar
   */
  public void downloadZip(List<String> shas, EntryHandler handler, BooleanSupplier abort) {
    HttpRequest.Builder builder =
        authorized("/player-api/files")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(toJson(Map.of("sha256", shas))));
    HttpResponse<InputStream> response = send(builder, HttpResponse.BodyHandlers.ofInputStream());
    if (response.statusCode() / 100 != 2) {
      throw errorOf(response.statusCode(), readQuietly(response.body()));
    }
    try (ZipInputStream zip = new ZipInputStream(response.body())) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (abort.getAsBoolean()) {
          throw new SyncAbortedException();
        }
        handler.accept(entry.getName(), new NonClosingInputStream(zip, abort));
      }
    } catch (IOException e) {
      throw new IllegalStateException("Fallo al leer el zip: " + e.getMessage(), e);
    }
  }

  /**
   * Descarga un fichero grande añadiéndolo al parcial; reanuda si el parcial ya tiene bytes.
   *
   * @param sha256 huella del fichero
   * @param part fichero parcial donde se acumula
   * @param abort se consulta durante la lectura para cancelar
   */
  public void downloadRanged(String sha256, Path part, BooleanSupplier abort) {
    try {
      long offset = Files.exists(part) ? Files.size(part) : 0;
      HttpRequest.Builder builder = authorized("/player-api/files/" + sha256).GET();
      if (offset > 0) {
        builder.header("Range", "bytes=" + offset + "-");
      }
      HttpResponse<InputStream> response = send(builder, HttpResponse.BodyHandlers.ofInputStream());
      int status = response.statusCode();
      if (status == 416) {
        response.body().close();
        return;
      }
      if (status != 200 && status != 206) {
        throw errorOf(status, readQuietly(response.body()));
      }
      StandardOpenOption mode =
          status == 206 ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING;
      try (InputStream in = response.body();
          var channel =
              Files.newByteChannel(
                  part, StandardOpenOption.CREATE, StandardOpenOption.WRITE, mode);
          var out = Channels.newOutputStream(channel)) {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) >= 0) {
          if (abort.getAsBoolean()) {
            throw new SyncAbortedException();
          }
          out.write(buffer, 0, read);
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Fallo al descargar " + sha256 + ": " + e.getMessage(), e);
    }
  }

  /**
   * Envía un latido.
   *
   * @param request estado actual
   * @return versión deseada y órdenes pendientes
   */
  public HeartbeatResponse heartbeat(HeartbeatRequest request) {
    HttpRequest.Builder builder =
        authorized("/player-api/heartbeat")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(toJson(request)));
    return read(send(builder, HttpResponse.BodyHandlers.ofByteArray()), HeartbeatResponse.class);
  }

  /**
   * Confirma la aplicación de una versión.
   *
   * @param version versión tratada
   * @param status APPLIED o FAILED
   * @param error motivo del fallo, si lo hay
   */
  public void ack(long version, String status, String error) {
    Map<String, Object> body = new HashMap<>();
    body.put("version", version);
    body.put("status", status);
    body.put("error", error);
    HttpRequest.Builder builder =
        authorized("/player-api/ack")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(toJson(body)));
    checkOk(send(builder, HttpResponse.BodyHandlers.ofByteArray()));
  }

  /** Callback para cada entrada de un zip. */
  @FunctionalInterface
  public interface EntryHandler {

    /**
     * Procesa una entrada.
     *
     * @param name nombre de la entrada (huella)
     * @param content contenido, que debe consumirse por completo
     * @throws IOException si falla la escritura
     */
    void accept(String name, InputStream content) throws IOException;
  }

  private HttpRequest.Builder authorized(String path) {
    return HttpRequest.newBuilder(URI.create(baseUrl + path))
        .timeout(CALL_TIMEOUT)
        .header("Authorization", "Bearer " + token);
  }

  private <T> HttpResponse<T> send(
      HttpRequest.Builder builder, HttpResponse.BodyHandler<T> handler) {
    try {
      return http.send(builder.build(), handler);
    } catch (IOException e) {
      throw new IllegalStateException(connectionMessage(e), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrumpido", e);
    }
  }

  private static String connectionMessage(IOException e) {
    String detail = e.getMessage();
    return detail == null || detail.isBlank()
        ? "Sin conexión con el servidor"
        : "Sin conexión con el servidor: " + detail;
  }

  private <T> T read(HttpResponse<byte[]> response, Class<T> type) {
    checkOk(response);
    try {
      return mapper.readValue(response.body(), type);
    } catch (IOException e) {
      throw new IllegalStateException("Respuesta ilegible: " + e.getMessage(), e);
    }
  }

  private void checkOk(HttpResponse<byte[]> response) {
    if (response.statusCode() / 100 != 2) {
      throw errorOf(response.statusCode(), new String(response.body()));
    }
  }

  private ServerException errorOf(int status, String body) {
    String code = null;
    String message = body;
    try {
      JsonNode node = mapper.readTree(body);
      code = node.path("errorCode").asText(null);
      message = node.path("message").asText(body);
    } catch (IOException ignored) {
      message = body;
    }
    return new ServerException(status, code, message);
  }

  private String toJson(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String readQuietly(InputStream in) {
    try (in) {
      return new String(in.readAllBytes());
    } catch (IOException e) {
      return "";
    }
  }

  private static final class NonClosingInputStream extends FilterInputStream {

    private final BooleanSupplier abort;

    NonClosingInputStream(InputStream in, BooleanSupplier abort) {
      super(in);
      this.abort = abort;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (abort.getAsBoolean()) {
        throw new SyncAbortedException();
      }
      return super.read(b, off, len);
    }

    @Override
    public void close() {}
  }
}
