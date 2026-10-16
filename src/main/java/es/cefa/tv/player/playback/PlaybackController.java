package es.cefa.tv.player.playback;

import es.cefa.tv.player.shared.model.Manifest.ManifestFile;
import es.cefa.tv.player.status.PlayerStatus;
import es.cefa.tv.player.storage.FileStore;
import es.cefa.tv.player.storage.ManifestStore;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Endpoints locales: página de reproducción, eventos SSE, ficheros y estado. */
@Controller
public class PlaybackController {

  private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

  private final PlaybackBroadcaster broadcaster;
  private final FileStore files;
  private final ManifestStore manifests;
  private final PlayerStatus status;

  /**
   * Crea el controlador.
   *
   * @param broadcaster difusor de órdenes
   * @param files almacén de ficheros
   * @param manifests manifest aplicado
   * @param status estado compartido
   */
  public PlaybackController(
      PlaybackBroadcaster broadcaster,
      FileStore files,
      ManifestStore manifests,
      PlayerStatus status) {
    this.broadcaster = broadcaster;
    this.files = files;
    this.manifests = manifests;
    this.status = status;
  }

  /**
   * Página de reproducción a pantalla completa.
   *
   * @return redirección interna al HTML estático
   */
  @GetMapping("/player")
  public String player() {
    return "forward:/player.html";
  }

  /**
   * Flujo de órdenes del motor.
   *
   * @return suscripción SSE
   */
  @GetMapping(value = "/player/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @ResponseBody
  public SseEmitter events() {
    return broadcaster.subscribe();
  }

  /**
   * Sirve un fichero descargado con soporte de {@code Range}.
   *
   * @param sha256 huella del fichero
   * @return contenido o 404
   */
  @GetMapping("/media/{sha256}")
  @ResponseBody
  public ResponseEntity<Resource> media(@PathVariable String sha256) {
    if (!SHA256.matcher(sha256).matches() || !files.has(sha256)) {
      return ResponseEntity.notFound().build();
    }
    MediaType type =
        manifests
            .current()
            .flatMap(
                m ->
                    m.filesOrEmpty().stream()
                        .filter(f -> f.sha256().equalsIgnoreCase(sha256))
                        .map(ManifestFile::mime)
                        .findFirst())
            .map(MediaType::parseMediaType)
            .orElse(MediaType.APPLICATION_OCTET_STREAM);
    return ResponseEntity.ok()
        .contentType(type)
        .cacheControl(CacheControl.noCache())
        .body(new FileSystemResource(files.path(sha256)));
  }

  /**
   * Estado del reproductor para la página de diagnóstico.
   *
   * @return estado en JSON
   */
  @GetMapping("/status.json")
  @ResponseBody
  public Map<String, Object> statusJson() {
    return status.snapshot(manifests.appliedVersion());
  }
}
