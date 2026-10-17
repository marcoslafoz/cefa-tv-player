package es.cefa.tv.player.playback;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Pausa de la reproducción mientras un operador controla la pantalla en remoto: el motor no avanza
 * de elemento ni recarga la página, para que no se pierda lo que se está escribiendo. Tras cerrar
 * la sesión se mantiene un margen para que una reconexión inmediata no deje avanzar la rotación.
 */
@Component
public class PlaybackHold {

  /** Margen tras cerrar la sesión remota durante el que la reproducción sigue congelada. */
  public static final Duration GRACE = Duration.ofSeconds(10);

  private final Clock clock;
  private volatile boolean held;
  private volatile Instant releasedAt;

  /**
   * Crea la pausa.
   *
   * @param clock reloj del reproductor
   */
  public PlaybackHold(Clock clock) {
    this.clock = clock;
  }

  /** Congela la reproducción porque empieza una sesión remota. */
  public void hold() {
    held = true;
    releasedAt = null;
  }

  /** Marca el fin de la sesión remota; la reproducción se reanuda tras el margen. */
  public void release() {
    if (held) {
      held = false;
      releasedAt = clock.instant();
    }
  }

  /**
   * Indica si la reproducción debe seguir congelada.
   *
   * @return {@code true} durante la sesión remota y en el margen posterior
   */
  public boolean isHeld() {
    if (held) {
      return true;
    }
    Instant released = releasedAt;
    return released != null && clock.instant().isBefore(released.plus(GRACE));
  }
}
