package es.cefa.tv.player.pairing;

import com.sun.management.OperatingSystemMXBean;
import es.cefa.tv.player.display.DisplayService;
import es.cefa.tv.player.shared.config.BuildInfo;
import es.cefa.tv.player.shared.config.PlayerProperties;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Reúne los datos reales del equipo para pedir el emparejamiento. */
@Component
public class SystemPairingRequestFactory implements PairingRequestFactory {

  private final PlayerProperties properties;
  private final DisplayService display;
  private final String version;
  private final String build;

  /**
   * Crea la fábrica.
   *
   * @param properties configuración del reproductor
   * @param display pantallas detectadas
   * @param version versión del reproductor
   * @param build commit corto con el que se construyó la imagen
   */
  public SystemPairingRequestFactory(
      PlayerProperties properties,
      DisplayService display,
      @Value("${info.app.version:dev}") String version,
      @Value("${info.app.build:unknown}") String build) {
    this.properties = properties;
    this.display = display;
    this.version = BuildInfo.version(version);
    this.build = BuildInfo.build(build);
  }

  @Override
  public PairingRequest create() {
    String hostname =
        properties.playerName() != null && !properties.playerName().isBlank()
            ? properties.playerName()
            : localHostname();
    Map<String, Object> hardware = new LinkedHashMap<>();
    hardware.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
    hardware.put("arch", System.getProperty("os.arch"));
    hardware.put("cpus", Runtime.getRuntime().availableProcessors());
    if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os) {
      hardware.put("ramMb", os.getTotalMemorySize() / (1024 * 1024));
    }
    return new PairingRequest(
        hostname, hardware, resolution(), version, build, ZoneId.systemDefault().getId());
  }

  private String resolution() {
    String detected = display.selectedResolution().orElse(null);
    if (detected != null) {
      return detected;
    }
    String value = System.getenv("CEFA_TV_RESOLUTION");
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    String[] parts = value.split("x");
    return parts.length >= 2 ? parts[0] + "x" + parts[1] : value;
  }

  private static String localHostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return "cefa-tv-player";
    }
  }
}
