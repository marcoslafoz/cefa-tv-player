package es.cefa.tv.player.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.shared.config.PlayerProperties;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Busca el servidor de CEFA TV en la red local mediante broadcast UDP. */
@Slf4j
@Component
public class DiscoveryClient {

  static final String REQUEST = "CEFATV_DISCOVER 1";
  private static final String SERVICE = "cefa-tv";
  private static final String SERVER_SERVICE = "cefa-tv-server";

  private final PlayerProperties properties;
  private final ObjectMapper mapper;
  private final HttpClient http =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(3))
          .build();

  /**
   * Crea el cliente de descubrimiento.
   *
   * @param properties configuración del reproductor
   * @param mapper serializador JSON
   */
  public DiscoveryClient(PlayerProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
  }

  /**
   * Busca el servidor en todas las redes locales.
   *
   * @param attempts número de intentos
   * @param wait tiempo de espera de respuestas por intento
   * @return URL base del core validada, o vacío si no responde ninguno
   */
  public Optional<String> discoverOnLan(int attempts, Duration wait) {
    return discover(broadcastTargets(properties.discoveryPort()), attempts, wait);
  }

  /**
   * Envía la petición a los destinos indicados y devuelve el primer servidor válido.
   *
   * @param targets direcciones a las que se envía la petición
   * @param attempts número de intentos
   * @param wait tiempo de espera de respuestas por intento
   * @return URL base del core validada, o vacío
   */
  public Optional<String> discover(
      Collection<InetSocketAddress> targets, int attempts, Duration wait) {
    for (int attempt = 1; attempt <= attempts; attempt++) {
      Optional<String> found = tryOnce(targets, wait.multipliedBy(attempt));
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  private Optional<String> tryOnce(Collection<InetSocketAddress> targets, Duration wait) {
    try (DatagramSocket socket = new DatagramSocket()) {
      socket.setBroadcast(true);
      byte[] payload = (REQUEST + "\n").getBytes(StandardCharsets.UTF_8);
      for (InetSocketAddress target : targets) {
        try {
          socket.send(new DatagramPacket(payload, payload.length, target));
        } catch (IOException e) {
          log.debug("No se pudo enviar el descubrimiento a {}: {}", target, e.getMessage());
        }
      }
      long deadline = System.nanoTime() + wait.toNanos();
      byte[] buffer = new byte[2048];
      while (true) {
        long remainingMs = (deadline - System.nanoTime()) / 1_000_000;
        if (remainingMs <= 0) {
          return Optional.empty();
        }
        socket.setSoTimeout((int) remainingMs);
        DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
        try {
          socket.receive(reply);
        } catch (SocketTimeoutException e) {
          return Optional.empty();
        }
        Optional<String> url =
            parse(new String(reply.getData(), 0, reply.getLength(), StandardCharsets.UTF_8));
        if (url.isPresent() && verify(url.get())) {
          return url;
        }
      }
    } catch (IOException e) {
      log.warn("Fallo en el descubrimiento: {}", e.getMessage());
      return Optional.empty();
    }
  }

  Optional<String> parse(String message) {
    try {
      JsonNode node = mapper.readTree(message.trim());
      if (!SERVICE.equals(node.path("service").asText())) {
        return Optional.empty();
      }
      String apiUrl = node.path("apiUrl").asText("");
      return apiUrl.isBlank() ? Optional.empty() : Optional.of(stripTrailingSlash(apiUrl));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  /**
   * Comprueba que la URL pertenece a un servidor de CEFA TV.
   *
   * @param apiUrl URL base del core
   * @return {@code true} si {@code /api/v1/public/info} identifica al servidor
   */
  public boolean verify(String apiUrl) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(apiUrl + "/api/v1/public/info"))
              .timeout(Duration.ofSeconds(5))
              .GET()
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == 200
          && SERVER_SERVICE.equals(mapper.readTree(response.body()).path("service").asText());
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /**
   * Normaliza una URL manual del servidor.
   *
   * @param url URL indicada por el usuario
   * @return URL sin barra final
   */
  public static String stripTrailingSlash(String url) {
    String trimmed = url.trim();
    return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }

  static List<InetSocketAddress> broadcastTargets(int port) {
    List<InetSocketAddress> targets = new ArrayList<>();
    try {
      targets.add(new InetSocketAddress(InetAddress.getByName("255.255.255.255"), port));
      for (NetworkInterface nic : NetworkInterface.networkInterfaces().toList()) {
        if (!nic.isUp() || nic.isLoopback()) {
          continue;
        }
        nic.getInterfaceAddresses().stream()
            .map(address -> address.getBroadcast())
            .filter(broadcast -> broadcast != null)
            .forEach(broadcast -> targets.add(new InetSocketAddress(broadcast, port)));
      }
    } catch (IOException e) {
      log.warn("No se pudieron enumerar las interfaces de red: {}", e.getMessage());
    }
    return targets;
  }
}
