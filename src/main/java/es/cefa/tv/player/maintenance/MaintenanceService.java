package es.cefa.tv.player.maintenance;

import es.cefa.tv.player.browser.ChromiumLauncher;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Modo mantenimiento: cierra el kiosco para poder trabajar en el equipo y lo vuelve a abrir con el
 * comando {@code cefa-tv-kiosk} o, por seguridad, pasado un tiempo para no dejar la pantalla
 * olvidada sin contenido.
 */
@Slf4j
@Service
public class MaintenanceService {

  /** Tiempo máximo en mantenimiento antes de volver al kiosco automáticamente. */
  public static final Duration AUTO_RESUME = Duration.ofMinutes(15);

  private final ChromiumLauncher launcher;
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "maintenance");
            thread.setDaemon(true);
            return thread;
          });
  private ScheduledFuture<?> autoResume;

  /**
   * Crea el servicio.
   *
   * @param launcher lanzador de Chromium
   */
  public MaintenanceService(ChromiumLauncher launcher) {
    this.launcher = launcher;
  }

  /** Cierra el kiosco y programa la vuelta automática. */
  public synchronized void enter() {
    if (launcher.isPaused()) {
      return;
    }
    log.warn("Modo mantenimiento: kiosco cerrado, vuelve solo en {} min", AUTO_RESUME.toMinutes());
    launcher.pause();
    autoResume = scheduler.schedule(this::resume, AUTO_RESUME.toSeconds(), TimeUnit.SECONDS);
  }

  /** Vuelve al kiosco y cancela la vuelta automática. */
  public synchronized void resume() {
    if (autoResume != null) {
      autoResume.cancel(false);
      autoResume = null;
    }
    if (launcher.isPaused()) {
      log.info("Fin del modo mantenimiento: se reabre el kiosco");
      launcher.resume();
    }
  }

  /**
   * Indica si el equipo está en mantenimiento.
   *
   * @return {@code true} mientras el kiosco está cerrado
   */
  public boolean isActive() {
    return launcher.isPaused();
  }
}
