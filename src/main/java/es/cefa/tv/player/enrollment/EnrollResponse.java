package es.cefa.tv.player.enrollment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Respuesta del servidor al alta.
 *
 * @param playerId identificador asignado
 * @param deviceToken token de dispositivo
 * @param name nombre asignado
 * @param pollIntervalSec segundos entre latidos
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EnrollResponse(
    String playerId, String deviceToken, String name, int pollIntervalSec) {}
