package es.cefa.tv.player.shared.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del reproductor.
 *
 * @param dataDir directorio persistente (estado, manifest y ficheros)
 * @param enrollmentKey clave de alta en el servidor
 * @param server URL base del core indicada manualmente; vacía para usar el descubrimiento
 * @param playerName nombre solicitado para el reproductor; vacío para usar el hostname
 * @param discoveryPort puerto UDP del sidecar de descubrimiento
 * @param heartbeatSec segundos entre latidos HTTP
 * @param chromium configuración del navegador
 * @param displayMode modo de pantalla del contenedor (host, xorg o headless)
 * @param shell configuración de la terminal remota
 */
@ConfigurationProperties(prefix = "cefa-tv")
public record PlayerProperties(
    Path dataDir,
    String enrollmentKey,
    String server,
    String playerName,
    int discoveryPort,
    int heartbeatSec,
    Chromium chromium,
    String displayMode,
    Shell shell) {

  /**
   * Configuración de Chromium.
   *
   * @param enabled si se lanza y gobierna el navegador
   * @param binary ejecutable de Chromium
   * @param debugPort puerto de depuración remota (CDP)
   * @param noSandbox desactiva el sandbox (necesario al ejecutarse como root en contenedor)
   */
  public record Chromium(boolean enabled, String binary, int debugPort, boolean noSandbox) {}

  /**
   * Configuración de la terminal remota.
   *
   * @param enabled si se aceptan sesiones de terminal del servidor
   * @param mode {@code container} para la shell del contenedor o {@code host} para la del anfitrión
   */
  public record Shell(boolean enabled, String mode) {

    /**
     * Indica si la shell debe ejecutarse en el anfitrión.
     *
     * @return {@code true} cuando el modo es {@code host}
     */
    public boolean hostMode() {
      return "host".equalsIgnoreCase(mode);
    }
  }

  /**
   * Indica si hay una clave de alta configurada.
   *
   * @return {@code true} si la clave no está vacía
   */
  public boolean hasEnrollmentKey() {
    return enrollmentKey != null && !enrollmentKey.isBlank();
  }

  /**
   * Indica si se ha fijado manualmente la URL del servidor.
   *
   * @return {@code true} si la URL no está vacía
   */
  public boolean hasServer() {
    return server != null && !server.isBlank();
  }
}
