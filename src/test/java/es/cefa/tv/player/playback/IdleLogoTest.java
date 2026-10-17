package es.cefa.tv.player.playback;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdleLogoTest {

  @Test
  void parsesBothLogosIgnoringCaseAndSpaces() {
    assertThat(IdleLogo.parse("ALGONTEC")).isEqualTo(IdleLogo.ALGONTEC);
    assertThat(IdleLogo.parse(" algontec ")).isEqualTo(IdleLogo.ALGONTEC);
    assertThat(IdleLogo.parse("Cefa")).isEqualTo(IdleLogo.CEFA);
  }

  @Test
  void anythingElseIsTheDefaultCefaLogo() {
    assertThat(IdleLogo.parse(null)).isEqualTo(IdleLogo.CEFA);
    assertThat(IdleLogo.parse("")).isEqualTo(IdleLogo.CEFA);
    assertThat(IdleLogo.parse("  ")).isEqualTo(IdleLogo.CEFA);
    assertThat(IdleLogo.parse("OTRO")).isEqualTo(IdleLogo.CEFA);
  }
}
