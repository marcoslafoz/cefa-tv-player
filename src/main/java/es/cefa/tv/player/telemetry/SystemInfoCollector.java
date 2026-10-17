package es.cefa.tv.player.telemetry;

import com.sun.management.OperatingSystemMXBean;
import es.cefa.tv.player.shared.config.BuildInfo;
import es.cefa.tv.player.shared.config.PlayerProperties;
import es.cefa.tv.player.shared.process.CommandRunner;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Recoge los datos del equipo leyendo {@code /proc}, {@code /sys} y la JVM, sin dependencias. */
@Slf4j
@Component
public class SystemInfoCollector {

  private static final Path PROC = Path.of("/proc");
  private static final Path THERMAL = Path.of("/sys/class/thermal");
  private static final Path HWMON = Path.of("/sys/class/hwmon");
  private static final Pattern VERSION = Pattern.compile("(\\d+(?:\\.\\d+)+)");
  private static final long MIN_CPU_INTERVAL_MS = 1000;
  private static final long MB = 1024 * 1024;

  private final PlayerProperties properties;
  private final CommandRunner runner;
  private final String playerVersion;
  private final String playerBuild;
  private long[] lastTicks = new long[0];
  private long lastTicksAt;
  private Double lastCpuPercent;
  private volatile Optional<String> chromiumVersion;

  /**
   * Crea el recolector.
   *
   * @param properties configuración del reproductor
   * @param runner ejecutor de programas del sistema
   * @param playerVersion versión del reproductor
   * @param playerBuild commit corto con el que se construyó la imagen
   */
  public SystemInfoCollector(
      PlayerProperties properties,
      CommandRunner runner,
      @Value("${info.app.version:dev}") String playerVersion,
      @Value("${info.app.build:unknown}") String playerBuild) {
    this.properties = properties;
    this.runner = runner;
    this.playerVersion = BuildInfo.version(playerVersion);
    this.playerBuild = BuildInfo.build(playerBuild);
  }

  /**
   * Toma una muestra del equipo.
   *
   * @return datos actuales, con {@code null} en lo que no se pueda calcular
   */
  public synchronized SystemInfo collect() {
    String meminfo = read(PROC.resolve("meminfo"));
    Long ramTotal = meminfo == null ? totalMemoryFromJvm() : toMb(ProcFiles.memTotalKb(meminfo));
    Long ramUsed = ramUsed(meminfo, ramTotal);
    String loadavg = read(PROC.resolve("loadavg"));
    Double load1 = loadavg == null ? jvmLoad() : orNull(ProcFiles.load1(loadavg));
    String uptime = read(PROC.resolve("uptime"));
    return new SystemInfo(
        hostname(),
        osName(),
        System.getProperty("os.version"),
        System.getProperty("os.arch"),
        cpuModel(),
        Runtime.getRuntime().availableProcessors(),
        cpuPercent(),
        load1,
        ramTotal,
        ramUsed,
        diskTotalMb(),
        diskFreeMb(),
        temperature(),
        uptime == null
            ? null
            : ProcFiles.uptimeSec(uptime).stream().boxed().findFirst().orElse(null),
        ips(),
        macs(),
        playerVersion,
        playerBuild,
        System.getProperty("java.version"),
        chromiumVersion(),
        ZoneId.systemDefault().getId(),
        properties.displayMode(),
        properties.shell().enabled(),
        properties.shell().hostMode() ? "host" : "container",
        null,
        null);
  }

