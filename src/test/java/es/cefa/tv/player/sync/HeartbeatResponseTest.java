package es.cefa.tv.player.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.sync.dto.HeartbeatResponse;
import org.junit.jupiter.api.Test;

class HeartbeatResponseTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void readsThePreferredOutputFromTheConfig() throws Exception {
    HeartbeatResponse response =
        mapper.readValue(
            "{\"desiredVersion\":3,\"commands\":[],\"config\":{\"preferredOutput\":\"HDMI-2\"}}",
            HeartbeatResponse.class);

    assertThat(response.config()).isNotNull();
    assertThat(response.config().preferredOutput()).isEqualTo("HDMI-2");
  }

  @Test
  void readsTheIdleLogoFromTheConfig() throws Exception {
    HeartbeatResponse response =
        mapper.readValue(
            "{\"desiredVersion\":3,\"config\":{\"preferredOutput\":null,\"idleLogo\":\"ALGONTEC\"}}",
            HeartbeatResponse.class);

    assertThat(response.config().idleLogo()).isEqualTo("ALGONTEC");
    assertThat(response.config().preferredOutput()).isNull();
  }

  @Test
  void distinguishesAutomaticSelectionFromAnAbsentConfig() throws Exception {
    HeartbeatResponse automatic =
        mapper.readValue(
            "{\"desiredVersion\":3,\"config\":{\"preferredOutput\":null}}",
            HeartbeatResponse.class);
    HeartbeatResponse absent =
        mapper.readValue("{\"desiredVersion\":3,\"commands\":[]}", HeartbeatResponse.class);

    assertThat(automatic.config()).isNotNull();
    assertThat(automatic.config().preferredOutput()).isNull();
    assertThat(absent.config()).isNull();
  }
}
