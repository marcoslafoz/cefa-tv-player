package es.cefa.tv.player.pairing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Respuesta al sondeo de un emparejamiento.
 *
 * @param status {@code PENDING}, {@code EXPIRED} o {@code APPROVED}
 * @param playerId identificador del reproductor, solo si está aprobado
 * @param deviceToken token de dispositivo, solo si está aprobado y solo en la primera lectura
 * @param name nombre asignado, solo si está aprobado
 * @param pollIntervalSec segundos entre latidos indicados por el servidor
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PairingPoll(
    String status, String playerId, String deviceToken, String name, int pollIntervalSec) {

  /**
   * Indica si el administrador ya ha aprobado el emparejamiento.
   *
   * @return {@code true} si el estado es {@code APPROVED}
   */
  public boolean approved() {
    return "APPROVED".equals(status);
  }

  /**
   * Indica si el código ha caducado.
   *
   * @return {@code true} si el estado es {@code EXPIRED}
   */
  public boolean expired() {
    return "EXPIRED".equals(status);
  }
}
