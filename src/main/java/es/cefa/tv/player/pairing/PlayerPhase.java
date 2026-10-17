package es.cefa.tv.player.pairing;

/** Fase del ciclo de vida del reproductor frente al servidor. */
public enum PlayerPhase {

  /** Arrancando, sin haber hecho nada todavía. */
  STARTING,

  /** Sin emparejar y buscando el servidor en la red. */
  SEARCHING_SERVER,

  /** Emparejamiento pedido y código mostrado, a la espera de que el administrador lo apruebe. */
  PAIRING,

  /** Emparejado pero sin comunicación con el servidor. */
  CONNECTING,

  /** Emparejado y en comunicación con el servidor. */
  ONLINE
}
