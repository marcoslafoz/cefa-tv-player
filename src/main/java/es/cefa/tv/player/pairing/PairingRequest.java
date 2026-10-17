package es.cefa.tv.player.pairing;

import java.util.Map;

/**
 * Petición de emparejamiento al servidor.
 *
 * @param hostname nombre de la máquina o nombre solicitado
 * @param hardware datos de hardware y sistema
 * @param resolution resolución de pantalla, por ejemplo {@code 1920x1080}
 * @param playerVersion versión del reproductor
 * @param playerBuild commit corto con el que se construyó la imagen
 * @param timezone zona horaria local
 */
public record PairingRequest(
    String hostname,
    Map<String, Object> hardware,
    String resolution,
    String playerVersion,
    String playerBuild,
    String timezone) {}
