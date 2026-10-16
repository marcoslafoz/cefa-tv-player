package es.cefa.tv.player.display;

/**
 * Evento publicado cuando cambian las pantallas detectadas.
 *
 * @param selectionChanged si cambió la salida elegida o su posición y tamaño, de modo que el
 *     navegador debe recolocarse
 */
public record DisplayChangedEvent(boolean selectionChanged) {}
