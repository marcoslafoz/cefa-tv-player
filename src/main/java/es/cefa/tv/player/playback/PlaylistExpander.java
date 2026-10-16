package es.cefa.tv.player.playback;

import es.cefa.tv.player.shared.model.Manifest;
import es.cefa.tv.player.shared.model.Manifest.ManifestAsset;
import es.cefa.tv.player.shared.model.Manifest.ManifestItem;
import es.cefa.tv.player.shared.model.Manifest.ManifestPage;
import es.cefa.tv.player.shared.model.Manifest.ManifestPlaylist;
import es.cefa.tv.player.storage.FileStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Convierte una playlist del manifest en la secuencia de pasos que se reproducen. */
@Component
public class PlaylistExpander {

  private static final int DEFAULT_STILL_SECONDS = 10;
  private static final int DEFAULT_WEB_SECONDS = 30;

  private final FileStore files;

  /**
   * Crea el expansor.
   *
   * @param files almacén de ficheros, para omitir lo que no esté descargado
   */
  public PlaylistExpander(FileStore files) {
    this.files = files;
  }

  /**
   * Expande una playlist; el PDF se convierte en una secuencia de imágenes con la duración del item
   * aplicada a cada página.
   *
   * @param manifest manifest aplicado
   * @param playlistId playlist a expandir
   * @return pasos reproducibles en orden
   */
  public List<Slide> expand(Manifest manifest, String playlistId) {
    ManifestPlaylist playlist =
        manifest.playlists() == null ? null : manifest.playlists().get(playlistId);
    if (playlist == null || playlist.items() == null) {
      return List.of();
    }
    List<Slide> slides = new ArrayList<>();
    for (ManifestItem item : playlist.items()) {
      ManifestAsset asset =
          manifest.assets() == null ? null : manifest.assets().get(item.assetId());
      if (asset != null && asset.type() != null) {
        addSlides(slides, asset, item);
      }
    }
    return slides;
  }

  private void addSlides(List<Slide> slides, ManifestAsset asset, ManifestItem item) {
    switch (asset.type()) {
      case "IMAGE", "HTML" ->
          addLocal(slides, asset, item, asset.type(), asset.sha256(), still(item));
      case "VIDEO" ->
          addLocal(slides, asset, item, "VIDEO", asset.sha256(), videoSeconds(asset, item));
      case "WEB" -> {
        if (asset.url() != null) {
          int seconds = item.durationSec() > 0 ? item.durationSec() : DEFAULT_WEB_SECONDS;
          int refresh = asset.refreshSec() == null ? 0 : asset.refreshSec();
          slides.add(
              new Slide(
                  asset.id(),
                  "WEB",
                  null,
                  asset.url(),
                  seconds,
                  item.fit(),
                  item.muted(),
                  refresh));
        }
      }
      case "PDF" -> {
        if (asset.pages() != null) {
          asset.pages().stream()
              .sorted(Comparator.comparingInt(ManifestPage::index))
              .forEach(page -> addLocal(slides, asset, item, "IMAGE", page.sha256(), still(item)));
        }
      }
      default -> {}
    }
  }

  private void addLocal(
      List<Slide> slides,
      ManifestAsset asset,
      ManifestItem item,
      String kind,
      String sha,
      int seconds) {
    if (sha != null && files.has(sha)) {
      slides.add(new Slide(asset.id(), kind, sha, null, seconds, item.fit(), item.muted(), 0));
    }
  }

  private int still(ManifestItem item) {
    return item.durationSec() > 0 ? item.durationSec() : DEFAULT_STILL_SECONDS;
  }

  private int videoSeconds(ManifestAsset asset, ManifestItem item) {
    if (item.durationSec() > 0) {
      return item.durationSec();
    }
    if (asset.durationMs() != null && asset.durationMs() > 0) {
      return (int) Math.ceil(asset.durationMs() / 1000.0);
    }
    return DEFAULT_STILL_SECONDS;
  }
}
