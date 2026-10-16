package es.cefa.tv.player.sync;

/** Se lanza cuando una descarga se cancela porque ha llegado una versión más reciente. */
public class SyncAbortedException extends RuntimeException {

  /** Crea la excepción. */
  public SyncAbortedException() {
    super("Sincronización cancelada");
  }
}
