package es.cefa.tv.player.pairing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

final class TestClock extends Clock {

  private Instant now;

  TestClock(Instant start) {
    this.now = start;
  }

  synchronized void advance(Duration duration) {
    now = now.plus(duration);
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public synchronized Instant instant() {
    return now;
  }
}
