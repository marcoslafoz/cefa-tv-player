package es.cefa.tv.player.playback;

import es.cefa.tv.player.browser.BrowserController;
import es.cefa.tv.player.pairing.PlayerPhase;
import es.cefa.tv.player.shared.model.Manifest;
import es.cefa.tv.player.status.PlayerStatus;
import es.cefa.tv.player.storage.ManifestStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Motor de reproducción: elige la playlist activa con el reloj local, avanza paso a paso y ordena a
 * la página local o a Chromium qué mostrar. No depende de la conexión con el servidor.
 */
@Slf4j
@Component
public class PlaybackEngine {

  private static final long POLL_MS = 500;
  private static final long IDLE_MS = 2000;

  private final ManifestStore manifests;
  private final ScheduleResolver resolver;
  private final PlaylistExpander expander;
  private final PlaybackBroadcaster broadcaster;
  private final BrowserController browser;
  private final PlayerStatus status;
  private final PlaybackHold playbackHold;
  private final IdleLogoService idleLogo;
  private final AtomicLong sequence = new AtomicLong();
  private volatile boolean running;
  private Thread thread;
  private Map<String, Object> lastScreen;

  /**
   * Crea el motor.
   *
   * @param manifests manifest aplicado
   * @param resolver resolución de la programación
   * @param expander expansión de playlists
   * @param broadcaster difusión a la página local
   * @param browser control del navegador
   * @param status estado compartido
   * @param playbackHold pausa durante el control remoto
   * @param idleLogo logo de la pantalla sin programación
   */
  public PlaybackEngine(
      ManifestStore manifests,
      ScheduleResolver resolver,
      PlaylistExpander expander,
      PlaybackBroadcaster broadcaster,
      BrowserController browser,
      PlayerStatus status,
      PlaybackHold playbackHold,
      IdleLogoService idleLogo) {
    this.manifests = manifests;
    this.resolver = resolver;
    this.expander = expander;
    this.broadcaster = broadcaster;
    this.browser = browser;
    this.status = status;
    this.playbackHold = playbackHold;
    this.idleLogo = idleLogo;
  }

  /** Arranca el hilo de reproducción. */
  @PostConstruct
  public void start() {
    running = true;
    thread = new Thread(this::run, "playback");
    thread.setDaemon(true);
    thread.start();
  }

  /** Detiene el hilo de reproducción. */
  @PreDestroy
  public void stop() {
    running = false;
    if (thread != null) {
      thread.interrupt();
    }
  }

  private void run() {
    String currentPlaylist = null;
    long currentVersion = -1;
    List<Slide> slides = List.of();
    int index = 0;
    while (running) {
      try {
        if (playbackHold.isHeld()) {
          pause(POLL_MS);
          continue;
        }
        PlayerPhase phase = status.phase();
        if (phase == PlayerPhase.PAIRING || phase == PlayerPhase.SEARCHING_SERVER) {
          screen(currentScreen(true));
          pause(IDLE_MS);
          continue;
        }
        Optional<Manifest> current = manifests.current();
        if (current.isEmpty()) {
          screen(currentScreen(true));
          pause(IDLE_MS);
          continue;
        }
        Manifest manifest = current.get();
        Optional<String> playlistId = resolver.activePlaylistId(manifest, Instant.now());
        if (playlistId.isEmpty()) {
          screen(currentScreen(false));
          pause(IDLE_MS);
          continue;
        }
        if (!playlistId.get().equals(currentPlaylist) || manifest.version() != currentVersion) {
          if (!playlistId.get().equals(currentPlaylist)) {
            index = 0;
          }
          slides = expander.expand(manifest, playlistId.get());
          currentPlaylist = playlistId.get();
          currentVersion = manifest.version();
        }
        if (slides.isEmpty()) {
          screen(currentScreen(false));
          pause(IDLE_MS);
          continue;
        }
        if (index >= slides.size()) {
          index = 0;
        }
        Slide slide = slides.get(index);
        show(slide, currentPlaylist);
        boolean completed = hold(slide, manifest.version(), currentPlaylist);
        if (completed) {
          index++;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (RuntimeException e) {
        log.warn("Error en el motor de reproducción: {}", e.getMessage());
        status.playing("ERROR", null, null);
        status.error(e.getMessage());
        try {
          pause(IDLE_MS);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }
  }

  private boolean hold(Slide slide, long version, String playlistId) throws InterruptedException {
    long end = System.currentTimeMillis() + slide.durationSec() * 1000L;
    long nextRefresh =
        slide.refreshSec() > 0
            ? System.currentTimeMillis() + slide.refreshSec() * 1000L
            : Long.MAX_VALUE;
    while (running && System.currentTimeMillis() < end) {
      pause(POLL_MS);
      if (playbackHold.isHeld()) {
        end += POLL_MS;
        if (nextRefresh != Long.MAX_VALUE) {
          nextRefresh += POLL_MS;
        }
        continue;
      }
      Optional<Manifest> current = manifests.current();
      if (current.isEmpty() || current.get().version() != version) {
        return false;
      }
      Optional<String> active = resolver.activePlaylistId(current.get(), Instant.now());
      if (active.isEmpty() || !active.get().equals(playlistId)) {
        return false;
      }
      if (System.currentTimeMillis() >= nextRefresh) {
        browser.reload();
        nextRefresh = System.currentTimeMillis() + slide.refreshSec() * 1000L;
      }
    }
    return true;
  }

  private void show(Slide slide, String playlistId) {
    switch (slide.kind()) {
      case "WEB" -> browser.navigate(slide.url());
      case "HTML" -> browser.navigate(browser.mediaUrl(slide.sha256()));
      default -> {
        browser.ensurePlayer();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "SHOW");
        event.put("seq", sequence.incrementAndGet());
        event.put("kind", slide.kind());
        event.put("src", "/media/" + slide.sha256());
        event.put("fit", slide.fit() == null ? "COVER" : slide.fit());
        event.put("muted", slide.muted());
        event.put("durationSec", slide.durationSec());
        broadcaster.publish(event);
      }
    }
    lastScreen = null;
    status.playing("PLAYING", playlistId, slide.assetId());
  }

  private void screen(Map<String, Object> event) {
    if (event.equals(lastScreen)) {
      return;
    }
    lastScreen = event;
    browser.ensurePlayer();
    broadcaster.publish(event);
    status.playing("IDLE", null, null);
  }

  private Map<String, Object> currentScreen(boolean waitingForContent) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "SCREEN");
    switch (status.phase()) {
      case PAIRING -> {
        event.put("screen", "PAIRING");
        event.put("code", status.pairingCode());
        event.put("server", status.serverUrl());
        event.put("error", status.connectionError());
      }
      case SEARCHING_SERVER -> {
        event.put("screen", "SEARCHING");
        event.put("message", status.message());
        event.put("error", status.connectionError());
        event.put("retryAt", retryAtMillis());
      }
      case CONNECTING -> {
        event.put("screen", waitingForContent ? "CONNECTING" : "LOGO");
        event.put("logo", idleLogo.current());
        event.put("server", status.serverUrl());
        event.put("error", status.connectionError());
        event.put("retryAt", retryAtMillis());
      }
      default -> {
        event.put("screen", "LOGO");
        event.put("logo", idleLogo.current());
      }
    }
    return event;
  }

  private Long retryAtMillis() {
    return status.retryAt() == null ? null : status.retryAt().toEpochMilli();
  }

  private void pause(long millis) throws InterruptedException {
    Thread.sleep(millis);
  }
}
