package es.cefa.tv.player.browser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.display.DisplayGeometry;
import es.cefa.tv.player.display.DisplayService;
import es.cefa.tv.player.shared.config.PlayerProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChromiumLauncherTest {

  @TempDir Path dir;

  private ChromiumLauncher launcher;

  @BeforeEach
  void setUp() {
    PlayerProperties properties =
        new PlayerProperties(
            dir,
            "",
            "",
            47808,
            30,
            new PlayerProperties.Chromium(true, "chromium", 9222, true),
            "headless",
            null);
    BrowserController browser = mock(BrowserController.class);
    when(browser.playerUrl()).thenReturn("http://localhost:8081/player");
    DisplayService display = mock(DisplayService.class);
    when(display.selectedGeometry())
        .thenReturn(Optional.of(new DisplayGeometry(1920, 0, 1280, 720)));
    launcher = new ChromiumLauncher(properties, browser, display);
  }

  @Test
  void launchesAKioskWithoutAnyBrowserInterface() {
    List<String> command = launcher.command();
    assertThat(command)
        .contains(
            "--kiosk",
            "--noerrdialogs",
            "--disable-infobars",
            "--disable-translate",
            "--disable-save-password-bubble",
            "--password-store=basic",
            "--use-mock-keychain",
            "--disable-session-crashed-bubble",
            "--hide-crash-restore-bubble",
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-component-update",
            "--disable-pinch",
            "--overscroll-history-navigation=0",
            "--autoplay-policy=no-user-gesture-required",
            "--lang=es-ES",
            "--accept-lang=es-ES,es",
            "--window-position=1920,0",
            "--window-size=1280,720",
            "--remote-debugging-port=9222",
            "http://localhost:8081/player");
    assertThat(command.stream().filter(flag -> flag.startsWith("--disable-features="))).hasSize(1);
    assertThat(command)
        .anyMatch(
            flag ->
                flag.startsWith("--disable-features=")
                    && flag.contains("Translate")
                    && flag.contains("PasswordManagerOnboarding")
                    && flag.contains("MediaRouter"));
    assertThat(command).doesNotContain("--enable-automation");
  }

  @Test
  void managedPolicyShipsInTheImageAndTurnsOffTheBrowserFeatures() throws Exception {
    Path policy = Path.of("docker/chromium/cefa-tv.json");
    var json = new ObjectMapper().readTree(Files.readString(policy));
    assertThat(json.get("TranslateEnabled").asBoolean()).isFalse();
    assertThat(json.get("PasswordManagerEnabled").asBoolean()).isFalse();
    assertThat(json.get("AutofillAddressEnabled").asBoolean()).isFalse();
    assertThat(json.get("AutofillCreditCardEnabled").asBoolean()).isFalse();
    assertThat(json.get("SavingBrowserHistoryDisabled").asBoolean()).isTrue();
    assertThat(json.get("BrowserSignin").asInt()).isZero();
    assertThat(json.get("SyncDisabled").asBoolean()).isTrue();
    assertThat(json.get("MetricsReportingEnabled").asBoolean()).isFalse();
    assertThat(json.get("DefaultNotificationsSetting").asInt()).isEqualTo(2);
    assertThat(json.get("DefaultPopupsSetting").asInt()).isEqualTo(2);
    assertThat(json.get("DefaultGeolocationSetting").asInt()).isEqualTo(2);
    assertThat(json.get("DownloadRestrictions").asInt()).isEqualTo(3);
  }

  @Test
  void cleansVolatileProfileFilesButKeepsCookies() throws Exception {
    Path profile = dir.resolve("chromium");
    Files.createDirectories(profile.resolve("Default/Sessions"));
    Files.writeString(profile.resolve("Local State"), "{}");
    Files.writeString(profile.resolve("Default/Preferences"), "{}");
    Files.writeString(profile.resolve("Default/Login Data"), "x");
    Files.writeString(profile.resolve("Default/Cookies"), "galletas");
    Files.writeString(profile.resolve("Default/Sessions/Session_1"), "x");
    Files.writeString(profile.resolve("SingletonLock"), "x");

    launcher.cleanProfile();

    assertThat(profile.resolve("Local State")).doesNotExist();
    assertThat(profile.resolve("Default/Preferences")).doesNotExist();
    assertThat(profile.resolve("Default/Login Data")).doesNotExist();
    assertThat(profile.resolve("Default/Sessions/Session_1")).doesNotExist();
    assertThat(profile.resolve("SingletonLock")).doesNotExist();
    assertThat(profile.resolve("Default/Cookies")).exists();
  }
}
