package es.cefa.tv.player.playback;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PlaybackHoldTest {

  private final AtomicReference<Instant> now =
      new AtomicReference<>(Instant.parse("2026-10-16T10:00:00Z"));
  private final Clock clock =
      new Clock() {
        @Override
        public ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
          return this;
        }

        @Override
        public Instant instant() {
          return now.get();
        }
      };
  private final PlaybackHold hold = new PlaybackHold(clock);

  @Test
  void isFreeUntilARemoteSessionStarts() {
    assertThat(hold.isHeld()).isFalse();
  }

  @Test
  void staysHeldForTheWholeSession() {
    hold.hold();
    now.set(now.get().plus(Duration.ofHours(3)));

    assertThat(hold.isHeld()).isTrue();
  }

  @Test
  void staysHeldDuringTheGraceAfterTheSessionEnds() {
    hold.hold();
    hold.release();

    now.set(now.get().plus(PlaybackHold.GRACE).minusMillis(1));
    assertThat(hold.isHeld()).isTrue();

    now.set(now.get().plusMillis(2));
    assertThat(hold.isHeld()).isFalse();
  }

  @Test
  void aNewSessionInsideTheGraceKeepsHolding() {
    hold.hold();
    hold.release();
    now.set(now.get().plusSeconds(5));
    hold.hold();

    now.set(now.get().plus(Duration.ofMinutes(5)));

    assertThat(hold.isHeld()).isTrue();
  }

  @Test
  void releasingWithoutHoldingDoesNothing() {
    hold.release();

    assertThat(hold.isHeld()).isFalse();
  }
}
