package es.cefa.tv.player.remote;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CursorProbeTest {

  @Test
  void normalizesKnownCursorsAndFallsBackToDefault() {
    assertThat(CursorProbe.normalize("pointer")).isEqualTo("pointer");
    assertThat(CursorProbe.normalize("TEXT")).isEqualTo("text");
    assertThat(CursorProbe.normalize(" ew-resize ")).isEqualTo("ew-resize");
    assertThat(CursorProbe.normalize("none")).isEqualTo("default");
    assertThat(CursorProbe.normalize("auto")).isEqualTo("default");
    assertThat(CursorProbe.normalize("banana")).isEqualTo("default");
    assertThat(CursorProbe.normalize(null)).isEqualTo("default");
  }

  @Test
  void dropsImageCursorsKeepingOnlyTheFallbackKeyword() {
    assertThat(CursorProbe.normalize("url(\"http://evil/x.png\") 4 4, pointer"))
        .isEqualTo("pointer");
    assertThat(CursorProbe.normalize("url(\"http://evil/x.png\"), auto")).isEqualTo("default");
    assertThat(CursorProbe.normalize("url(javascript:alert(1))")).isEqualTo("default");
  }

  @Test
  void buildsAnExpressionThatAsksTheElementUnderThePoint() {
    String expression = CursorProbe.expression(960.5, 270.0);
    assertThat(expression).contains("elementFromPoint").contains("getComputedStyle");
    assertThat(expression).endsWith("(960.5,270.0)");
  }
}
