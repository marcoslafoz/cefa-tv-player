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
 * @param javaVersion versión de Java
 * @param chromiumVersion versión de Chromium
 * @param timezone zona horaria
 * @param displayMode modo de pantalla (host, xorg o headless)
 * @param shellEnabled si acepta sesiones de terminal remota
 * @param shellMode shell del contenedor ({@code container}) o del anfitrión ({@code host})
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
    String javaVersion,
    String chromiumVersion,
    String timezone,
    String displayMode,
    boolean shellEnabled,
    String shellMode) {}
