package es.cefa.tv.player.shared.time;

import org.springframework.stereotype.Component;

/** Espera real con {@link Thread#sleep(long)}. */
@Component
public class ThreadSleeper implements Sleeper {

  @Override
  public void sleep(long millis) throws InterruptedException {
    Thread.sleep(millis);
  }
}
