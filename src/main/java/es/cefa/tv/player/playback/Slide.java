package es.cefa.tv.player.playback;

/**
 * Paso elemental de reproducción: una imagen, un vídeo, una página de PDF, una web o un HTML.
 *
 * @param assetId medio de origen
 * @param kind IMAGE, VIDEO, HTML o WEB
 * @param sha256 huella del fichero local, si lo hay
 * @param url dirección externa (WEB)
 * @param durationSec segundos en pantalla
 * @param fit COVER o CONTAIN
 * @param muted silenciar vídeo
 * @param refreshSec segundos entre recargas de una web, 0 para no recargar
 */
public record Slide(
    String assetId,
    String kind,
    String sha256,
    String url,
    int durationSec,
    String fit,
    boolean muted,
    int refreshSec) {}
