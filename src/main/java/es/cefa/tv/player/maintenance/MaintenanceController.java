package es.cefa.tv.player.maintenance;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints locales del modo mantenimiento; el servidor HTTP solo escucha en 127.0.0.1. */
@RestController
@RequestMapping("/maintenance")
public class MaintenanceController {

  private final MaintenanceService maintenance;

  /**
   * Crea el controlador.
   *
   * @param maintenance servicio de mantenimiento
   */
  public MaintenanceController(MaintenanceService maintenance) {
    this.maintenance = maintenance;
  }

  /**
   * Estado del modo mantenimiento.
   *
   * @return si está activo
   */
  @GetMapping
  public Map<String, Object> status() {
    return Map.of("active", maintenance.isActive());
  }

  /**
   * Cierra el kiosco.
   *
   * @return estado resultante
   */
  @PostMapping("/enter")
  public Map<String, Object> enter() {
    maintenance.enter();
    return status();
  }

  /**
   * Vuelve al kiosco; lo ejecuta el comando {@code cefa-tv-kiosk}.
   *
   * @return estado resultante
   */
  @PostMapping("/resume")
  public Map<String, Object> resume() {
    maintenance.resume();
    return status();
  }
}
