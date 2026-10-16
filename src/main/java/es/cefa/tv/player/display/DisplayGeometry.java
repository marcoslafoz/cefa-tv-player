package es.cefa.tv.player.display;

/**
 * Posición y tamaño de una salida dentro del escritorio X.
 *
 * @param x desplazamiento horizontal
 * @param y desplazamiento vertical
 * @param width ancho en píxeles
 * @param height alto en píxeles
 */
public record DisplayGeometry(int x, int y, int width, int height) {}
