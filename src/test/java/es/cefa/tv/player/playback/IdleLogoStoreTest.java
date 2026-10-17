package es.cefa.tv.player.playback;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.shared.config.PlayerProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IdleLogoStoreTest {

  @TempDir Path dir;

  private IdleLogoStore store;

  @BeforeEach
  void setUp() {
    store =
        new IdleLogoStore(
            new PlayerProperties(dir, "", "", 47808, 30, null, "headless", null),
            new ObjectMapper());
  }

  @Test
  void savesAndLoadsTheLogo() {
    store.save(IdleLogo.ALGONTEC);

    assertThat(store.load()).contains(IdleLogo.ALGONTEC);
    assertThat(dir.resolve("idle-logo.json")).exists();
  }

  @Test
  void isEmptyWithoutFile() {
    assertThat(store.load()).isEmpty();
  }

  @Test
  void anUnknownStoredValueFallsBackToCefa() throws Exception {
    Files.writeString(dir.resolve("idle-logo.json"), "{\"idleLogo\":\"RARO\"}");

    assertThat(store.load()).contains(IdleLogo.CEFA);
  }

  @Test
  void dropsACorruptFileInsteadOfFailing() throws Exception {
    Files.writeString(dir.resolve("idle-logo.json"), "{roto");

    assertThat(store.load()).isEmpty();
    assertThat(dir.resolve("idle-logo.json")).doesNotExist();
  }
}
