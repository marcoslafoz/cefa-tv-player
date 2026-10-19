package es.cefa.tv.player.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.cefa.tv.player.browser.ChromiumLauncher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaintenanceServiceTest {

  private ChromiumLauncher launcher;
  private MaintenanceService maintenance;

  @BeforeEach
  void setUp() {
    launcher = mock(ChromiumLauncher.class);
    maintenance = new MaintenanceService(launcher);
  }

  @Test
  void enteringPausesTheBrowser() {
    maintenance.enter();

    verify(launcher).pause();
  }

  @Test
  void enteringTwiceDoesNotPauseAgain() {
    when(launcher.isPaused()).thenReturn(true);

    maintenance.enter();

    verify(launcher, never()).pause();
  }

  @Test
  void resumingReopensTheBrowserOnlyWhenPaused() {
    when(launcher.isPaused()).thenReturn(true);
    maintenance.resume();
    verify(launcher).resume();

    when(launcher.isPaused()).thenReturn(false);
    maintenance.resume();
    verify(launcher).resume();
  }

  @Test
  void reportsTheLauncherState() {
    when(launcher.isPaused()).thenReturn(true);

    assertThat(maintenance.isActive()).isTrue();
  }
}