  private Long ramUsed(String meminfo, Long ramTotal) {
    if (meminfo != null && ramTotal != null) {
      Long available = toMb(ProcFiles.memAvailableKb(meminfo));
      if (available != null) {
        return ramTotal - available;
      }
    }
    if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os) {
      return (os.getTotalMemorySize() - os.getFreeMemorySize()) / MB;
    }
    return null;
  }

  private static Long totalMemoryFromJvm() {
    return ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os
        ? os.getTotalMemorySize() / MB
        : null;
  }

  private static Double jvmLoad() {
    double load = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
    return load < 0 ? null : load;
  }

  private Double cpuPercent() {
    long now = System.currentTimeMillis();
    String stat = read(PROC.resolve("stat"));
    if (stat == null) {
      return ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os
              && os.getCpuLoad() >= 0
          ? Math.round(os.getCpuLoad() * 1000) / 10.0
          : null;
    }
    long[] ticks = ProcFiles.cpuTicks(stat);
    if (ticks.length == 0) {
      return null;
    }
    if (lastTicks.length == 0) {
      lastTicks = ticks;
      lastTicksAt = now;
      return null;
    }
    if (now - lastTicksAt < MIN_CPU_INTERVAL_MS) {
      return lastCpuPercent;
    }
    long total = ticks[0] - lastTicks[0];
    long idle = ticks[1] - lastTicks[1];
    lastTicks = ticks;
    lastTicksAt = now;
    lastCpuPercent =
        total <= 0 ? lastCpuPercent : Math.round((total - idle) * 1000.0 / total) / 10.0;
    return lastCpuPercent;
  }

  private String cpuModel() {
    String cpuinfo = read(PROC.resolve("cpuinfo"));
    return cpuinfo == null ? null : ProcFiles.cpuModel(cpuinfo);
  }

  private String osName() {
    String release = read(Path.of("/etc/os-release"));
    String pretty = release == null ? null : ProcFiles.prettyName(release);
    return pretty != null ? pretty : System.getProperty("os.name");
  }

  private Long diskTotalMb() {
    try {
      return Files.getFileStore(dataDir()).getTotalSpace() / MB;
    } catch (IOException e) {
      return null;
    }
  }

  private Long diskFreeMb() {
    try {
      return Files.getFileStore(dataDir()).getUsableSpace() / MB;
    } catch (IOException e) {
      return null;
    }
  }

  private Path dataDir() throws IOException {
    Path dir = properties.dataDir().toAbsolutePath();
    Files.createDirectories(dir);
    return dir;
  }

  private Double temperature() {
    List<Path> candidates = new ArrayList<>();
    candidates.addAll(sensorFiles(THERMAL, "thermal_zone", "temp"));
    candidates.addAll(sensorFiles(HWMON, "hwmon", "temp1_input"));
    OptionalDouble max =
        candidates.stream()
            .map(this::read)
            .filter(value -> value != null)
            .map(ProcFiles::temperature)
            .filter(OptionalDouble::isPresent)
            .mapToDouble(OptionalDouble::getAsDouble)
            .max();
    return max.isPresent() ? Math.round(max.getAsDouble() * 10) / 10.0 : null;
  }

  private static List<Path> sensorFiles(Path base, String prefix, String file) {
    if (!Files.isDirectory(base)) {
      return List.of();
    }
    try (Stream<Path> entries = Files.list(base)) {
      return entries
          .filter(entry -> entry.getFileName().toString().startsWith(prefix))
          .map(entry -> entry.resolve(file))
          .filter(Files::isReadable)
          .toList();
    } catch (IOException e) {
      return List.of();
    }
  }

  private static String hostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return null;
    }
  }

  private static List<String> ips() {
    List<String> ips = new ArrayList<>();
    for (NetworkInterface nic : activeInterfaces()) {
      nic.inetAddresses()
          .filter(address -> address instanceof Inet4Address && !address.isLoopbackAddress())
          .forEach(address -> ips.add(address.getHostAddress()));
    }
    return ips;
  }

  private static List<String> macs() {
    List<String> macs = new ArrayList<>();
    for (NetworkInterface nic : activeInterfaces()) {
      try {
        byte[] hardware = nic.getHardwareAddress();
        if (hardware != null && hardware.length == 6) {
          StringBuilder mac = new StringBuilder();
          for (byte part : hardware) {
            mac.append(mac.isEmpty() ? "" : ":").append(String.format("%02x", part));
          }
          macs.add(mac.toString());
        }
      } catch (SocketException e) {
        log.debug("MAC no disponible en {}: {}", nic.getName(), e.getMessage());
      }
    }
    return macs;
  }

  private static List<NetworkInterface> activeInterfaces() {
    try {
      return NetworkInterface.networkInterfaces()
          .filter(
              nic -> {
                try {
                  return nic.isUp() && !nic.isLoopback() && !nic.isVirtual();
                } catch (SocketException e) {
                  return false;
                }
              })
          .toList();
    } catch (SocketException e) {
      return List.of();
    }
  }

  private String chromiumVersion() {
    if (!properties.chromium().enabled()) {
      return null;
    }
    if (chromiumVersion == null) {
      chromiumVersion =
          runner
              .run(List.of(properties.chromium().binary(), "--version"))
              .map(VERSION::matcher)
              .filter(Matcher::find)
              .map(matcher -> matcher.group(1));
    }
    return chromiumVersion.orElse(null);
  }

  private String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      return null;
    }
  }

  private static Long toMb(java.util.OptionalLong kilobytes) {
    return kilobytes.isPresent() ? kilobytes.getAsLong() / 1024 : null;
  }

  private static Double orNull(OptionalDouble value) {
    return value.isPresent() ? value.getAsDouble() : null;
  }
}
