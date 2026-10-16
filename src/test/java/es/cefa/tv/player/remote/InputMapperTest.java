package es.cefa.tv.player.remote;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class InputMapperTest {

  private final ObjectMapper json = new ObjectMapper();
  private final InputMapper mapper = new InputMapper(json);

  private List<CdpCommand> map(String input) throws Exception {
    JsonNode node = json.readTree(input);
    return mapper.map(node, 1920, 1080);
  }

  @Test
  void scalesNormalizedCoordinatesToTheViewport() throws Exception {
    CdpCommand command = map("{\"kind\":\"MOUSE_MOVE\",\"x\":0.5,\"y\":0.25}").get(0);
    assertThat(command.method()).isEqualTo("Input.dispatchMouseEvent");
    assertThat(command.params().get("type").asText()).isEqualTo("mouseMoved");
    assertThat(command.params().get("x").asDouble()).isEqualTo(960.0);
    assertThat(command.params().get("y").asDouble()).isEqualTo(270.0);
  }

  @Test
  void tracksPressedButtonsAcrossEvents() throws Exception {
    CdpCommand down = map("{\"kind\":\"MOUSE_DOWN\",\"x\":0,\"y\":0,\"button\":\"left\"}").get(0);
    assertThat(down.params().get("type").asText()).isEqualTo("mousePressed");
    assertThat(down.params().get("buttons").asInt()).isEqualTo(1);
    assertThat(down.params().get("clickCount").asInt()).isEqualTo(1);
    CdpCommand drag = map("{\"kind\":\"MOUSE_MOVE\",\"x\":0.1,\"y\":0.1}").get(0);
    assertThat(drag.params().get("buttons").asInt()).isEqualTo(1);
    CdpCommand up = map("{\"kind\":\"MOUSE_UP\",\"x\":0.1,\"y\":0.1,\"button\":\"left\"}").get(0);
    assertThat(up.params().get("type").asText()).isEqualTo("mouseReleased");
    assertThat(up.params().get("buttons").asInt()).isZero();
  }

  @Test
  void mapsWheelWithDeltas() throws Exception {
    CdpCommand wheel =
        map("{\"kind\":\"MOUSE_WHEEL\",\"x\":0.5,\"y\":0.5,\"deltaX\":0,\"deltaY\":120}").get(0);
    assertThat(wheel.params().get("type").asText()).isEqualTo("mouseWheel");
    assertThat(wheel.params().get("deltaY").asDouble()).isEqualTo(120.0);
  }

  @Test
  void printableKeysCarryTextAndSpecialKeysAreRaw() throws Exception {
    CdpCommand letter = map("{\"kind\":\"KEY_DOWN\",\"key\":\"a\",\"code\":\"KeyA\"}").get(0);
    assertThat(letter.params().get("type").asText()).isEqualTo("keyDown");
    assertThat(letter.params().get("text").asText()).isEqualTo("a");
    assertThat(letter.params().get("windowsVirtualKeyCode").asInt()).isEqualTo(65);

    CdpCommand arrow =
        map("{\"kind\":\"KEY_DOWN\",\"key\":\"ArrowLeft\",\"code\":\"ArrowLeft\"}").get(0);
    assertThat(arrow.params().get("type").asText()).isEqualTo("rawKeyDown");
    assertThat(arrow.params().get("windowsVirtualKeyCode").asInt()).isEqualTo(37);

    CdpCommand enter = map("{\"kind\":\"KEY_DOWN\",\"key\":\"Enter\",\"code\":\"Enter\"}").get(0);
    assertThat(enter.params().get("text").asText()).isEqualTo("\r");

    CdpCommand release = map("{\"kind\":\"KEY_UP\",\"key\":\"a\",\"code\":\"KeyA\"}").get(0);
    assertThat(release.params().get("type").asText()).isEqualTo("keyUp");
  }

  @Test
  void ctrlShortcutsDoNotProduceText() throws Exception {
    CdpCommand command =
        map("{\"kind\":\"KEY_DOWN\",\"key\":\"a\",\"code\":\"KeyA\",\"modifiers\":2}").get(0);
    assertThat(command.params().get("type").asText()).isEqualTo("rawKeyDown");
    assertThat(command.params().has("text")).isFalse();
    assertThat(command.params().get("modifiers").asInt()).isEqualTo(2);
  }

  @Test
  void textInsertsAndUnknownKindsAreIgnored() throws Exception {
    CdpCommand text = map("{\"kind\":\"TEXT\",\"text\":\"hola\"}").get(0);
    assertThat(text.method()).isEqualTo("Input.insertText");
    assertThat(text.params().get("text").asText()).isEqualTo("hola");
    assertThat(map("{\"kind\":\"DANCE\"}")).isEmpty();
  }
}
