package es.cefa.tv.player.remote;

import java.util.Locale;
import java.util.Set;

/**
 * Consulta el cursor que mostraría el navegador bajo un punto de la página: la expresión JavaScript
 * que se evalúa en Chromium y la normalización del resultado a un cursor CSS seguro.
 */
public final class CursorProbe {

  private static final String DEFAULT_CURSOR = "default";
  private static final Set<String> ALLOWED =
      Set.of(
          "default",
          "pointer",
          "text",
          "vertical-text",
          "crosshair",
          "move",
          "grab",
          "grabbing",
          "wait",
          "progress",
          "help",
          "not-allowed",
          "no-drop",
          "copy",
          "alias",
          "cell",
          "context-menu",
          "zoom-in",
          "zoom-out",
          "all-scroll",
          "col-resize",
          "row-resize",
          "n-resize",
          "e-resize",
          "s-resize",
          "w-resize",
          "ne-resize",
          "nw-resize",
          "se-resize",
          "sw-resize",
          "ew-resize",
          "ns-resize",
          "nesw-resize",
          "nwse-resize");

  private CursorProbe() {}

  /**
   * Construye la expresión que devuelve el estilo de cursor del elemento bajo el punto.
   *
   * @param x coordenada horizontal en píxeles CSS
   * @param y coordenada vertical en píxeles CSS
   * @return expresión JavaScript lista para {@code Runtime.evaluate}
   */
  public static String expression(double x, double y) {
    return "(function(x,y){var e=document.elementFromPoint(x,y);if(!e){return 'default'}"
        + "var s=getComputedStyle(e);var c=s.cursor;"
        + "if(c==='auto'){var n=e.tagName;"
        + "if(n==='INPUT'||n==='TEXTAREA'||e.isContentEditable){return 'text'}"
        + "var t=Array.prototype.some.call(e.childNodes,function(k){"
        + "return k.nodeType===3&&k.textContent.trim().length>0});"
        + "return t?'text':'default'}return c})("
        + x
        + ","
        + y
        + ")";
  }

  /**
   * Convierte el cursor devuelto por el navegador en un valor que el visor puede aplicar.
   *
   * @param raw valor de {@code cursor} calculado en la página, o nulo
   * @return un cursor de la lista permitida; {@code default} si es desconocido, oculto o una imagen
   */
  public static String normalize(String raw) {
    if (raw == null) {
      return DEFAULT_CURSOR;
    }
    String first = raw.trim().toLowerCase(Locale.ROOT);
    int comma = first.lastIndexOf(',');
    if (comma >= 0) {
      first = first.substring(comma + 1).trim();
    }
    return ALLOWED.contains(first) ? first : DEFAULT_CURSOR;
  }
}
