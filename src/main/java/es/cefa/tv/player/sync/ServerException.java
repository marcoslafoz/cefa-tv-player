package es.cefa.tv.player.sync;

import lombok.Getter;

/** Respuesta de error del servidor. */
@Getter
public class ServerException extends RuntimeException {

  private final int status;
  private final String errorCode;

  /**
   * Crea la excepción.
   *
   * @param status código HTTP
   * @param errorCode código de error del contrato, si lo hay
   * @param message mensaje del servidor
   */
  public ServerException(int status, String errorCode, String message) {
    super("HTTP " + status + (errorCode == null ? "" : " " + errorCode) + ": " + message);
    this.status = status;
    this.errorCode = errorCode;
  }
}
