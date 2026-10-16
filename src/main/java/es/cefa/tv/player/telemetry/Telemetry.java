package es.cefa.tv.player.telemetry;

import es.cefa.tv.player.display.DisplayOutput;
import java.util.List;

/**
 * Telemetría completa del reproductor.
 *
 * @param system datos del equipo
 * @param displays pantallas detectadas
 */
public record Telemetry(SystemInfo system, List<DisplayOutput> displays) {}
