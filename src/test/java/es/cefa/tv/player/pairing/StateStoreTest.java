package es.cefa.tv.player.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.shared.config.PlayerProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StateStoreTest {

  @TempDir Path dir;

  private StateStore store;

  @BeforeEach
  void setUp() {
    PlayerProperties properties =
        new PlayerProperties(dir, "", "", 47808, 30, null, "headless", null);
    store = new StateStore(properties, new ObjectMapper());
  }

  @Test
  void savesAndLoadsTheState() {
    PlayerState state = new PlayerState("http://servidor:8080", "id", "token", "Recepcion", 30);
    store.save(state);
    assertThat(store.load()).contains(state);
    store.clear();
    assertThat(store.load()).isEmpty();
  }

  @Test
  void aCorruptStateFileIsSetAsideAndTheReproducerPairsAgain() throws Exception {
    Files.writeString(dir.resolve("state.json"), "{esto no es json");
    assertThat(store.load()).isEmpty();
    assertThat(dir.resolve("state.json")).doesNotExist();
    assertThat(dir.resolve("state.json.corrupt")).exists();
  }
}
