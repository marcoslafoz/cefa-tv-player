package es.cefa.tv.player.browser;

import es.cefa.tv.player.display.DisplayChangedEvent;
import es.cefa.tv.player.display.DisplayGeometry;
import es.cefa.tv.player.display.DisplayService;
import es.cefa.tv.player.shared.config.PlayerProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Lanza Chromium en modo kiosco y lo vuelve a arrancar si se cierra o falla, salvo que esté pausado
 * por el modo mantenimiento.
 */
@Slf4j
@Component
public class ChromiumLauncher {

  private static final long RESTART_DELAY_MS = 3000;
  private static final long PAUSE_POLL_MS = 500;
  private static final List<String> KIOSK_FLAGS =
      List.of(
          "--kiosk",
          "--start-fullscreen",
          "--noerrdialogs",
          "--disable-infobars",
          "--disable-translate",
          "--disable-features=Translate,TranslateUI,AutofillServerCommunication,"
              + "PasswordManagerOnboarding,MediaRouter",
          "--disable-save-password-bubble",
          "--password-store=basic",
          "--use-mock-keychain",
          "--disable-session-crashed-bubble",
          "--hide-crash-restore-bubble",
          "--no-first-run",
          "--no-default-browser-check",
          "--disable-component-update",
          "--disable-sync",
          "--disable-default-apps",
          "--disable-pinch",
          "--overscroll-history-navigation=0",
          "--autoplay-policy=no-user-gesture-required",
          "--hide-scrollbars",
          "--force-device-scale-factor=1",
          "--lang=es-ES",
          "--accept-lang=es-ES,es");
  private static final List<String> VOLATILE_PROFILE_FILES =
      List.of(
          "Local State",
          "Default/Preferences",
          "Default/Secure Preferences",
          "Default/Current Session",
          "Default/Current Tabs",
          "Default/Last Session",
          "Default/Last Tabs",
          "Default/Web Data",
          "Default/Login Data",
          "Default/Login Data-journal");

  private final PlayerProperties properties;
  private final BrowserController browser;
  private final DisplayService display;
  private volatile boolean running;
  private volatile boolean paused;
  private volatile Process process;
  private Thread supervisor;

  /**
   * Crea el lanzador.
   *
   * @param properties configuración del reproductor
   * @param browser control de navegación, informado en cada arranque
   * @param display pantallas detectadas, donde se coloca la ventana
   */
  public ChromiumLauncher(
      PlayerProperties properties, BrowserController browser, DisplayService display) {
    this.properties = properties;
    this.browser = browser;
    this.display = display;
  }

  /** Arranca el supervisor si el navegador está habilitado. */
  @PostConstruct
  public void start() {
    if (!properties.chromium().enabled()) {
      log.info("Chromium deshabilitado");
      return;
    }
    running = true;
    supervisor = new Thread(this::supervise, "chromium-supervisor");
    supervisor.setDaemon(true);
    supervisor.start();
  }

  /** Detiene el supervisor y cierra Chromium. */
  @PreDestroy
  public void stop() {
    running = false;
    Process current = process;
    if (current != null) {
      current.destroy();
    }
  }

  /**
   * Relanza el navegador cuando cambia la salida elegida o su resolución.
   *
   * @param event cambio de pantallas detectado
   */
  @EventListener
  public void onDisplayChanged(DisplayChangedEvent event) {
    if (event.selectionChanged()) {
      restartBrowser();
    }
  }

  /** Cierra el navegador actual para que el supervisor lo relance. */
  public void restartBrowser() {
    Process current = process;
    if (current != null) {
      current.destroy();
    }
  }

  /** Cierra el navegador y evita que se relance hasta llamar a {@link #resume()}. */
  public void pause() {
    paused = true;
    restartBrowser();
  }

  /** Permite que el supervisor vuelva a abrir el navegador en modo kiosco. */
  public void resume() {
    paused = false;
  }

  /**
   * Indica si el navegador está pausado.
   *
   * @return {@code true} mientras no se relanza
   */
  public boolean isPaused() {
    return paused;
  }

  List<String> command() {
    PlayerProperties.Chromium chromium = properties.chromium();
    List<String> command = new ArrayList<>();
    command.add(chromium.binary());
    command.addAll(KIOSK_FLAGS);
    DisplayGeometry geometry = display.selectedGeometry().orElse(new DisplayGeometry(0, 0, 0, 0));
    command.add("--window-position=" + geometry.x() + "," + geometry.y());
    if (geometry.width() > 0 && geometry.height() > 0) {
      command.add("--window-size=" + geometry.width() + "," + geometry.height());
    }
    command.add("--disable-dev-shm-usage");
    command.add("--remote-debugging-port=" + chromium.debugPort());
    command.add("--remote-allow-origins=*");
    command.add("--user-data-dir=" + properties.dataDir().resolve("chromium").toAbsolutePath());
    if (chromium.noSandbox()) {
      command.add("--no-sandbox");
    }
    command.add(browser.playerUrl());
    return command;
  }

  private void supervise() {
    while (running) {
      if (paused) {
        try {
          Thread.sleep(PAUSE_POLL_MS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
        continue;
      }
      try {
        cleanProfile();
        process =
            new ProcessBuilder(command())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        browser.markOnPlayer();
        log.info("Chromium iniciado");
        int exit = process.waitFor();
        log.warn("Chromium terminó con código {}", exit);
      } catch (IOException e) {
        log.error("No se pudo iniciar Chromium: {}", e.getMessage());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      try {
        Thread.sleep(RESTART_DELAY_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  void cleanProfile() throws IOException {
    Path profile = properties.dataDir().resolve("chromium");
    Files.createDirectories(profile);
    for (String lock : List.of("SingletonLock", "SingletonSocket", "SingletonCookie")) {
      Files.deleteIfExists(profile.resolve(lock));
    }
    for (String file : VOLATILE_PROFILE_FILES) {
      Files.deleteIfExists(profile.resolve(file));
    }
    Path sessions = profile.resolve("Default/Sessions");
    if (Files.isDirectory(sessions)) {
      try (var entries = Files.list(sessions)) {
        for (Path entry : entries.toList()) {
          Files.deleteIfExists(entry);
        }
      }
    }
  }
}
