package es.cefa.tv.player.pairing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Emparejamiento en curso que se guarda en disco para sobrevivir a un reinicio: mientras exista, el
 * reproductor sigue sondeando este identificador en lugar de pedir un código nuevo.
 *
 * @param serverUrl URL base del core que lo emitió
 * @param pairingId identificador del emparejamiento
 * @param code código mostrado en pantalla
 * @param secret secreto que acompaña a cada sondeo
 * @param expiresAt caducidad del código, solo informativa
 * @param pollIntervalSec segundos entre sondeos
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PendingPairing(
    String serverUrl,
    String pairingId,
    String code,
    String secret,
    Instant expiresAt,
    int pollIntervalSec) {

  /**
   * Convierte la oferta del servidor en un emparejamiento pendiente.
   *
   * @param serverUrl URL base del core
   * @param offer oferta recibida
   * @return emparejamiento listo para guardar
   */
  public static PendingPairing of(String serverUrl, PairingOffer offer) {
    return new PendingPairing(
        serverUrl,
        offer.pairingId(),
        offer.code(),
        offer.secret(),
        offer.expiresAt(),
        offer.pollIntervalSec());
  }
}
