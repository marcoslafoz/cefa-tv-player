package es.cefa.tv.player.display;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.cefa.tv.player.shared.process.CommandRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DisplayServiceTest {

  private static final String HEADER =
      "Screen 0: minimum 8 x 8, current 1920 x 1080, maximum 32767 x 32767\n";
  private static final String HDMI_ACTIVE =
      "HDMI-1 connected primary 1920x1080+0+0 (normal left inverted right x axis y axis) 0mm x"
          + " 0mm\n"
          + "   1920x1080     60.00*+\n"
          + "   1280x720      60.00\n";
  private static final String DP_SECOND =
      "DP-1 connected 1920x1080+1920+0 (normal left inverted right x axis y axis) 0mm x 0mm\n"
          + "   1920x1080     60.00*+\n";

  private final FakeRunner runner = new FakeRunner();
  private final List<DisplayChangedEvent> events = new ArrayList<>();
  private DisplayService service;

  @BeforeEach
  void setUp() {
    service = new DisplayService(runner, event -> events.add((DisplayChangedEvent) event));
  }

  @Test
  void choosesPrimaryConnectedOutputBeforeOthers() {
    List<XrandrOutput> outputs =
        XrandrParser.parse(HEADER + "VGA-1 disconnected (normal)\n" + DP_SECOND + HDMI_ACTIVE);

    assertThat(DisplayService.choose(outputs).orElseThrow().name()).isEqualTo("HDMI-1");
  }

  @Test
  void choosesFirstConnectedWhenThereIsNoPrimary() {
    List<XrandrOutput> outputs =
        XrandrParser.parse(HEADER + "VGA-1 disconnected (normal)\n" + DP_SECOND);

    assertThat(DisplayService.choose(outputs).orElseThrow().name()).isEqualTo("DP-1");
  }

  @Test
  void choosesNothingWhenNoMonitorIsConnected() {
    assertThat(DisplayService.choose(XrandrParser.parse(HEADER + "VGA-1 disconnected (normal)\n")))
        .isEmpty();
  }

  @Test
  void doesNotTouchOutputThatAlreadyUsesPreferredMode() {
    runner.xrandr = HEADER + HDMI_ACTIVE + DP_SECOND;

    service.start();

    assertThat(runner.commands).noneMatch(command -> command.contains("--auto"));
    assertThat(service.selectedGeometry()).contains(new DisplayGeometry(0, 0, 1920, 1080));
    assertThat(service.selectedResolution()).contains("1920x1080");
    assertThat(service.displays()).extracting(DisplayOutput::selected).containsExactly(true, false);
  }

  @Test
  void switchesInactiveSelectedOutputToItsPreferredMode() {
    runner.xrandr =
        HEADER
            + "HDMI-1 connected (normal left inverted right x axis y axis)\n"
            + "   1920x1080     60.00 +\n";
    runner.afterAuto =
        HEADER
            + "HDMI-1 connected primary 1920x1080+0+0 (normal left inverted right x axis y axis)"
            + " 0mm x 0mm\n"
            + "   1920x1080     60.00*+\n";

    service.start();

    assertThat(runner.commands).contains(List.of("xrandr", "--output", "HDMI-1", "--auto"));
    assertThat(service.selectedResolution()).contains("1920x1080");
  }

  @Test
  void publishesEventWhenMonitorIsUnplugged() {
    runner.xrandr = HEADER + HDMI_ACTIVE + DP_SECOND;
    service.start();

    runner.xrandr = HEADER + HDMI_ACTIVE + "DP-1 disconnected (normal)\n";
    service.poll();

    assertThat(events).hasSize(1);
    assertThat(events.get(0).selectionChanged()).isFalse();
  }

  @Test
  void publishesSelectionChangeWhenSelectedMonitorDisappears() {
    runner.xrandr = HEADER + HDMI_ACTIVE + DP_SECOND;
    service.start();

    runner.xrandr = HEADER + "HDMI-1 disconnected (normal)\n" + DP_SECOND;
    service.poll();

    assertThat(events).hasSize(1);
    assertThat(events.get(0).selectionChanged()).isTrue();
    assertThat(
            service.displays().stream()
                .filter(DisplayOutput::selected)
                .findFirst()
                .orElseThrow()
                .output())
        .isEqualTo("DP-1");
  }

  @Test
  void publishesNothingWhenNothingChanges() {
    runner.xrandr = HEADER + HDMI_ACTIVE;
    service.start();

    service.poll();
    service.poll();

    assertThat(events).isEmpty();
  }

  @Test
  void setsRequestedResolutionAndKeepsItOnNextPolls() {
    runner.xrandr = HEADER + HDMI_ACTIVE;
    service.start();
    runner.afterMode =
        HEADER
            + "HDMI-1 connected primary 1280x720+0+0 (normal left inverted right x axis y axis) 0mm"
            + " x 0mm\n"
            + "   1920x1080     60.00 +\n"
            + "   1280x720      60.00*\n";

    service.setResolution("HDMI-1", 1280, 720);
    runner.commands.clear();
    service.poll();

    assertThat(service.selectedResolution()).contains("1280x720");
    assertThat(runner.commands).noneMatch(command -> command.contains("--auto"));
    assertThat(events).hasSize(1);
  }

  @Test
  void acceptsCustomModeWhoseNameCarriesTheRefreshRate() {
    runner.xrandr = HEADER + HDMI_ACTIVE + "   1024x768_60.00  60.00\n";

    service.start();
    service.setResolution("HDMI-1", 1024, 768);

    assertThat(runner.commands)
        .contains(List.of("xrandr", "--output", "HDMI-1", "--mode", "1024x768_60.00"));
  }

  @Test
  void rejectsUnknownOutputUnavailableModeAndDisconnectedOutput() {
    runner.xrandr = HEADER + HDMI_ACTIVE + "VGA-1 disconnected (normal)\n";
    service.start();

    assertThatThrownBy(() -> service.setResolution("HDMI-9", 1920, 1080))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.setResolution("HDMI-1", 800, 600))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.setResolution("VGA-1", 1920, 1080))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void redetectPublishesEvenWhenNothingChanged() {
    runner.xrandr = HEADER + HDMI_ACTIVE;
    service.start();

    service.redetect();

    assertThat(events).hasSize(1);
  }

  @Test
  void reportsNoDisplaysWhenXrandrIsUnavailable() {
    runner.xrandr = null;

    service.start();

    assertThat(service.displays()).isEmpty();
    assertThat(service.selectedGeometry()).isEmpty();
    assertThat(service.selectedResolution()).isEmpty();
  }

  private static final class FakeRunner implements CommandRunner {

    final List<List<String>> commands = new ArrayList<>();
    String xrandr;
    String afterAuto;
    String afterMode;

    @Override
    public Optional<String> run(List<String> command) {
      commands.add(command);
      if (command.contains("--query")) {
        return Optional.ofNullable(xrandr);
      }
      if (command.contains("--auto") && afterAuto != null) {
        xrandr = afterAuto;
      }
      if (command.contains("--mode") && afterMode != null) {
        xrandr = afterMode;
      }
      return Optional.of("");
    }
  }
}
