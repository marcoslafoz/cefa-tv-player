package es.cefa.tv.player.playback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.shared.config.PlayerProperties;
import es.cefa.tv.player.status.PlayerStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VolumeServiceTest {

  @TempDir Path dir;

  private final PlaybackBroadcaster broadcaster = mock(PlaybackBroadcaster.class);
  private final PlayerStatus status = new PlayerStatus("test");
  private VolumeStore store;
  private VolumeService service;

  @BeforeEach
  void setUp() {
    store =
        new VolumeStore(
            new PlayerProperties(dir, "", "", 47808, 30, null, "headless", null),
            new ObjectMapper());
    service = new VolumeService(store, broadcaster, status);
    service.load();
  }

  @Test
  void defaultsTo100WhenNothingIsStored() {
    assertThat(service.current()).isEqualTo(100);
    assertThat(status.snapshot(0)).containsEntry("volume", 100);
  }

  @Test
  void changingTheVolumePersistsItReportsItAndNotifiesThePage() {
    service.set(30);

    assertThat(service.current()).isEqualTo(30);
    assertThat(status.volume()).isEqualTo(30);
    assertThat(store.load()).hasValue(30);
    verify(broadcaster).publishTransient(Map.of("type", "CONFIG", "volume", 30));
  }

  @Test
  void settingTheSameVolumeDoesNothing() {
    service.set(100);

    verify(broadcaster, never()).publishTransient(any());
    assertThat(dir.resolve("volume.json")).doesNotExist();
  }

  @Test
  void aNullVolumeReturnsTo100AndOutOfRangeValuesAreClamped() {
    service.set(0);
    assertThat(service.current()).isZero();

    service.set(null);
    assertThat(service.current()).isEqualTo(100);

    service.set(-5);
    assertThat(service.current()).isZero();

    service.set(250);
    assertThat(service.current()).isEqualTo(100);
  }

  @Test
  void survivesARestartWithoutServer() {
    service.set(55);

    VolumeService restarted =
        new VolumeService(store, mock(PlaybackBroadcaster.class), new PlayerStatus("test"));
    restarted.load();

    assertThat(restarted.current()).isEqualTo(55);
  }

  @Test
  void aCorruptFileFallsBackTo100AndIsDropped() throws Exception {
    Files.writeString(dir.resolve("volume.json"), "{roto");

    assertThat(store.load()).isEmpty();
    assertThat(dir.resolve("volume.json")).doesNotExist();
  }
}
