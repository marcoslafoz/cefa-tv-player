package es.cefa.tv.player.playback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.shared.config.PlayerProperties;
import es.cefa.tv.player.status.PlayerStatus;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IdleLogoServiceTest {

  @TempDir Path dir;

  private final PlaybackBroadcaster broadcaster = mock(PlaybackBroadcaster.class);
  private final PlayerStatus status = new PlayerStatus("test");
  private IdleLogoStore store;
  private IdleLogoService service;

  @BeforeEach
  void setUp() {
    store =
        new IdleLogoStore(
            new PlayerProperties(dir, "", "", 47808, 30, null, "headless", null),
            new ObjectMapper());
    service = new IdleLogoService(store, broadcaster, status);
    service.load();
  }

  @Test
  void defaultsToCefaWhenNothingIsStored() {
    assertThat(service.current()).isEqualTo("CEFA");
    assertThat(status.idleLogo()).isEqualTo("CEFA");
    assertThat(status.snapshot(0)).containsEntry("idleLogo", "CEFA");
  }

  @Test
  void changingTheLogoPersistsItReportsItAndNotifiesThePage() {
    service.set("ALGONTEC");

    assertThat(service.current()).isEqualTo("ALGONTEC");
    assertThat(status.idleLogo()).isEqualTo("ALGONTEC");
    assertThat(store.load()).contains(IdleLogo.ALGONTEC);
    verify(broadcaster).publishTransient(Map.of("type", "CONFIG", "idleLogo", "ALGONTEC"));
  }

  @Test
  void settingTheSameLogoDoesNothing() {
    service.set("CEFA");

    verify(broadcaster, never()).publishTransient(any());
    assertThat(dir.resolve("idle-logo.json")).doesNotExist();
  }

  @Test
  void anUnknownOrNullValueReturnsToCefa() {
    service.set("ALGONTEC");

    service.set("DESCONOCIDO");
    assertThat(service.current()).isEqualTo("CEFA");

    service.set("ALGONTEC");
    service.set(null);
    assertThat(service.current()).isEqualTo("CEFA");
    assertThat(store.load()).contains(IdleLogo.CEFA);
  }

  @Test
  void survivesARestartWithoutServer() {
    service.set("ALGONTEC");

    IdleLogoService restarted =
        new IdleLogoService(store, mock(PlaybackBroadcaster.class), new PlayerStatus("test"));
    restarted.load();

    assertThat(restarted.current()).isEqualTo("ALGONTEC");
  }
}
