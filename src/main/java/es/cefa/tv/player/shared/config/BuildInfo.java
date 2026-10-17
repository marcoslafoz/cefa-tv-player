package es.cefa.tv.player.shared.config;

/** Normalización de los datos de versión que se informan al servidor. */
public final class BuildInfo {

  /** Valor del build cuando la imagen se construyó sin acceso a git. */
  public static final String UNKNOWN = "unknown";

  private static final String SNAPSHOT = "-SNAPSHOT";

  private BuildInfo() {}

  /**
   * Devuelve la versión del reproductor sin el sufijo {@code -SNAPSHOT}.
   *
   * @param raw versión leída del {@code pom.xml}
   * @return versión limpia, o {@code dev} si no hay ninguna
   */
  public static String version(String raw) {
    if (raw == null || raw.isBlank()) {
      return "dev";
    }
    String trimmed = raw.trim();
    return trimmed.endsWith(SNAPSHOT)
        ? trimmed.substring(0, trimmed.length() - SNAPSHOT.length())
        : trimmed;
  }

  /**
   * Devuelve el commit corto con el que se construyó la imagen.
   *
   * @param raw valor de {@code PLAYER_BUILD}
   * @return commit corto, o {@link #UNKNOWN} si está vacío
   */
  public static String build(String raw) {
    return raw == null || raw.isBlank() ? UNKNOWN : raw.trim();
  }
}
