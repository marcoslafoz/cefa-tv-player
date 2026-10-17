package es.cefa.tv.player.remote;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.cefa.tv.player.browser.CdpClient;
import es.cefa.tv.player.playback.PlaybackHold;
import es.cefa.tv.player.shared.config.PlayerProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RemoteServiceCursorTest {

  private final ObjectMapper json = new ObjectMapper();
  private final List<Object> messages = new CopyOnWriteArrayList<>();
  private final List<String> calls = new CopyOnWriteArrayList<>();
  private final AtomicReference<String> cursorUnderPointer = new AtomicReference<>("pointer");
  private RemoteService service;

  @BeforeEach
  void setUp() {
    PlayerProperties properties =
        new PlayerProperties(
            Path.of("."),
            "",
            "",
            47808,
            30,
            new PlayerProperties.Chromium(true, "chromium", 9222, true),
            "headless",
            null);
    CdpClient cdp =
        new CdpClient(properties, json) {
          @Override
          public JsonNode call(String method, ObjectNode params) throws IOException {
            calls.add(method);
            ObjectNode result = json.createObjectNode();
            if ("Runtime.evaluate".equals(method)) {
              result.putObject("result").put("value", cursorUnderPointer.get());
            }
            return result;
          }
        };
    service =
        new RemoteService(cdp, properties, json, new PlaybackHold(java.time.Clock.systemUTC()));
    service.setMessageSink(messages::add);
    service.start(5, 60, 1280);
  }

  @AfterEach
  void tearDown() {
    service.stop();
  }

  private void move(double x, double y) throws Exception {
    service.input(json.readTree("{\"kind\":\"MOUSE_MOVE\",\"x\":" + x + ",\"y\":" + y + "}"));
  }

  @Test
  void reportsTheCursorOnlyWhenItChangesAndAtMostEveryHundredMilliseconds() throws Exception {
    move(0.5, 0.5);
    move(0.51, 0.5);
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0)).isEqualTo(java.util.Map.of("type", "CURSOR", "css", "pointer"));

    Thread.sleep(130);
    move(0.52, 0.5);
    assertThat(messages).hasSize(1);

    cursorUnderPointer.set("text");
    Thread.sleep(130);
    move(0.53, 0.5);
    assertThat(messages).hasSize(2);
    assertThat(messages.get(1)).isEqualTo(java.util.Map.of("type", "CURSOR", "css", "text"));
  }

  @Test
  void doesNotQueryTheCursorOnClicksOrAfterStopping() throws Exception {
    service.input(
        json.readTree("{\"kind\":\"MOUSE_DOWN\",\"x\":0.5,\"y\":0.5,\"button\":\"left\"}"));
    assertThat(calls).doesNotContain("Runtime.evaluate");
    service.stop();
    calls.clear();
    move(0.5, 0.5);
    assertThat(calls).isEmpty();
  }
}
