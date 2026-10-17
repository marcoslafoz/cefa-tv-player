package es.cefa.tv.player.pairing;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

final class FakePairingServer {

  record Reply(int status, String body) {}

  private final HttpServer server;
  private final Supplier<Instant> now;
  final AtomicInteger requests = new AtomicInteger();
  final List<String> secretsSeen = new CopyOnWriteArrayList<>();
  final ConcurrentLinkedQueue<Reply> requestReplies = new ConcurrentLinkedQueue<>();
  final ConcurrentLinkedQueue<Reply> pollReplies = new ConcurrentLinkedQueue<>();
  volatile Duration codeLifetime = Duration.ofMinutes(15);
  volatile Runnable onPoll = () -> {};

  FakePairingServer(Supplier<Instant> now) throws IOException {
    this.now = now;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/player-api/pairing/request", this::request);
    server.createContext("/player-api/pairing/", this::poll);
    server.start();
  }

  String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  void stop() {
    server.stop(0);
  }

  static Reply pending() {
    return new Reply(200, "{\"status\":\"PENDING\"}");
  }

  static Reply expired() {
    return new Reply(200, "{\"status\":\"EXPIRED\"}");
  }

  static Reply approved(String token) {
    String tokenField = token == null ? "" : ",\"deviceToken\":\"" + token + "\"";
    return new Reply(
        200,
        "{\"status\":\"APPROVED\",\"playerId\":\"player-1\",\"name\":\"Recepcion\""
            + tokenField
            + ",\"pollIntervalSec\":30}");
  }

  static Reply notFound() {
    return new Reply(404, "{\"errorCode\":\"NOT_FOUND\",\"message\":\"desconocido\"}");
  }

  static Reply unauthorized() {
    return new Reply(
        401, "{\"errorCode\":\"INVALID_PAIRING_SECRET\",\"message\":\"secreto no valido\"}");
  }

  static Reply failure() {
    return new Reply(503, "{\"errorCode\":\"UNAVAILABLE\",\"message\":\"no disponible\"}");
  }

  private void request(HttpExchange exchange) throws IOException {
    exchange.getRequestBody().readAllBytes();
    Reply scripted = requestReplies.poll();
    if (scripted != null) {
      reply(exchange, scripted);
      return;
    }
    int number = requests.incrementAndGet();
    String body =
        "{\"pairingId\":\"pairing-"
            + number
            + "\",\"code\":\"CODE-"
            + number
            + "\",\"secret\":\"secret-"
            + number
            + "\",\"expiresAt\":\""
            + now.get().plus(codeLifetime)
            + "\",\"pollIntervalSec\":1}";
    reply(exchange, new Reply(200, body));
  }

  private void poll(HttpExchange exchange) throws IOException {
    secretsSeen.add(exchange.getRequestHeaders().getFirst("X-Pairing-Secret"));
    onPoll.run();
    Reply scripted = pollReplies.poll();
    reply(exchange, scripted == null ? pending() : scripted);
  }

  private void reply(HttpExchange exchange, Reply reply) throws IOException {
    byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(reply.status(), bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
