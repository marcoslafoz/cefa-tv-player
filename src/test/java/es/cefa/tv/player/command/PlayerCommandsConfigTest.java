package es.cefa.tv.player.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.browser.BrowserController;
import es.cefa.tv.player.display.DisplayService;
import es.cefa.tv.player.pairing.StateStore;
import es.cefa.tv.player.playback.IdleLogoService;
import es.cefa.tv.player.playback.VolumeService;
import es.cefa.tv.player.preview.PreviewService;
import es.cefa.tv.player.remote.RemoteService;
import es.cefa.tv.player.shell.ShellService;
import es.cefa.tv.player.status.PlayerStatus;
import es.cefa.tv.player.storage.FileStore;
import es.cefa.tv.player.storage.ManifestStore;
import es.cefa.tv.player.sync.PlayerSocket;
import es.cefa.tv.player.sync.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

class PlayerCommandsConfigTest {

  private final ObjectMapper json = new ObjectMapper();
  private final DisplayService display = mock(DisplayService.class);
  private final PreviewService preview = mock(PreviewService.class);
  private final IdleLogoService idleLogo = mock(IdleLogoService.class);
  private final VolumeService volume = mock(VolumeService.class);
  private PlayerCommands commands;

  @BeforeEach
  void setUp() {
    commands =
        new PlayerCommands(
            mock(PlayerSocket.class),
            mock(RemoteService.class),
            preview,
            idleLogo,
            volume,
            mock(SyncService.class),
            mock(BrowserController.class),
            mock(StateStore.class),
            mock(ManifestStore.class),
            mock(FileStore.class),
            mock(ShellService.class),
            display,
            mock(PlayerStatus.class),
            mock(ConfigurableApplicationContext.class),
            event -> {});
  }

  @Test
  void appliesThePreferredOutputOfAConfigMessage() throws Exception {
    commands.handle(json.readTree("{\"type\":\"CONFIG\",\"preferredOutput\":\"HDMI-2\"}"));

    verify(display).setPreferredOutput("HDMI-2");
  }

  @Test
  void aNullPreferredOutputMeansAutomaticSelection() throws Exception {
    commands.handle(json.readTree("{\"type\":\"CONFIG\",\"preferredOutput\":null}"));

    verify(display).setPreferredOutput(null);
  }

  @Test
  void ignoresAConfigMessageWithoutThePreferredOutputField() throws Exception {
    commands.handle(json.readTree("{\"type\":\"CONFIG\"}"));

    verify(display, never()).setPreferredOutput(any());
  }

  @Test
  void appliesTheVolumeOfAConfigMessage() throws Exception {
    commands.handle(json.readTree("{\"type\":\"CONFIG\",\"volume\":35}"));

    verify(volume).set(35);
  }

  @Test
  void aNullVolumeMeansTheDefault() throws Exception {
    commands.handle(json.readTree("{\"type\":\"CONFIG\",\"volume\":null}"));

    verify(volume).set(null);
  }

  @Test
  void ignoresAConfigMessageWithoutTheVolumeField() throws Exception {
    commands.handle(json.readTree("{\"type\":\"CONFIG\",\"preferredOutput\":\"HDMI-2\"}"));

    verify(volume, never()).set(any());
  }

  @Test
  void startsThePreviewWithTheRequestedParameters() throws Exception {
    commands.handle(
        json.readTree(
            "{\"type\":\"PREVIEW_START\",\"intervalSec\":5,\"maxWidth\":320,\"quality\":50}"));

    verify(preview).start(5, 320, 50);
  }

  @Test
  void startsThePreviewWithDefaultsWhenParametersAreMissing() throws Exception {
    commands.handle(json.readTree("{\"type\":\"PREVIEW_START\"}"));

    verify(preview).start(0, 0, 0);
  }

  @Test
  void stopsThePreview() throws Exception {
    commands.handle(json.readTree("{\"type\":\"PREVIEW_STOP\"}"));

    verify(preview).stop();
  }
}
