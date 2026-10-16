package es.cefa.tv.player.display;

import java.util.List;

/**
 * Pantalla informada al servidor.
 *
 * @param output nombre de la salida
 * @param connected si hay un monitor conectado
 * @param primary si es la salida primaria
 * @param selected si es la salida donde se reproduce
 * @param width ancho actual, o del modo preferido si la salida está apagada
 * @param height alto actual, o del modo preferido si la salida está apagada
 * @param refreshHz frecuencia de refresco
 * @param xOffset desplazamiento horizontal en el escritorio
 * @param yOffset desplazamiento vertical en el escritorio
 * @param modes modos disponibles
 */
public record DisplayOutput(
    String output,
    boolean connected,
    boolean primary,
    boolean selected,
    Integer width,
    Integer height,
    Double refreshHz,
    Integer xOffset,
    Integer yOffset,
    List<String> modes) {}
