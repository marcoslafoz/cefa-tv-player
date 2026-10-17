package es.cefa.tv.player.shared.time;

/** Espera interrumpible, separada para poder sustituirla en las pruebas. */
public interface Sleeper {

  /**
   * Espera el tiempo indicado.
   *
   * @param millis milisegundos a esperar
   * @throws InterruptedException si el hilo se interrumpe durante la espera
   */
  void sleep(long millis) throws InterruptedException;
}
