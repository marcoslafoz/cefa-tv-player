package es.cefa.tv.player.maintenance;

import com.fasterxml.jackson.databind.JsonNode;
import es.cefa.tv.player.browser.CdpClient;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Detecta Ctrl+Shift+M dentro de Chromium, también sobre webs externas, inyectando por CDP un
 * oyente de teclado que avisa al reproductor a través de un binding.
 */
@Slf4j
@Component
public class MaintenanceHotkey {

  static final String BINDING = "cefaMaintenance";
  static final String SCRIPT =
      "(()=>{if(window.__cefaMaintenance)return;window.__cefaMaintenance=true;"
          + "addEventListener('keydown',e=>{if(e.ctrlKey&&e.shiftKey&&!e.altKey&&e.code==='KeyM')"
          + "{e.preventDefault();window."
          + BINDING
          + "('enter')}},true)})()";
  private static final long CHECK_SECONDS = 5;

  private final CdpClient cdp;
  private final MaintenanceService maintenance;
  private volatile boolean installed;

  /**
   * Crea el detector.
   *
   * @param cdp cliente DevTools
   * @param maintenance servicio de mantenimiento
   */
  public MaintenanceHotkey(CdpClient cdp, MaintenanceService maintenance) {
    this.cdp = cdp;
    this.maintenance = maintenance;
  }

  /** Registra el oyente de eventos y vigila que el script siga instalado. */
  @PostConstruct
  public void start() {
    cdp.addEventListener(this::onCdpEvent);
    ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "maintenance-hotkey");
              thread.setDaemon(true);
              return thread;
            });
    scheduler.scheduleWithFixedDelay(this::ensureInstalled, 5, CHECK_SECONDS, TimeUnit.SECONDS);
  }

  void onCdpEvent(String method, JsonNode params) {
    if ("Cefa.disconnected".equals(method)) {
      installed = false;
    } else if ("Runtime.bindingCalled".equals(method)
        && BINDING.equals(params.path("name").asText())) {
      maintenance.enter();
    }
  }

  void ensureInstalled() {
    if (installed || maintenance.isActive()) {
      return;
    }
    try {
      cdp.call("Runtime.enable", null);
      cdp.call("Runtime.addBinding", cdp.params().put("name", BINDING));
      cdp.call("Page.addScriptToEvaluateOnNewDocument", cdp.params().put("source", SCRIPT));
      cdp.call("Runtime.evaluate", cdp.params().put("expression", SCRIPT));
      installed = true;
      log.info("Atajo Ctrl+Shift+M del modo mantenimiento instalado");
    } catch (IOException e) {
      log.debug("Atajo de mantenimiento pendiente: {}", e.getMessage());
    }
  }
}
