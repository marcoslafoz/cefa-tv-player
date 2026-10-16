package es.cefa.tv.player.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Salida de vídeo detectada con xrandr.
 *
 * @param name nombre de la salida, por ejemplo {@code HDMI-1}
 * @param connected si hay un monitor conectado
 * @param primary si es la salida primaria
 * @param geometry posición y tamaño actuales, vacío si la salida está apagada
 * @param modes modos disponibles en el orden de xrandr
 */
public record XrandrOutput(
    String name,
    boolean connected,
    boolean primary,
    Optional<DisplayGeometry> geometry,
    List<XrandrMode> modes) {

  /**
   * Modo activo.
   *
   * @return modo marcado como actual, si lo hay
   */
  public Optional<XrandrMode> currentMode() {
    return modes.stream().filter(XrandrMode::current).findFirst();
  }

  /**
   * Modo preferido del monitor.
   *
   * @return modo marcado como preferido, si lo hay
   */
  public Optional<XrandrMode> preferredMode() {
    return modes.stream().filter(XrandrMode::preferred).findFirst();
  }

  /**
   * Nombres de modo sin repetir y en el orden de xrandr.
   *
   * @return lista de nombres
   */
  public List<String> modeNames() {
    List<String> names = new ArrayList<>();
    for (XrandrMode mode : modes) {
      if (!names.contains(mode.name())) {
        names.add(mode.name());
      }
    }
    return names;
  }

  /**
   * Convierte la salida al formato que se informa al servidor.
   *
   * @param selected si es la salida elegida para reproducir
   * @return descripción de la pantalla
   */
  public DisplayOutput toDisplay(boolean selected) {
    Optional<XrandrMode> reference = currentMode().or(this::preferredMode);
    Integer width =
        geometry
            .map(DisplayGeometry::width)
            .or(() -> reference.map(XrandrMode::width))
            .orElse(null);
    Integer height =
        geometry
            .map(DisplayGeometry::height)
            .or(() -> reference.map(XrandrMode::height))
            .orElse(null);
    return new DisplayOutput(
        name,
        connected,
        primary,
        selected,
        width,
        height,
        reference.map(XrandrMode::refreshHz).filter(rate -> rate > 0).orElse(null),
        geometry.map(DisplayGeometry::x).orElse(null),
        geometry.map(DisplayGeometry::y).orElse(null),
        modeNames());
  }
}
