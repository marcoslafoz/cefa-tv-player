package es.cefa.tv.player.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Programación completa de un reproductor tal como la publica el servidor.
 *
 * @param version versión del manifest
 * @param generatedAt instante de generación
 * @param playerId identificador del reproductor
 * @param timezone zona horaria con la que se evalúan las franjas
 * @param fallbackPlaylistId playlist de reserva cuando no hay programación activa
 * @param files blobs necesarios identificados por sha256
 * @param assets medios por identificador
 * @param playlists playlists por identificador
 * @param schedules programaciones
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Manifest(
    long version,
    String generatedAt,
    String playerId,
    String timezone,
    String fallbackPlaylistId,
    List<ManifestFile> files,
    Map<String, ManifestAsset> assets,
    Map<String, ManifestPlaylist> playlists,
    List<ManifestSchedule> schedules) {

  /**
   * Devuelve la lista de ficheros nunca nula.
   *
   * @return ficheros del manifest
   */
  public List<ManifestFile> filesOrEmpty() {
    return files == null ? List.of() : files;
  }

  /**
   * Devuelve las programaciones nunca nulas.
   *
   * @return programaciones del manifest
   */
  public List<ManifestSchedule> schedulesOrEmpty() {
    return schedules == null ? List.of() : schedules;
  }

  /**
   * Fichero necesario para reproducir.
   *
   * @param sha256 huella del contenido
   * @param sizeBytes tamaño en bytes
   * @param mime tipo MIME
   * @param name nombre original
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ManifestFile(String sha256, long sizeBytes, String mime, String name) {}

  /**
   * Medio reproducible.
   *
   * @param id identificador
   * @param type IMAGE, VIDEO, PDF, WEB o HTML
   * @param name nombre
   * @param sha256 huella del fichero (IMAGE, VIDEO, HTML)
   * @param mime tipo MIME
   * @param durationMs duración propia en milisegundos
   * @param url dirección (WEB)
   * @param refreshSec segundos entre recargas (WEB)
   * @param pages páginas rasterizadas (PDF)
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ManifestAsset(
      String id,
      String type,
      String name,
      String sha256,
      String mime,
      Long durationMs,
      String url,
      Integer refreshSec,
      List<ManifestPage> pages) {}

  /**
   * Página de un PDF.
   *
   * @param index posición de la página
   * @param sha256 huella de la imagen de la página
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ManifestPage(int index, String sha256) {}

  /**
   * Lista ordenada de medios.
   *
   * @param id identificador
   * @param name nombre
   * @param items elementos en orden
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ManifestPlaylist(String id, String name, List<ManifestItem> items) {}

  /**
   * Elemento de una playlist.
   *
   * @param assetId medio referenciado
   * @param durationSec segundos en pantalla (por página en un PDF)
   * @param fit COVER o CONTAIN
   * @param muted silenciar el vídeo
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ManifestItem(String assetId, int durationSec, String fit, boolean muted) {}

  /**
   * Programación de una playlist.
   *
   * @param id identificador
   * @param playlistId playlist a emitir
   * @param priority prioridad; gana la mayor
   * @param startDate primer día, o nulo
   * @param endDate último día, o nulo
   * @param startTime hora de inicio
   * @param endTime hora de fin (exclusiva)
   * @param daysOfWeek días de la semana, 1 lunes a 7 domingo
   * @param allDay si ocupa el día completo
   * @param enabled si está habilitada
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ManifestSchedule(
      String id,
      String playlistId,
      int priority,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime startTime,
      LocalTime endTime,
      List<Integer> daysOfWeek,
      boolean allDay,
      boolean enabled) {}
}
