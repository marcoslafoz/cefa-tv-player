package es.cefa.tv.player;

import static org.assertj.core.api.Assertions.assertThat;

import es.cefa.tv.player.status.PlayerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "cefa-tv.chromium.enabled=false",
      "cefa-tv.data-dir=${java.io.tmpdir}/cefa-tv-player-context-test",
      "cefa-tv.enrollment-key="
    })
class PlayerApplicationTests {

  @Autowired TestRestTemplate rest;
  @Autowired PlayerStatus status;

  @Test
  void contextLoadsAndServesTheLocalPages() {
    assertThat(rest.getForEntity("/player", String.class).getBody()).contains("layer-a");
    assertThat(rest.getForEntity("/status.json", String.class).getBody())
        .contains("appliedVersion");
    assertThat(rest.getForEntity("/media/" + "0".repeat(64), String.class).getStatusCode().value())
        .isEqualTo(404);
    assertThat(status).isNotNull();
  }
}
