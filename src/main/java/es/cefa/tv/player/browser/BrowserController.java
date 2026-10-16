package es.cefa.tv.player.browser;

import es.cefa.tv.player.shared.config.PlayerProperties;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Gobierna qué muestra Chromium: la página local del reproductor, una web o un HTML. */
@Slf4j
@Component
public class BrowserController {

  private final PlayerProperties properties;
  private final CdpClient cdp;
  private final String localBase;
  private volatile boolean onPlayer = true;

  /**
   * Crea el controlador.
   *
   * @param properties configuración del reproductor
   * @param cdp cliente del protocolo DevTools
   * @param port puerto HTTP local del reproductor
   */
  public BrowserController(
      PlayerProperties properties, CdpClient cdp, @Value("${server.port}") int port) {
    this.properties = properties;
    this.cdp = cdp;
    this.localBase = "http://localhost:" + port;
  }

  /**
   * URL de la página local de reproducción.
   *
   * @return URL absoluta
   */
  public String playerUrl() {
    return localBase + "/player";
  }

  /**
   * URL local de un fichero descargado.
   *
   * @param sha256 huella del fichero
   * @return URL absoluta
   */
  public String mediaUrl(String sha256) {
    return localBase + "/media/" + sha256;
  }

  /** Anota que el navegador ha arrancado mostrando la página del reproductor. */
  public void markOnPlayer() {
    this.onPlayer = true;
  }

  /** Vuelve a la página local del reproductor si el navegador está en otra. */
  public void ensurePlayer() {
    if (!onPlayer) {
      navigate(playerUrl());
    }
  }

  /**
   * Lleva el navegador a una dirección.
   *
   * @param url dirección a mostrar
   */
  public void navigate(String url) {
    if (!properties.chromium().enabled()) {
      onPlayer = url.equals(playerUrl());
      return;
    }
    try {
      cdp.call("Page.navigate", cdp.params().put("url", url));
      onPlayer = url.equals(playerUrl());
    } catch (IOException e) {
      log.debug("No se pudo navegar a {}: {}", url, e.getMessage());
    }
  }

  /** Recarga la página actual. */
  public void reload() {
    if (!properties.chromium().enabled()) {
      return;
    }
    try {
      cdp.call("Page.reload", cdp.params().put("ignoreCache", true));
    } catch (IOException e) {
      log.debug("No se pudo recargar: {}", e.getMessage());
    }
  }
}
