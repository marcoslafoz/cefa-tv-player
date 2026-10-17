package es.cefa.tv.player.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemInfoConfigTest {

  @Test
  void withConfigAddsThePreferredOutputAndTheIdleLogoToTheTelemetry() {
    SystemInfo base =
        new SystemInfo(
            "host",
            "os",
            "k",
            "a",
            "cpu",
            4,
            1.0,
            0.1,
            100L,
            50L,
            100L,
            50L,
            null,
            10L,
            List.of(),
            List.of(),
            "0.1.0",
            "abc1234",
            "21",
            "130",
            "UTC",
            "headless",
            true,
            "container",
            null,
            null);

    JsonNode json = new ObjectMapper().valueToTree(base.withConfig("HDMI-1", "ALGONTEC"));

    assertThat(json.path("preferredOutput").asText()).isEqualTo("HDMI-1");
    assertThat(json.path("idleLogo").asText()).isEqualTo("ALGONTEC");
    assertThat(json.path("playerBuild").asText()).isEqualTo("abc1234");
    assertThat(new ObjectMapper().valueToTree(base).path("idleLogo").isNull()).isTrue();
  }
}
