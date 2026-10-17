package es.cefa.tv.player.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import es.cefa.tv.player.status.PlayerStatus;
import org.junit.jupiter.api.Test;

class BuildInfoTest {

  @Test
  void stripsTheSnapshotSuffixFromTheVersion() {
    assertThat(BuildInfo.version("0.1.0-SNAPSHOT")).isEqualTo("0.1.0");
    assertThat(BuildInfo.version("0.1.0")).isEqualTo("0.1.0");
    assertThat(BuildInfo.version(" 1.2.3 ")).isEqualTo("1.2.3");
  }

  @Test
  void usesDevWhenThereIsNoVersion() {
    assertThat(BuildInfo.version(null)).isEqualTo("dev");
    assertThat(BuildInfo.version("  ")).isEqualTo("dev");
  }

  @Test
  void usesUnknownWhenThereIsNoBuild() {
    assertThat(BuildInfo.build(null)).isEqualTo("unknown");
    assertThat(BuildInfo.build("")).isEqualTo("unknown");
    assertThat(BuildInfo.build(" ")).isEqualTo("unknown");
    assertThat(BuildInfo.build("abc1234")).isEqualTo("abc1234");
  }

  @Test
  void theStatusSnapshotExposesVersionAndBuild() {
    PlayerStatus status = new PlayerStatus("0.1.0", "abc1234");

    assertThat(status.snapshot(0))
        .containsEntry("version", "0.1.0")
        .containsEntry("build", "abc1234");
    assertThat(status.version()).isEqualTo("0.1.0");
    assertThat(status.build()).isEqualTo("abc1234");
    assertThat(new PlayerStatus("0.1.0").build()).isEqualTo("unknown");
  }
}
