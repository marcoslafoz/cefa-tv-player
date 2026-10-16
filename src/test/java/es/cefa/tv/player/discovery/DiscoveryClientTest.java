package es.cefa.tv.player.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import es.cefa.tv.player.shared.config.PlayerProperties;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscoveryClientTest {

  private HttpServer http;
  private DatagramSocket udp;
  private Thread responder;
  private final AtomicReference<String> received = new AtomicReference<>();
  private DiscoveryClient client;

  @BeforeEach
  void setUp() throws Exception {
    http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    http.createContext(
        "/api/v1/public/info",
        exchange -> {
          byte[] body =
              "{\"service\":\"cefa-tv-server\",\"name\":\"t\",\"version\":\"1\"}".getBytes();
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    http.start();
    udp = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
    client =
        new DiscoveryClient(
            new PlayerProperties(Path.of("."), "", "", "", 0, 30, null, "headless", null),
            new ObjectMapper());
  }

  @AfterEach
  void tearDown() {
    http.stop(0);
    udp.close();
  }

  private void respondWith(String reply) {
    responder =
        new Thread(
            () -> {
              try {
                byte[] buffer = new byte[512];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udp.receive(packet);
                received.set(
                    new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8));
                byte[] data = reply.getBytes(StandardCharsets.UTF_8);
                udp.send(new DatagramPacket(data, data.length, packet.getSocketAddress()));
              } catch (Exception ignored) {
              }
            });
    responder.setDaemon(true);
    responder.start();
  }

  @Test
  void findsTheServerAndValidatesItsInfoEndpoint() {
    String apiUrl = "http://127.0.0.1:" + http.getAddress().getPort() + "/";
    respondWith("{\"service\":\"cefa-tv\",\"version\":1,\"apiUrl\":\"" + apiUrl + "\"}");
    Optional<String> found =
        client.discover(
            List.of(new InetSocketAddress("127.0.0.1", udp.getLocalPort())),
            1,
            Duration.ofSeconds(2));
    assertThat(found).contains("http://127.0.0.1:" + http.getAddress().getPort());
    assertThat(received.get().trim()).isEqualTo("CEFATV_DISCOVER 1");
  }

  @Test
  void ignoresRepliesFromOtherServices() {
    respondWith("{\"service\":\"otra-cosa\",\"apiUrl\":\"http://127.0.0.1:1\"}");
    Optional<String> found =
        client.discover(
            List.of(new InetSocketAddress("127.0.0.1", udp.getLocalPort())),
            1,
            Duration.ofMillis(500));
    assertThat(found).isEmpty();
  }

  @Test
  void returnsEmptyWhenNobodyAnswers() {
    Optional<String> found =
        client.discover(
            List.of(new InetSocketAddress("127.0.0.1", udp.getLocalPort())),
            1,
            Duration.ofMillis(300));
    assertThat(found).isEmpty();
  }
}
