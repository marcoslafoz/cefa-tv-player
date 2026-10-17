package es.cefa.tv.player.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

class BackoffTest {

  @Test
  void growsFromTwoSecondsAndNeverExceedsOneMinute() {
    Backoff backoff = new Backoff();
    long previousBase = 0;
    for (int attempt = 0; attempt < 12; attempt++) {
      long delay = backoff.nextDelayMs();
      assertThat(delay).isBetween(2000L, 60000L);
      long expectedBase = Math.min(60000L, 2000L << attempt);
      assertThat(delay)
          .isBetween(
              Math.min(60000L, Math.round(expectedBase * 0.8)),
              Math.min(60000L, Math.round(expectedBase * 1.2)) + 1);
      assertThat(expectedBase).isGreaterThanOrEqualTo(previousBase);
      previousBase = expectedBase;
    }
  }

  @Test
  void resetReturnsToTheMinimumWait() {
    Backoff backoff = new Backoff(2000, 60000, new Random(1));
    for (int i = 0; i < 6; i++) {
      backoff.nextDelayMs();
    }
    assertThat(backoff.attempts()).isEqualTo(6);
    backoff.reset();
    assertThat(backoff.attempts()).isZero();
    assertThat(backoff.nextDelayMs()).isBetween(2000L, 2400L);
  }

  @Test
  void addsJitterSoPlayersDoNotRetryAtTheSameInstant() {
    Backoff backoff = new Backoff(2000, 60000, new Random(42));
    long first = backoff.nextDelayMs();
    backoff.reset();
    Backoff other = new Backoff(2000, 60000, new Random(7));
    long second = other.nextDelayMs();
    assertThat(first).isNotEqualTo(second);
  }
}
