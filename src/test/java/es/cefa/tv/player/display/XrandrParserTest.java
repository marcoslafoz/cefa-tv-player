package es.cefa.tv.player.display;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class XrandrParserTest {

  static final String LAPTOP_AND_HDMI =
      """
      Screen 0: minimum 320 x 200, current 3286 x 1080, maximum 16384 x 16384
      eDP-1 connected primary 1366x768+0+0 (normal left inverted right x axis y axis) 309mm x 174mm
         1366x768      60.00*+  40.00
         1280x720      60.00    59.99    59.86
      HDMI-1 connected 1920x1080+1366+0 (normal left inverted right x axis y axis) 527mm x 296mm
         1920x1080     60.00*+  50.00    59.94
         1920x1080i    60.00    50.00    59.94
         1280x720      60.00    50.00    59.94
      DP-1 disconnected (normal left inverted right x axis y axis)
      VGA-1 disconnected (normal left inverted right x axis y axis)
      """;

  static final String OFF_AND_ROTATED =
      """
      Screen 0: minimum 8 x 8, current 1080 x 1920, maximum 32767 x 32767
      HDMI-1 connected 1080x1920+0+0 left (normal left inverted right x axis y axis) 527mm x 296mm
         1920x1080     60.00*+
         1280x720      60.00
      HDMI-2 connected (normal left inverted right x axis y axis)
         3840x2160     30.00 +  25.00
         1920x1080     60.00    50.00
      """;

  static final String XVFB =
      """
      Screen 0: minimum 1 x 1, current 1920 x 1080, maximum 32767 x 32767
      screen connected 1920x1080+0+0 0mm x 0mm
         1920x1080      0.00*
      """;

  @Test
  void parsesLaptopWithExternalMonitor() {
    List<XrandrOutput> outputs = XrandrParser.parse(LAPTOP_AND_HDMI);

    assertThat(outputs)
        .extracting(XrandrOutput::name)
        .containsExactly("eDP-1", "HDMI-1", "DP-1", "VGA-1");
    XrandrOutput edp = outputs.get(0);
    assertThat(edp.connected()).isTrue();
    assertThat(edp.primary()).isTrue();
    assertThat(edp.geometry()).contains(new DisplayGeometry(0, 0, 1366, 768));
    assertThat(edp.currentMode().orElseThrow().name()).isEqualTo("1366x768");
    assertThat(edp.preferredMode().orElseThrow().refreshHz()).isEqualTo(60.0);
    XrandrOutput hdmi = outputs.get(1);
    assertThat(hdmi.primary()).isFalse();
    assertThat(hdmi.geometry()).contains(new DisplayGeometry(1366, 0, 1920, 1080));
    assertThat(hdmi.modeNames()).containsExactly("1920x1080", "1920x1080i", "1280x720");
    assertThat(outputs.get(2).connected()).isFalse();
    assertThat(outputs.get(2).geometry()).isEmpty();
  }

  @Test
  void parsesRotatedOutputAndConnectedButOff() {
    List<XrandrOutput> outputs = XrandrParser.parse(OFF_AND_ROTATED);

    XrandrOutput rotated = outputs.get(0);
    assertThat(rotated.geometry()).contains(new DisplayGeometry(0, 0, 1080, 1920));
    XrandrOutput off = outputs.get(1);
    assertThat(off.connected()).isTrue();
    assertThat(off.geometry()).isEmpty();
    assertThat(off.currentMode()).isEmpty();
    assertThat(off.preferredMode().orElseThrow().name()).isEqualTo("3840x2160");
    assertThat(off.preferredMode().orElseThrow().refreshHz()).isEqualTo(30.0);
  }

  @Test
  void parsesXvfbVirtualScreen() {
    List<XrandrOutput> outputs = XrandrParser.parse(XVFB);

    assertThat(outputs).hasSize(1);
    DisplayOutput display = outputs.get(0).toDisplay(true);
    assertThat(display.output()).isEqualTo("screen");
    assertThat(display.width()).isEqualTo(1920);
    assertThat(display.height()).isEqualTo(1080);
    assertThat(display.refreshHz()).isNull();
    assertThat(display.selected()).isTrue();
  }

  @Test
  void convertsInactiveOutputUsingPreferredMode() {
    XrandrOutput off = XrandrParser.parse(OFF_AND_ROTATED).get(1);

    DisplayOutput display = off.toDisplay(false);

    assertThat(display.width()).isEqualTo(3840);
    assertThat(display.height()).isEqualTo(2160);
    assertThat(display.xOffset()).isNull();
    assertThat(display.modes()).containsExactly("3840x2160", "1920x1080");
  }

  @Test
  void returnsEmptyListForEmptyOrUnrelatedText() {
    assertThat(XrandrParser.parse("")).isEmpty();
    assertThat(XrandrParser.parse("Can't open display :0")).isEmpty();
  }
}
