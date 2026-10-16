package es.cefa.tv.player.playback;

import static org.assertj.core.api.Assertions.assertThat;

import es.cefa.tv.player.shared.model.Manifest;
import es.cefa.tv.player.shared.model.Manifest.ManifestSchedule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScheduleResolverTest {

  private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
  private final ScheduleResolver resolver = new ScheduleResolver();

  private Manifest manifest(String fallback, ManifestSchedule... schedules) {
    return new Manifest(
        1, null, "p", "Europe/Madrid", fallback, List.of(), Map.of(), Map.of(), List.of(schedules));
  }

  private ManifestSchedule schedule(
      String playlist,
      int priority,
      String start,
      String end,
      LocalDate from,
      LocalDate to,
      Integer... days) {
    return new ManifestSchedule(
        playlist + priority,
        playlist,
        priority,
        from,
        to,
        start == null ? null : LocalTime.parse(start),
        end == null ? null : LocalTime.parse(end),
        List.of(days),
        false,
        true);
  }

  private Instant at(String isoLocal) {
    return ZonedDateTime.of(java.time.LocalDateTime.parse(isoLocal), MADRID).toInstant();
  }

  @Test
  void usesTheTimezoneOfTheManifestNotUtc() {
    Manifest manifest =
        manifest(null, schedule("morning", 1, "08:00", "12:00", null, null, 1, 2, 3, 4, 5));
    assertThat(resolver.activePlaylistId(manifest, at("2026-10-14T09:00:00"))).contains("morning");
    assertThat(resolver.activePlaylistId(manifest, at("2026-10-14T07:59:00"))).isEmpty();
    assertThat(resolver.activePlaylistId(manifest, at("2026-10-14T12:00:00"))).isEmpty();
  }

  @Test
  void higherPriorityWinsAndTiesGoToTheLatestStart() {
    Manifest manifest =
        manifest(
            null,
            schedule("base", 1, "00:00", "23:59", null, null, 1, 2, 3, 4, 5, 6, 7),
            schedule("promo", 10, "10:00", "11:00", null, null, 1, 2, 3, 4, 5, 6, 7),
            schedule("a", 5, "08:00", "20:00", null, null, 1, 2, 3, 4, 5, 6, 7),
            schedule("b", 5, "09:00", "20:00", null, null, 1, 2, 3, 4, 5, 6, 7));
    assertThat(resolver.activePlaylistId(manifest, at("2026-10-14T10:30:00"))).contains("promo");
    assertThat(resolver.activePlaylistId(manifest, at("2026-10-14T09:30:00"))).contains("b");
    assertThat(resolver.activePlaylistId(manifest, at("2026-10-14T08:30:00"))).contains("a");
    assertThat(resolver.activePlaylistId(manifest, at("2026-10-14T06:00:00"))).contains("base");
  }

  @Test
  void respectsDaysOfWeekAndDateWindow() {
    LocalDate from = LocalDate.of(2026, 10, 12);
    LocalDate to = LocalDate.of(2026, 10, 14);
    Manifest manifest = manifest(null, schedule("s", 1, "00:00", "23:00", from, to, 3));
    assertThat(resolver.activePlaylistId(manifest, at("2026-10-14T10:00:00"))).contains("s");
    assertThat(resolver.activePlaylistId(manifest, at("2026-10-13T10:00:00"))).isEmpty();
    assertThat(resolver.activePlaylistId(manifest, at("2026-10-21T10:00:00"))).isEmpty();
  }

  @Test
  void fallsBackWhenNothingIsActive() {
    Manifest manifest = manifest("fallback", schedule("s", 1, "08:00", "09:00", null, null, 1));
    assertThat(resolver.activePlaylistId(manifest, at("2026-10-14T15:00:00"))).contains("fallback");
  }

  @Test
  void overnightRangeSpillsIntoTheNextDay() {
    Manifest manifest = manifest(null, schedule("night", 1, "22:00", "02:00", null, null, 3));
    assertThat(resolver.activePlaylistId(manifest, at("2026-10-14T23:00:00"))).contains("night");
    assertThat(resolver.activePlaylistId(manifest, at("2026-10-15T01:00:00"))).contains("night");
    assertThat(resolver.activePlaylistId(manifest, at("2026-10-15T03:00:00"))).isEmpty();
    assertThat(resolver.activePlaylistId(manifest, at("2026-10-14T01:00:00"))).isEmpty();
  }

  @Test
  void disabledSchedulesAreIgnoredAndAllDayIgnoresTimes() {
    ManifestSchedule disabled =
        new ManifestSchedule("d", "off", 99, null, null, null, null, List.of(), true, false);
    ManifestSchedule allDay =
        new ManifestSchedule("a", "all", 1, null, null, null, null, List.of(), true, true);
    Manifest manifest = manifest(null, disabled, allDay);
    assertThat(resolver.activePlaylistId(manifest, at("2026-10-14T03:00:00"))).contains("all");
  }
}
