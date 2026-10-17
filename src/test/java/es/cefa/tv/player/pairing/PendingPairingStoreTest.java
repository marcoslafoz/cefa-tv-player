package es.cefa.tv.player.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.shared.config.PlayerProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingPairingStoreTest {

  @TempDir Path dir;

  private PendingPairingStore store;

  @BeforeEach
  void setUp() {
    PlayerProperties properties =
        new PlayerProperties(dir, "", "", 47808, 30, null, "headless", null);
    store = new PendingPairingStore(properties, new ObjectMapper().findAndRegisterModules());
  }

  @Test
  void roundTripsThePendingPairing() {
    PendingPairing pending =
        new PendingPairing(
            "http://server:8080",
            "id-1",
            "ABC-123",
            "secret",
            Instant.parse("2026-10-16T10:15:00Z"),
            3);

    store.save(pending);

    assertThat(store.load()).contains(pending);
  }

  @Test
  void isEmptyWithoutAFileAndAfterClearing() {
    assertThat(store.load()).isEmpty();
    store.save(new PendingPairing("http://s", "id", "C", "s", null, 3));
    store.clear();
    assertThat(store.load()).isEmpty();
    store.clear();
  }

  @Test
  void dropsACorruptFileInsteadOfFailing() throws Exception {
    Files.writeString(dir.resolve("pairing.json"), "{no es json");

    assertThat(store.load()).isEmpty();
    assertThat(dir.resolve("pairing.json")).doesNotExist();
  }

  @Test
  void ignoresAFileWithoutTheRequiredFields() throws Exception {
    Files.writeString(dir.resolve("pairing.json"), "{\"code\":\"ABC-123\"}");

    assertThat(store.load()).isEmpty();
  }
}
