package es.cefa.tv.player.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Respuesta a un latido.
 *
 * @param desiredVersion versión que debería aplicar el reproductor
 * @param commands órdenes pendientes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HeartbeatResponse(long desiredVersion, List<Command> commands) {

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
