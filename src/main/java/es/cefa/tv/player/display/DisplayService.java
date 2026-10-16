package es.cefa.tv.player.display;

import es.cefa.tv.player.shared.process.CommandRunner;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Detecta los monitores con xrandr, elige la salida donde se reproduce (la primaria si existe y, si
 * no, la primera conectada), adapta su resolución y avisa cuando algo cambia.
 */
@Slf4j
@Service
public class DisplayService {

  private final CommandRunner runner;
  private final ApplicationEventPublisher events;
  private final Map<String, String> manualModes = new HashMap<>();
  private volatile List<XrandrOutput> outputs = List.of();
  private volatile Optional<XrandrOutput> selected = Optional.empty();
  private String lastSignature = "";
  private String lastSelection = "";
  private String failedApply = "";

  /**
   * Crea el servicio.
   *
   * @param runner ejecutor de programas del sistema
   * @param events publicador de eventos de Spring
   */
  public DisplayService(CommandRunner runner, ApplicationEventPublisher events) {
    this.runner = runner;
    this.events = events;
  }

  /** Detecta las pantallas y adapta la resolución antes de que arranque el navegador. */
  @PostConstruct
  public synchronized void start() {
    refresh(false, false);
  }

  /** Comprueba cada 10 segundos si se ha enchufado, desenchufado o cambiado un monitor. */
  @Scheduled(fixedDelay = 10000, initialDelay = 10000)
  public synchronized void poll() {
    refresh(true, false);
  }

  /** Vuelve a detectar las pantallas y a adaptar la resolución por orden del servidor. */
  public synchronized void redetect() {
    manualModes.clear();
    failedApply = "";
    refresh(true, true);
  }

  /**
   * Fija el modo de una salida.
   *
   * @param output nombre de la salida
   * @param width ancho pedido
   * @param height alto pedido
   * @throws IllegalArgumentException si la salida no existe, no está conectada o no ofrece el modo
   */
  public synchronized void setResolution(String output, int width, int height) {
    String mode = width + "x" + height;
    List<XrandrOutput> current = query();
    XrandrOutput target =
        current.stream()
            .filter(candidate -> candidate.name().equals(output))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Salida desconocida: " + output));
    if (!target.connected()) {
      throw new IllegalArgumentException("La salida " + output + " no tiene monitor conectado");
    }
    String modeName =
        target.modeNames().stream()
            .filter(name -> name.equals(mode) || name.startsWith(mode + "_"))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "La salida " + output + " no ofrece el modo " + mode));
    if (runner.run(List.of("xrandr", "--output", output, "--mode", modeName)).isEmpty()) {
      throw new IllegalArgumentException("xrandr no pudo aplicar " + modeName + " en " + output);
    }
    manualModes.put(output, modeName);
    refresh(true, true);
  }

  /**
   * Pantallas detectadas, con la elegida marcada.
   *
   * @return descripción de todas las salidas
   */
  public List<DisplayOutput> displays() {
    String selectedName = selected.map(XrandrOutput::name).orElse(null);
    return outputs.stream()
        .map(output -> output.toDisplay(output.name().equals(selectedName)))
        .toList();
  }

  /**
   * Posición y tamaño de la salida elegida.
   *
   * @return geometría actual, vacía si no hay pantalla detectada
   */
  public Optional<DisplayGeometry> selectedGeometry() {
    return selected.flatMap(XrandrOutput::geometry);
  }

  /**
   * Resolución de la salida elegida.
   *
   * @return texto {@code AxB} o vacío si no hay pantalla detectada
   */
  public Optional<String> selectedResolution() {
    return selected
        .map(output -> output.toDisplay(true))
        .filter(display -> display.width() != null && display.height() != null)
        .map(display -> display.width() + "x" + display.height());
  }

  static Optional<XrandrOutput> choose(List<XrandrOutput> outputs) {
    Optional<XrandrOutput> primary =
        outputs.stream().filter(output -> output.connected() && output.primary()).findFirst();
    return primary.or(() -> outputs.stream().filter(XrandrOutput::connected).findFirst());
  }

  private void refresh(boolean publish, boolean forcePublish) {
    List<XrandrOutput> detected = query();
    Optional<XrandrOutput> chosen = choose(detected);
    if (chosen.isPresent() && adapt(chosen.get())) {
      detected = query();
      chosen = choose(detected);
    }
    outputs = detected;
    selected = chosen;
    String newSignature = signature(detected);
    String newSelection = chosen.map(DisplayService::selectionSignature).orElse("");
    boolean changed = !newSignature.equals(lastSignature);
    boolean selectionChanged = !newSelection.equals(lastSelection);
    lastSignature = newSignature;
    lastSelection = newSelection;
    if (publish && (changed || forcePublish)) {
      log.info(
          "Pantallas actualizadas: {} (elegida: {})",
          detected.stream().filter(XrandrOutput::connected).map(XrandrOutput::name).toList(),
          chosen.map(XrandrOutput::name).orElse("ninguna"));
      events.publishEvent(new DisplayChangedEvent(selectionChanged));
    }
  }

  private List<XrandrOutput> query() {
    return runner.run(List.of("xrandr", "--query")).map(XrandrParser::parse).orElse(List.of());
  }

  private boolean adapt(XrandrOutput output) {
    String wanted = manualModes.get(output.name());
    if (wanted != null && !output.modeNames().contains(wanted)) {
      manualModes.remove(output.name());
      wanted = null;
    }
    String target =
        wanted != null ? wanted : output.preferredMode().map(XrandrMode::name).orElse(null);
    if (target == null) {
      return false;
    }
    boolean alreadyActive =
        output.currentMode().map(mode -> mode.name().equals(target)).orElse(false);
    if (alreadyActive) {
      return false;
    }
    String attempt = output.name() + "@" + target + "@" + selectionSignature(output);
    if (attempt.equals(failedApply)) {
      return false;
    }
    List<String> command =
        wanted != null
            ? List.of("xrandr", "--output", output.name(), "--mode", wanted)
            : List.of("xrandr", "--output", output.name(), "--auto");
    boolean applied = runner.run(command).isPresent();
    if (!applied) {
      failedApply = attempt;
      log.warn("No se pudo adaptar la resolución de {} a {}", output.name(), target);
    }
    return applied;
  }

  private static String signature(List<XrandrOutput> outputs) {
    return outputs.stream()
        .map(output -> output.name() + ":" + output.connected() + ":" + selectionSignature(output))
        .collect(Collectors.joining("|"));
  }

  private static String selectionSignature(XrandrOutput output) {
    return output.name()
        + ":"
        + output.geometry().map(Objects::toString).orElse("off")
        + ":"
        + output.currentMode().map(XrandrMode::name).orElse("-");
  }
}
