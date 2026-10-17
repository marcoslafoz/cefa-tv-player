package es.cefa.tv.player.display;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.shared.config.PlayerProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DisplayPreferenceStoreTest {

  @TempDir Path dir;

  private DisplayPreferenceStore store;

  @BeforeEach
  void setUp() {
    store =
        new DisplayPreferenceStore(
            new PlayerProperties(dir, "", "", 47808, 30, null, "headless", null),
            new ObjectMapper());
  }

  @Test
  void savesAndLoadsThePreferredOutput() {
    store.save("HDMI-2");

    assertThat(store.load()).contains("HDMI-2");
  }

  @Test
  void savingNullForgetsThePreference() {
    store.save("HDMI-2");
    store.save(null);

    assertThat(store.load()).isEmpty();
    assertThat(dir.resolve("display.json")).doesNotExist();
  }

  @Test
  void isEmptyWithoutFileAndDropsACorruptOne() throws Exception {
    assertThat(store.load()).isEmpty();
    Files.writeString(dir.resolve("display.json"), "{roto");

    assertThat(store.load()).isEmpty();
    assertThat(dir.resolve("display.json")).doesNotExist();
  }
}
