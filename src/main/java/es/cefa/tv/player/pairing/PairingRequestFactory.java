package es.cefa.tv.player.pairing;

/** Construye la petición de emparejamiento con los datos actuales del equipo. */
public interface PairingRequestFactory {

  /**
   * Crea la petición.
   *
   * @return datos de la máquina, pantalla, versión y zona horaria
   */
  PairingRequest create();
}
