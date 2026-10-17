package es.cefa.tv.player.display;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DisplayChooseTest {

  private static final String OUTPUTS =
      "Screen 0: minimum 8 x 8, current 3840 x 1080, maximum 32767 x 32767\n"
          + "HDMI-1 connected primary 1920x1080+0+0 (normal left inverted right x axis y axis) 0mm"
          + " x 0mm\n"
          + "   1920x1080     60.00*+\n"
          + "DP-1 connected 1920x1080+1920+0 (normal left inverted right x axis y axis) 0mm x 0mm\n"
          + "   1920x1080     60.00*+\n"
          + "VGA-1 disconnected (normal)\n";

  private final List<XrandrOutput> outputs = XrandrParser.parse(OUTPUTS);

  @Test
  void prefersTheRequestedConnectedOutput() {
    assertThat(DisplayService.choose(outputs, "DP-1").orElseThrow().name()).isEqualTo("DP-1");
  }

  @Test
  void ignoresARequestedOutputThatIsDisconnectedOrUnknown() {
    assertThat(DisplayService.choose(outputs, "VGA-1").orElseThrow().name()).isEqualTo("HDMI-1");
    assertThat(DisplayService.choose(outputs, "XYZ-9").orElseThrow().name()).isEqualTo("HDMI-1");
  }

  @Test
  void usesTheAutomaticChoiceWithoutPreference() {
    assertThat(DisplayService.choose(outputs, null).orElseThrow().name()).isEqualTo("HDMI-1");
  }
}
