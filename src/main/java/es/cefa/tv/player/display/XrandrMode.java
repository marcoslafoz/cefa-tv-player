package es.cefa.tv.player.display;

/**
 * Modo de vídeo de una salida tal y como lo informa xrandr.
 *
 * @param name nombre del modo, por ejemplo {@code 1920x1080}
 * @param refreshHz frecuencia de refresco en hercios
 * @param current si es el modo activo ({@code *})
 * @param preferred si es el modo preferido del monitor ({@code +})
 */
public record XrandrMode(String name, double refreshHz, boolean current, boolean preferred) {

  /**
   * Anchura del modo.
   *
   * @return píxeles de ancho o 0 si el nombre no tiene el formato {@code AxB}
   */
  public int width() {
    return dimension(0);
  }

  /**
   * Altura del modo.
   *
   * @return píxeles de alto o 0 si el nombre no tiene el formato {@code AxB}
   */
  public int height() {
    return dimension(1);
  }

  private int dimension(int index) {
    String[] parts = name.split("x", 2);
    if (parts.length < 2) {
      return 0;
    }
    String digits = parts[index].replaceAll("^(\\d+).*$", "$1");
    return digits.matches("\\d+") ? Integer.parseInt(digits) : 0;
  }
}
