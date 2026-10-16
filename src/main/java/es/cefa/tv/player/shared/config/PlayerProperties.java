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
 */
@ConfigurationProperties(prefix = "cefa-tv")
public record PlayerProperties(
    Path dataDir,
    String enrollmentKey,
    String server,
    String playerName,
    int discoveryPort,
    int heartbeatSec,
    Chromium chromium) {

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
