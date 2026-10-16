package es.cefa.tv.player.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Interpreta la salida de {@code xrandr --query}. */
public final class XrandrParser {

  private static final Pattern OUTPUT =
      Pattern.compile(
          "^(\\S+) (connected|disconnected)(?: (primary))?"
              + "(?: (\\d+)x(\\d+)([+-]\\d+)([+-]\\d+))?.*$");
  private static final Pattern MODE = Pattern.compile("^\\s+(\\d+x\\d+\\S*)\\s+(.*)$");
  private static final Pattern RATE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([*+]{0,2})");

  private XrandrParser() {}

  /**
   * Lee la salida completa de xrandr.
   *
   * @param text texto devuelto por {@code xrandr --query}
   * @return salidas en el orden en que las lista xrandr
   */
  public static List<XrandrOutput> parse(String text) {
    List<XrandrOutput> outputs = new ArrayList<>();
    String name = null;
    boolean connected = false;
    boolean primary = false;
    Optional<DisplayGeometry> geometry = Optional.empty();
    List<XrandrMode> modes = new ArrayList<>();
    for (String line : text.split("\\R")) {
      Matcher header = OUTPUT.matcher(line);
      if (header.matches()) {
        if (name != null) {
          outputs.add(new XrandrOutput(name, connected, primary, geometry, modes));
        }
        name = header.group(1);
        connected = "connected".equals(header.group(2));
        primary = header.group(3) != null;
        geometry = geometry(header);
        modes = new ArrayList<>();
        continue;
      }
      Matcher mode = MODE.matcher(line);
      if (name != null && mode.matches()) {
        modes.addAll(rates(mode.group(1), mode.group(2)));
      }
    }
    if (name != null) {
      outputs.add(new XrandrOutput(name, connected, primary, geometry, modes));
    }
    return outputs;
  }

  private static Optional<DisplayGeometry> geometry(Matcher header) {
    if (header.group(4) == null) {
      return Optional.empty();
    }
    return Optional.of(
        new DisplayGeometry(
            Integer.parseInt(header.group(6)),
            Integer.parseInt(header.group(7)),
            Integer.parseInt(header.group(4)),
            Integer.parseInt(header.group(5))));
  }

  private static List<XrandrMode> rates(String modeName, String rest) {
    List<XrandrMode> modes = new ArrayList<>();
    Matcher rate = RATE.matcher(rest);
    while (rate.find()) {
      String flags = rate.group(2);
      modes.add(
          new XrandrMode(
              modeName,
              Double.parseDouble(rate.group(1)),
              flags.contains("*"),
              flags.contains("+")));
    }
    return modes;
  }
}
