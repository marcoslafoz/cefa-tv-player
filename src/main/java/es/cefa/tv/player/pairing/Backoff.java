package es.cefa.tv.player.pairing;

import java.util.Random;

/** Espera creciente entre reintentos, de 2 s a 60 s por defecto, con variación aleatoria. */
public class Backoff {

  /** Espera mínima por defecto en milisegundos. */
  public static final long DEFAULT_MIN_MS = 2000;

  /** Espera máxima por defecto en milisegundos. */
  public static final long DEFAULT_MAX_MS = 60000;

  private static final double JITTER = 0.2;

  private final long minMs;
  private final long maxMs;
  private final Random random;
  private int attempts;

  /** Crea una espera con los límites por defecto. */
  public Backoff() {
    this(DEFAULT_MIN_MS, DEFAULT_MAX_MS, new Random());
  }

  /**
   * Crea una espera con límites propios.
   *
   * @param minMs espera del primer reintento
   * @param maxMs espera máxima
   * @param random generador de la variación aleatoria
   */
  public Backoff(long minMs, long maxMs, Random random) {
    this.minMs = minMs;
    this.maxMs = maxMs;
    this.random = random;
  }

  /**
   * Calcula la espera del siguiente reintento y avanza el contador.
   *
   * @return milisegundos a esperar, nunca menos que el mínimo ni más que el máximo
   */
  public synchronized long nextDelayMs() {
    double base = Math.min((double) maxMs, minMs * Math.pow(2, Math.min(attempts, 30)));
    attempts++;
    double factor = 1 + (random.nextDouble() * 2 - 1) * JITTER;
    long delay = Math.round(base * factor);
    return Math.max(minMs, Math.min(maxMs, delay));
  }

  /**
   * Número de reintentos consecutivos fallidos.
   *
   * @return reintentos desde el último {@link #reset()}
   */
  public synchronized int attempts() {
    return attempts;
  }

  /** Vuelve a la espera mínima tras un éxito. */
  public synchronized void reset() {
    attempts = 0;
  }
}
