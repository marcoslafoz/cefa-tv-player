package es.cefa.tv.player.telemetry;

import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Interpreta ficheros de {@code /proc} y {@code /sys}. */
final class ProcFiles {

  private static final Pattern MEM_TOTAL = Pattern.compile("(?m)^MemTotal:\\s+(\\d+) kB");
  private static final Pattern MEM_AVAILABLE = Pattern.compile("(?m)^MemAvailable:\\s+(\\d+) kB");
  private static final Pattern MODEL_NAME =
      Pattern.compile("(?m)^(?:model name|Model|Hardware)\\s*:\\s*(.+)$");
  private static final Pattern PRETTY_NAME = Pattern.compile("(?m)^PRETTY_NAME=\"?([^\"\\n]+)\"?$");

  private ProcFiles() {}

  /**
   * Memoria total de {@code /proc/meminfo}.
   *
   * @param meminfo contenido del fichero
   * @return kilobytes
   */
  static OptionalLong memTotalKb(String meminfo) {
    return firstLong(MEM_TOTAL, meminfo);
  }

  /**
   * Memoria disponible de {@code /proc/meminfo}.
   *
   * @param meminfo contenido del fichero
   * @return kilobytes
   */
  static OptionalLong memAvailableKb(String meminfo) {
    return firstLong(MEM_AVAILABLE, meminfo);
  }

  /**
   * Modelo de CPU de {@code /proc/cpuinfo}.
   *
   * @param cpuinfo contenido del fichero
   * @return modelo o {@code null} si no figura
   */
  static String cpuModel(String cpuinfo) {
    Matcher matcher = MODEL_NAME.matcher(cpuinfo);
    return matcher.find() ? matcher.group(1).trim() : null;
  }

  /**
   * Nombre legible de la distribución de {@code /etc/os-release}.
   *
   * @param osRelease contenido del fichero
   * @return nombre o {@code null} si no figura
   */
  static String prettyName(String osRelease) {
    Matcher matcher = PRETTY_NAME.matcher(osRelease);
    return matcher.find() ? matcher.group(1).trim() : null;
  }

  /**
   * Tiempo de CPU acumulado de la primera línea de {@code /proc/stat}.
   *
   * @param stat contenido del fichero
   * @return {@code [total, inactivo]} en ticks, vacío si no se puede leer
   */
  static long[] cpuTicks(String stat) {
    String line = stat.lines().filter(l -> l.startsWith("cpu ")).findFirst().orElse(null);
    if (line == null) {
      return new long[0];
    }
    String[] parts = line.trim().split("\\s+");
    long total = 0;
    long idle = 0;
    for (int i = 1; i < parts.length && i <= 8; i++) {
      long value = Long.parseLong(parts[i]);
      total += value;
      if (i == 4 || i == 5) {
        idle += value;
      }
    }
    return new long[] {total, idle};
  }

  /**
   * Primer valor de {@code /proc/loadavg}.
   *
   * @param loadavg contenido del fichero
   * @return carga del último minuto
   */
  static OptionalDouble load1(String loadavg) {
    String[] parts = loadavg.trim().split("\\s+");
    return parts.length > 0 && parts[0].matches("\\d+(\\.\\d+)?")
        ? OptionalDouble.of(Double.parseDouble(parts[0]))
        : OptionalDouble.empty();
  }

  /**
   * Segundos de actividad de {@code /proc/uptime}.
   *
   * @param uptime contenido del fichero
   * @return segundos desde el arranque
   */
  static OptionalLong uptimeSec(String uptime) {
    String[] parts = uptime.trim().split("\\s+");
    return parts.length > 0 && parts[0].matches("\\d+(\\.\\d+)?")
        ? OptionalLong.of((long) Double.parseDouble(parts[0]))
        : OptionalLong.empty();
  }

  /**
   * Temperatura de un sensor térmico.
   *
   * @param milliCelsius contenido de {@code temp}, en milésimas de grado
   * @return grados centígrados si el valor es plausible
   */
  static OptionalDouble temperature(String milliCelsius) {
    String trimmed = milliCelsius.trim();
    if (!trimmed.matches("-?\\d+")) {
      return OptionalDouble.empty();
    }
    double celsius = Long.parseLong(trimmed) / 1000.0;
    return celsius > -40 && celsius < 150 ? OptionalDouble.of(celsius) : OptionalDouble.empty();
  }

  private static OptionalLong firstLong(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    return matcher.find()
        ? OptionalLong.of(Long.parseLong(matcher.group(1)))
        : OptionalLong.empty();
  }
}
