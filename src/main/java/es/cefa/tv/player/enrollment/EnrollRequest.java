package es.cefa.tv.player.enrollment;

import java.util.Map;

/**
 * Petición de alta en el servidor.
 *
 * @param enrollmentKey clave de alta
 * @param hostname nombre de la máquina o nombre solicitado
 * @param hardware datos de hardware y sistema
 * @param resolution resolución de pantalla, por ejemplo {@code 1920x1080}
 * @param playerVersion versión del reproductor
 * @param timezone zona horaria local
 */
public record EnrollRequest(
    String enrollmentKey,
    String hostname,
    Map<String, Object> hardware,
    String resolution,
    String playerVersion,
    String timezone) {}
