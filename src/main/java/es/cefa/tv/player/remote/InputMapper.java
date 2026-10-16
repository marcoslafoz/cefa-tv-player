package es.cefa.tv.player.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;

/**
 * Traduce los mensajes {@code INPUT} del contrato (coordenadas normalizadas 0..1) a comandos {@code
 * Input.*} de DevTools. Mantiene los botones pulsados para informar de {@code buttons}.
 */
public class InputMapper {

  private static final int BUTTON_LEFT = 1;
  private static final int BUTTON_RIGHT = 2;
  private static final int BUTTON_MIDDLE = 4;
  private static final int MODIFIERS_NO_TEXT = 1 | 2 | 4;
  private static final Map<String, Integer> VIRTUAL_KEYS =
      Map.ofEntries(
          Map.entry("Backspace", 8),
          Map.entry("Tab", 9),
          Map.entry("Enter", 13),
          Map.entry("Shift", 16),
          Map.entry("Control", 17),
          Map.entry("Alt", 18),
          Map.entry("Escape", 27),
          Map.entry(" ", 32),
          Map.entry("PageUp", 33),
          Map.entry("PageDown", 34),
          Map.entry("End", 35),
          Map.entry("Home", 36),
          Map.entry("ArrowLeft", 37),
          Map.entry("ArrowUp", 38),
          Map.entry("ArrowRight", 39),
          Map.entry("ArrowDown", 40),
          Map.entry("Insert", 45),
          Map.entry("Delete", 46));

  private final ObjectMapper mapper;
  private int pressedButtons;

  /**
   * Crea el traductor.
   *
   * @param mapper fábrica de nodos JSON
   */
  public InputMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * Convierte un mensaje de entrada en comandos de DevTools.
   *
   * @param input mensaje {@code INPUT}
   * @param viewportWidth ancho del viewport en píxeles CSS
   * @param viewportHeight alto del viewport en píxeles CSS
   * @return comandos a ejecutar en orden; vacío si el mensaje no es reconocible
   */
  public List<CdpCommand> map(JsonNode input, double viewportWidth, double viewportHeight) {
    String kind = input.path("kind").asText("");
    int modifiers = input.path("modifiers").asInt(0);
    return switch (kind) {
      case "MOUSE_MOVE" ->
          List.of(mouse(input, "mouseMoved", "none", modifiers, viewportWidth, viewportHeight, 0));
      case "MOUSE_DOWN" -> {
        String button = input.path("button").asText("left");
        pressedButtons |= mask(button);
        yield List.of(
            mouse(input, "mousePressed", button, modifiers, viewportWidth, viewportHeight, 1));
      }
      case "MOUSE_UP" -> {
        String button = input.path("button").asText("left");
        pressedButtons &= ~mask(button);
        yield List.of(
            mouse(input, "mouseReleased", button, modifiers, viewportWidth, viewportHeight, 1));
      }
      case "MOUSE_WHEEL" -> {
        CdpCommand wheel =
            mouse(input, "mouseWheel", "none", modifiers, viewportWidth, viewportHeight, 0);
        wheel.params().put("deltaX", input.path("deltaX").asDouble(0));
        wheel.params().put("deltaY", input.path("deltaY").asDouble(0));
        yield List.of(wheel);
      }
      case "KEY_DOWN" -> List.of(key(input, true, modifiers));
      case "KEY_UP" -> List.of(key(input, false, modifiers));
      case "TEXT" -> {
        String text = input.path("text").asText("");
        yield text.isEmpty()
            ? List.<CdpCommand>of()
            : List.of(
                new CdpCommand("Input.insertText", mapper.createObjectNode().put("text", text)));
      }
      default -> List.of();
    };
  }

  private CdpCommand mouse(
      JsonNode input,
      String type,
      String button,
      int modifiers,
      double width,
      double height,
      int clickCount) {
    ObjectNode params = mapper.createObjectNode();
    params.put("type", type);
    params.put("x", clamp(input.path("x").asDouble(0)) * width);
    params.put("y", clamp(input.path("y").asDouble(0)) * height);
    params.put("button", button);
    params.put("buttons", pressedButtons);
    params.put("clickCount", clickCount);
    params.put("modifiers", modifiers);
    return new CdpCommand("Input.dispatchMouseEvent", params);
  }

  private CdpCommand key(JsonNode input, boolean down, int modifiers) {
    String key = input.path("key").asText("");
    ObjectNode params = mapper.createObjectNode();
    params.put("key", key);
    params.put("code", input.path("code").asText(""));
    params.put("modifiers", modifiers);
    int virtualCode = virtualKeyCode(key);
    if (virtualCode > 0) {
      params.put("windowsVirtualKeyCode", virtualCode);
    }
    boolean printable =
        key.codePointCount(0, key.length()) == 1 && (modifiers & MODIFIERS_NO_TEXT) == 0;
    boolean enter = "Enter".equals(key);
    if (!down) {
      params.put("type", "keyUp");
    } else if (printable || enter) {
      params.put("type", "keyDown");
      params.put("text", enter ? "\r" : key);
    } else {
      params.put("type", "rawKeyDown");
    }
    return new CdpCommand("Input.dispatchKeyEvent", params);
  }

  private static int virtualKeyCode(String key) {
    Integer known = VIRTUAL_KEYS.get(key);
    if (known != null) {
      return known;
    }
    if (key.length() == 1) {
      return Character.toUpperCase(key.charAt(0));
    }
    return 0;
  }

  private static int mask(String button) {
    return switch (button) {
      case "right" -> BUTTON_RIGHT;
      case "middle" -> BUTTON_MIDDLE;
      default -> BUTTON_LEFT;
    };
  }

  private static double clamp(double value) {
    return Math.max(0, Math.min(1, value));
  }
}
