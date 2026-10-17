package es.cefa.tv.player.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.cefa.tv.player.browser.CdpClient;
import es.cefa.tv.player.remote.RemoteService;
import es.cefa.tv.player.shared.config.PlayerProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PreviewServiceTest {

  private final ObjectMapper json = new ObjectMapper();
  private final AtomicReference<Instant> now =
      new AtomicReference<>(Instant.parse("2026-10-16T10:00:00Z"));
  private final Clock clock =
      new Clock() {
        @Override
        public ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
          return this;
        }

        @Override
        public Instant instant() {
          return now.get();
        }
      };
  private final List<ObjectNode> screenshots = new CopyOnWriteArrayList<>();
  private final List<String> methods = new CopyOnWriteArrayList<>();
  private final List<Map<String, Object>> sent = new CopyOnWriteArrayList<>();
  private final AtomicBoolean sinkFree = new AtomicBoolean(true);
  private final AtomicReference<IntFunction<byte[]>> image =
      new AtomicReference<>(quality -> new byte[] {1, 2, 3, 4, 5, 6});
  private final RemoteService remote = mock(RemoteService.class);
  private PreviewService service;

  @BeforeEach
  void setUp() {
    PlayerProperties properties = properties(true);
    service = new PreviewService(fakeCdp(properties), properties, remote, clock);
    service.setMessageSink(
        message -> {
          if (!sinkFree.get()) {
            return false;
          }
          @SuppressWarnings("unchecked")
          Map<String, Object> typed = (Map<String, Object>) message;
          sent.add(typed);
          return true;
        });
  }

  @AfterEach
  void tearDown() {
    service.stop();
  }

  private PlayerProperties properties(boolean chromium) {
    return new PlayerProperties(
        Path.of("."),
        "",
        "",
        47808,
        30,
        new PlayerProperties.Chromium(chromium, "chromium", 9222, true),
        "headless",
        null);
  }

  private CdpClient fakeCdp(PlayerProperties properties) {
    return new CdpClient(properties, json) {
      @Override
      public JsonNode call(String method, ObjectNode params) throws IOException {
        methods.add(method);
        ObjectNode result = json.createObjectNode();
        if ("Page.getLayoutMetrics".equals(method)) {
          ObjectNode viewport = result.putObject("cssVisualViewport");
          viewport.put("clientWidth", 1920);
          viewport.put("clientHeight", 1080);
        }
        if ("Page.captureScreenshot".equals(method)) {
          screenshots.add(params.deepCopy());
          result.put(
              "data",
              Base64.getEncoder()
                  .encodeToString(image.get().apply(params.path("quality").asInt())));
        }
        return result;
      }
    };
  }

  @Test
  void neverCapturesWithoutAStartRequest() {
    service.capture();

    assertThat(methods).isEmpty();
    assertThat(sent).isEmpty();
    assertThat(service.isActive()).isFalse();
  }

  @Test
  void capturesAReducedFrameWithAClipAndSendsItAsAPreviewFrame() {
    service.configure(5, 320, 50);

    service.capture();

    assertThat(screenshots).hasSize(1);
    ObjectNode request = screenshots.get(0);
    assertThat(request.path("format").asText()).isEqualTo("jpeg");
    assertThat(request.path("quality").asInt()).isEqualTo(50);
    assertThat(request.path("clip").path("width").asDouble()).isEqualTo(1920);
    assertThat(request.path("clip").path("height").asDouble()).isEqualTo(1080);
    assertThat(request.path("clip").path("scale").asDouble()).isEqualTo(320.0 / 1920.0);
    assertThat(sent).hasSize(1);
    Map<String, Object> frame = sent.get(0);
    assertThat(frame)
        .containsEntry("type", "PREVIEW_FRAME")
        .containsEntry("w", 320)
        .containsEntry("h", 180)
        .containsEntry("at", "2026-10-16T10:00:00Z");
    assertThat(frame.get("jpegBase64"))
        .isEqualTo(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3, 4, 5, 6}));
  }

  @Test
  void skipsAnIdenticalImageUntilTheKeepAliveAndSendsAChangedOne() {
    service.configure(5, 320, 50);
    service.capture();

    now.set(now.get().plusSeconds(5));
    service.capture();
    now.set(now.get().plusSeconds(30));
    service.capture();
    assertThat(sent).hasSize(1);

    now.set(now.get().plusSeconds(30));
    service.capture();
    assertThat(sent).hasSize(2);

    image.set(quality -> new byte[] {9, 9, 9});
    now.set(now.get().plusSeconds(5));
    service.capture();
    assertThat(sent).hasSize(3);
  }

  @Test
  void retriesOnTheNextCaptureWhenTheChannelIsBusy() {
    service.configure(5, 320, 50);
    sinkFree.set(false);
    service.capture();
    assertThat(sent).isEmpty();

    sinkFree.set(true);
    service.capture();

    assertThat(sent).hasSize(1);
  }

  @Test
  void shrinksQualityThenWidthToStayUnderTheSizeLimitAndRemembersIt() {
    image.set(quality -> new byte[quality > 25 ? 40 * 1024 : 12 * 1024]);
    service.configure(5, 320, 60);

    service.capture();

    assertThat(sent).hasSize(1);
    String data = (String) sent.get(0).get("jpegBase64");
    assertThat(Base64.getDecoder().decode(data).length)
        .isLessThanOrEqualTo(PreviewService.MAX_FRAME_BYTES);
    List<Integer> qualities = new ArrayList<>();
    screenshots.forEach(shot -> qualities.add(shot.path("quality").asInt()));
    assertThat(qualities).startsWith(60, 45, 30).endsWith(20);

    screenshots.clear();
    now.set(now.get().plusSeconds(61));
    service.capture();
    assertThat(screenshots).hasSize(1);
    assertThat(screenshots.get(0).path("quality").asInt()).isEqualTo(20);
  }

  @Test
  void doesNotCaptureDuringARemoteControlSession() {
    when(remote.isActive()).thenReturn(true);
    service.configure(5, 320, 50);

    service.capture();

    assertThat(methods).isEmpty();
    assertThat(sent).isEmpty();
  }

  @Test
  void stopsCapturingAfterStop() {
    service.configure(5, 320, 50);
    service.capture();
    methods.clear();

    service.stop();
    now.set(now.get().plusSeconds(120));
    service.capture();

    assertThat(service.isActive()).isFalse();
    assertThat(methods).isEmpty();
  }

  @Test
  void doesNothingWhenChromiumIsDisabled() {
    PlayerProperties disabled = properties(false);
    service = new PreviewService(fakeCdp(disabled), disabled, remote, clock);
    service.setMessageSink(message -> sent.add(Map.of()));
    service.configure(5, 320, 50);

    service.capture();

    assertThat(methods).isEmpty();
    assertThat(sent).isEmpty();
  }

  @Test
  void clampsTheRequestedParameters() {
    assertThat(service.configure(0, 0, 0)).isEqualTo(PreviewService.DEFAULT_INTERVAL_SEC);
    assertThat(service.configure(9999, 5000, 500)).isEqualTo(300);
    service.capture();
    assertThat(screenshots.get(0).path("quality").asInt()).isEqualTo(90);
    assertThat(screenshots.get(0).path("clip").path("scale").asDouble())
        .isEqualTo(1.0 / 1.0 * 1280.0 / 1920.0);
  }
}
