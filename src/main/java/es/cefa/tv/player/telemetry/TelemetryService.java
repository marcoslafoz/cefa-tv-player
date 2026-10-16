package es.cefa.tv.player.telemetry;

import es.cefa.tv.player.display.DisplayChangedEvent;
import es.cefa.tv.player.display.DisplayService;
import es.cefa.tv.player.sync.PlayerSocket;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Compone la telemetría del reproductor y la envía al servidor por el WebSocket. */
@Service
public class TelemetryService {

  private final SystemInfoCollector collector;
  private final DisplayService displays;
  private final PlayerSocket socket;

  /**
   * Crea el servicio.
   *
   * @param collector recolector de datos del equipo
   * @param displays pantallas detectadas
   * @param socket WebSocket con el servidor
   */
  public TelemetryService(
      SystemInfoCollector collector, DisplayService displays, PlayerSocket socket) {
    this.collector = collector;
    this.displays = displays;
    this.socket = socket;
  }

  /**
   * Toma una muestra completa.
   *
   * @return datos del equipo y pantallas
   */
  public Telemetry snapshot() {
    return new Telemetry(collector.collect(), displays.displays());
  }

  /** Envía la telemetría cada 15 segundos mientras el WebSocket esté abierto. */
  @Scheduled(fixedDelay = 15000, initialDelay = 15000)
  public void publish() {
    if (!socket.isOpen()) {
      return;
    }
    Telemetry telemetry = snapshot();
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("type", "TELEMETRY");
    message.put("system", telemetry.system());
    message.put("displays", telemetry.displays());
    socket.sendText(message);
  }

  /**
   * Avisa al servidor en cuanto cambian las pantallas, sin esperar al siguiente ciclo.
   *
   * @param event cambio de pantallas detectado
   */
  @EventListener
  public void onDisplayChanged(DisplayChangedEvent event) {
    publish();
  }
}
