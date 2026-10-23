package es.cefa.tv.player.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Respuesta a un latido.
 *
 * @param desiredVersion versión que debería aplicar el reproductor
 * @param commands órdenes pendientes
 * @param config configuración que fija el servidor, nula si la respuesta no la trae
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HeartbeatResponse(long desiredVersion, List<Command> commands, Config config) {

  /**
   * Configuración del reproductor decidida desde el servidor.
   *
   * @param preferredOutput salida de vídeo preferida, nula para la selección automática
   * @param idleLogo logo efectivo de la pantalla sin programación, {@code CEFA} o {@code ALGONTEC}
   * @param volume volumen de los vídeos de 0 a 100, nulo si el servidor no lo envía (equivale a 100)
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Config(String preferredOutput, String idleLogo, Integer volume) {}

  /**
   * Orden pendiente.
   *
   * @param type RELOAD, RESTART, UNENROLL, DISPLAY_REDETECT o SET_RESOLUTION
   * @param output salida de vídeo de SET_RESOLUTION
   * @param width ancho pedido por SET_RESOLUTION
   * @param height alto pedido por SET_RESOLUTION
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Command(String type, String output, Integer width, Integer height) {}

  /**
   * Órdenes nunca nulas.
   *
   * @return lista de órdenes
   */
  public List<Command> commandsOrEmpty() {
    return commands == null ? List.of() : commands;
  }
}
