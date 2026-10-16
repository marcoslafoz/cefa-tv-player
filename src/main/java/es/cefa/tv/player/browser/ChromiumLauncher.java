package es.cefa.tv.player.browser;

import es.cefa.tv.player.shared.config.PlayerProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Lanza Chromium en modo kiosco y lo vuelve a arrancar si se cierra o falla. */
@Slf4j
@Component
public class ChromiumLauncher {

  private static final long RESTART_DELAY_MS = 3000;

  private final PlayerProperties properties;
  private final BrowserController browser;
  private volatile boolean running;
  private volatile Process process;
  private Thread supervisor;

  /**
   * Crea el lanzador.
   *
   * @param properties configuración del reproductor
   * @param browser control de navegación, informado en cada arranque
   */
  public ChromiumLauncher(PlayerProperties properties, BrowserController browser) {
    this.properties = properties;
    this.browser = browser;
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

  /** Cierra el navegador actual para que el supervisor lo relance. */
  public void restartBrowser() {
    Process current = process;
    if (current != null) {
      current.destroy();
    }
  }

  List<String> command() {
    PlayerProperties.Chromium chromium = properties.chromium();
    List<String> command = new ArrayList<>();
    command.add(chromium.binary());
    command.add("--kiosk");
    command.add("--start-fullscreen");
    command.add("--noerrdialogs");
    command.add("--disable-infobars");
    command.add("--no-first-run");
    command.add("--disable-session-crashed-bubble");
    command.add("--disable-features=Translate,TranslateUI");
    command.add("--autoplay-policy=no-user-gesture-required");
    command.add("--hide-scrollbars");
    command.add("--overscroll-history-navigation=0");
    command.add("--force-device-scale-factor=1");
    command.add("--window-position=0,0");
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
      try {
        removeStaleLocks();
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

  private void removeStaleLocks() throws IOException {
    Path profile = properties.dataDir().resolve("chromium");
    Files.createDirectories(profile);
    for (String lock : List.of("SingletonLock", "SingletonSocket", "SingletonCookie")) {
      Files.deleteIfExists(profile.resolve(lock));
    }
  }
}
