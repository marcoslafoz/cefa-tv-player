package es.cefa.tv.player.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import es.cefa.tv.player.shared.model.Manifest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class FakeServer {

  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
  private final HttpServer server;
  final Map<String, byte[]> files = new ConcurrentHashMap<>();
  final List<String> rangeHeaders = new CopyOnWriteArrayList<>();
  final List<String> acks = new CopyOnWriteArrayList<>();
  final List<String> zipRequests = new CopyOnWriteArrayList<>();
  volatile Manifest manifest;

  FakeServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/player-api/manifest", this::manifest);
    server.createContext("/player-api/files", this::files);
    server.createContext("/player-api/ack", this::ack);
    server.start();
  }

  String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  void stop() {
    server.stop(0);
  }

  private void manifest(HttpExchange exchange) throws IOException {
    String etag = "\"" + manifest.version() + "\"";
    if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
      exchange.sendResponseHeaders(304, -1);
      exchange.close();
      return;
    }
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
      gzip.write(mapper.writeValueAsBytes(manifest));
    }
    exchange.getResponseHeaders().add("Content-Encoding", "gzip");
    exchange.getResponseHeaders().add("ETag", etag);
    reply(exchange, 200, buffer.toByteArray());
  }

  private void files(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    if ("POST".equals(exchange.getRequestMethod())) {
      JsonNode body = mapper.readTree(exchange.getRequestBody());
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
        for (JsonNode sha : body.get("sha256")) {
          zipRequests.add(sha.asText());
          zip.putNextEntry(new ZipEntry(sha.asText()));
          zip.write(files.get(sha.asText()));
          zip.closeEntry();
        }
      }
      reply(exchange, 200, buffer.toByteArray());
      return;
    }
    byte[] data = files.get(path.substring(path.lastIndexOf('/') + 1));
    String range = exchange.getRequestHeaders().getFirst("Range");
    if (range != null) {
      rangeHeaders.add(range);
      int start = Integer.parseInt(range.substring("bytes=".length(), range.length() - 1));
      byte[] tail = java.util.Arrays.copyOfRange(data, start, data.length);
      reply(exchange, 206, tail);
    } else {
      reply(exchange, 200, data);
    }
  }

  private void ack(HttpExchange exchange) throws IOException {
    JsonNode body = mapper.readTree(exchange.getRequestBody());
    acks.add(body.get("version").asLong() + ":" + body.get("status").asText());
    reply(exchange, 204, new byte[0]);
  }

  private void reply(HttpExchange exchange, int status, byte[] body) throws IOException {
    exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
    if (body.length > 0) {
      exchange.getResponseBody().write(body);
    }
    exchange.close();
  }
}
