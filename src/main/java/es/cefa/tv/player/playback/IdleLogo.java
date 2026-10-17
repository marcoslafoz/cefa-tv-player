package es.cefa.tv.player.playback;

import java.util.Locale;

/** Logo que se muestra en la pantalla sin programación. */
public enum IdleLogo {

  /** Logo vertical de CEFA, el valor por defecto. */
  CEFA,

  /** Logo horizontal de Algontec. */
  ALGONTEC;

  /**
   * Interpreta el valor recibido del servidor.
   *
   * @param raw nombre del logo en cualquier combinación de mayúsculas
   * @return el logo pedido, o {@link #CEFA} si el valor es nulo, vacío o desconocido
   */
  public static IdleLogo parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return CEFA;
    }
    try {
      return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return CEFA;
    }
  }
}
