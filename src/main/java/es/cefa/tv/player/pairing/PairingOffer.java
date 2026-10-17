package es.cefa.tv.player.pairing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Emparejamiento pendiente que devuelve el servidor.
 *
 * @param pairingId identificador del emparejamiento
 * @param code código que se muestra en pantalla, por ejemplo {@code ABC-123}
 * @param secret secreto que solo conoce este reproductor y que se envía al sondear
 * @param expiresAt instante en que caduca el código
 * @param pollIntervalSec segundos entre sondeos
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PairingOffer(
    String pairingId, String code, String secret, Instant expiresAt, int pollIntervalSec) {}
