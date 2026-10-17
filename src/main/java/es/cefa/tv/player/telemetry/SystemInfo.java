package es.cefa.tv.player.telemetry;

import java.util.List;

/**
 * Datos del equipo informados al servidor. Los campos que no se pueden calcular van a {@code null}.
 *
 * @param hostname nombre de la máquina
 * @param os sistema operativo
 * @param kernel versión del núcleo
 * @param arch arquitectura de CPU
 * @param cpuModel modelo de CPU
 * @param cpuCount número de CPU lógicas
 * @param cpuPercent uso de CPU en porcentaje
 * @param load1 carga media del último minuto
 * @param ramTotalMb memoria total en MB
 * @param ramUsedMb memoria en uso en MB
 * @param diskTotalMb capacidad del disco de datos en MB
 * @param diskFreeMb espacio libre del disco de datos en MB
 * @param tempC temperatura en grados centígrados
 * @param uptimeSec segundos desde el arranque del equipo
 * @param ips direcciones IPv4 del equipo
 * @param macs direcciones MAC de las interfaces activas
 * @param playerVersion versión del reproductor
 * @param playerBuild commit corto con el que se construyó la imagen
 * @param javaVersion versión de Java
 * @param chromiumVersion versión de Chromium
 * @param timezone zona horaria
 * @param displayMode modo de pantalla (host, xorg o headless)
 * @param shellEnabled si acepta sesiones de terminal remota
 * @param shellMode shell del contenedor ({@code container}) o del anfitrión ({@code host})
 * @param preferredOutput salida de vídeo preferida por el servidor, nulo si es automática
 * @param idleLogo logo de la pantalla sin programación, {@code CEFA} o {@code ALGONTEC}
 */
public record SystemInfo(
    String hostname,
    String os,
    String kernel,
    String arch,
    String cpuModel,
    int cpuCount,
    Double cpuPercent,
    Double load1,
    Long ramTotalMb,
    Long ramUsedMb,
    Long diskTotalMb,
    Long diskFreeMb,
    Double tempC,
    Long uptimeSec,
    List<String> ips,
    List<String> macs,
    String playerVersion,
    String playerBuild,
    String javaVersion,
    String chromiumVersion,
    String timezone,
    String displayMode,
    boolean shellEnabled,
    String shellMode,
    String preferredOutput,
    String idleLogo) {

  /**
   * Copia con la configuración recibida del servidor.
   *
   * @param output salida preferida o nulo
   * @param logo logo de la pantalla sin programación
   * @return nuevo registro con los campos actualizados
   */
  public SystemInfo withConfig(String output, String logo) {
    return new SystemInfo(
        hostname,
        os,
        kernel,
        arch,
        cpuModel,
        cpuCount,
        cpuPercent,
        load1,
        ramTotalMb,
        ramUsedMb,
        diskTotalMb,
        diskFreeMb,
        tempC,
        uptimeSec,
        ips,
        macs,
        playerVersion,
        playerBuild,
        javaVersion,
        chromiumVersion,
        timezone,
        displayMode,
        shellEnabled,
        shellMode,
        output,
        logo);
  }
}
