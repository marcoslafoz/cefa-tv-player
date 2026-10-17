package es.cefa.tv.player.sync.dto;

import es.cefa.tv.player.display.DisplayOutput;
import es.cefa.tv.player.telemetry.SystemInfo;
import java.util.List;

/**
 * Latido enviado al servidor.
 *
 * @param appliedVersion versión aplicada
 * @param downloadingVersion versión en descarga o 0
 * @param state IDLE, DOWNLOADING, PLAYING o ERROR
 * @param currentPlaylistId playlist actual
 * @param currentAssetId medio actual
 * @param diskFreeMb espacio libre en MB
 * @param uptimeSec segundos desde el arranque
 * @param ip dirección IP local
 * @param error último error
 * @param system datos del equipo
 * @param displays pantallas detectadas
 * @param playerVersion versión del reproductor
 * @param playerBuild commit corto con el que se construyó la imagen
 */
public record HeartbeatRequest(
    long appliedVersion,
    long downloadingVersion,
    String state,
    String currentPlaylistId,
    String currentAssetId,
    long diskFreeMb,
    long uptimeSec,
    String ip,
    String error,
    SystemInfo system,
    List<DisplayOutput> displays,
    String playerVersion,
    String playerBuild) {}
