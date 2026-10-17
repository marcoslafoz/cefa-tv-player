package es.cefa.tv.player.pairing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Estado persistente tras el alta.
 *
 * @param serverUrl URL base del core
 * @param playerId identificador asignado por el servidor
 * @param deviceToken token de dispositivo
 * @param name nombre asignado
 * @param pollIntervalSec segundos entre latidos indicados por el servidor
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerState(
    String serverUrl, String playerId, String deviceToken, String name, int pollIntervalSec) {}
