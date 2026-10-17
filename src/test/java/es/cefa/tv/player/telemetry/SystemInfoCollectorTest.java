package es.cefa.tv.player.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.shared.config.PlayerProperties;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SystemInfoCollectorTest {

  @TempDir Path dir;

  @Test
  void collectsSystemDataAndSerializesTheContractFields() {
    PlayerProperties properties =
        new PlayerProperties(
            dir,
            "",
            "",
            47808,
            30,
            new PlayerProperties.Chromium(true, "chromium", 9222, true),
            "headless",
            new PlayerProperties.Shell(true, "container"));
    SystemInfoCollector collector =
        new SystemInfoCollector(
            properties,
            command -> Optional.of("Chromium 130.0.6723.116 built on Debian GNU/Linux 12"),
            "0.1.0-SNAPSHOT",
            "abc1234");

    SystemInfo info = collector.collect();
    JsonNode json = new ObjectMapper().valueToTree(info);

    assertThat(info.cpuCount()).isPositive();
    assertThat(info.diskFreeMb()).isPositive();
    assertThat(info.javaVersion()).isNotBlank();
    assertThat(info.chromiumVersion()).isEqualTo("130.0.6723.116");
    assertThat(info.playerVersion()).isEqualTo("0.1.0");
    assertThat(info.playerBuild()).isEqualTo("abc1234");
    assertThat(info.displayMode()).isEqualTo("headless");
    assertThat(info.shellEnabled()).isTrue();
    assertThat(info.shellMode()).isEqualTo("container");
    assertThat(
            List.of(
                "hostname",
                "os",
                "kernel",
                "arch",
                "cpuModel",
                "cpuCount",
                "cpuPercent",
                "load1",
                "ramTotalMb",
                "ramUsedMb",
                "diskTotalMb",
                "diskFreeMb",
                "tempC",
                "uptimeSec",
                "ips",
                "macs",
                "playerVersion",
                "playerBuild",
                "javaVersion",
                "chromiumVersion",
                "timezone",
                "displayMode",
                "shellEnabled",
                "shellMode"))
        .allMatch(json::has);
  }

  @Test
  void reportsHostShellModeAndNoChromiumVersionWhenBrowserIsDisabled() {
    PlayerProperties properties =
        new PlayerProperties(
            dir,
            "",
            "",
            47808,
            30,
            new PlayerProperties.Chromium(false, "chromium", 9222, true),
            "host",
            new PlayerProperties.Shell(false, "host"));

    SystemInfo info =
        new SystemInfoCollector(properties, command -> Optional.empty(), "0.1.0", " ").collect();

    assertThat(info.chromiumVersion()).isNull();
    assertThat(info.playerBuild()).isEqualTo("unknown");
    assertThat(info.shellEnabled()).isFalse();
    assertThat(info.shellMode()).isEqualTo("host");
  }
}
