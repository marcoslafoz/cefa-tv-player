package es.cefa.tv.player.playback;

import es.cefa.tv.player.shared.model.Manifest;
import es.cefa.tv.player.shared.model.Manifest.ManifestSchedule;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Decide qué playlist toca emitir en un instante usando el reloj local y la zona horaria del
 * manifest: gana la programación activa de mayor prioridad y, en empate, la que empezó más tarde.
 */
@Component
public class ScheduleResolver {

  private static final Comparator<ManifestSchedule> PRECEDENCE =
      Comparator.comparingInt(ManifestSchedule::priority)
          .thenComparing(
              ManifestSchedule::startDate, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(
              ManifestSchedule::startTime, Comparator.nullsFirst(Comparator.naturalOrder()));

  /**
   * Resuelve la playlist activa, recurriendo a la de reserva si no hay programación.
   *
   * @param manifest manifest aplicado
   * @param now instante actual
   * @return identificador de la playlist, o vacío si no hay nada que emitir
   */
  public Optional<String> activePlaylistId(Manifest manifest, Instant now) {
    ZonedDateTime local = now.atZone(zoneOf(manifest));
    Optional<String> scheduled = activeSchedule(manifest, local).map(ManifestSchedule::playlistId);
    if (scheduled.isPresent()) {
      return scheduled;
    }
    return Optional.ofNullable(manifest.fallbackPlaylistId());
  }

  /**
   * Programación activa en un instante local.
   *
   * @param manifest manifest aplicado
   * @param local instante en la zona horaria del reproductor
   * @return programación ganadora, si hay alguna
   */
  public Optional<ManifestSchedule> activeSchedule(Manifest manifest, ZonedDateTime local) {
    return manifest.schedulesOrEmpty().stream()
        .filter(ManifestSchedule::enabled)
        .filter(schedule -> isActive(schedule, local))
        .max(PRECEDENCE);
  }

  private boolean isActive(ManifestSchedule schedule, ZonedDateTime local) {
    LocalDate today = local.toLocalDate();
    LocalTime time = local.toLocalTime();
    if (schedule.allDay()) {
      return matchesDay(schedule, today);
    }
    LocalTime start = schedule.startTime() == null ? LocalTime.MIDNIGHT : schedule.startTime();
    LocalTime end = schedule.endTime();
    if (end == null) {
      return matchesDay(schedule, today) && !time.isBefore(start);
    }
    if (end.isAfter(start)) {
      return matchesDay(schedule, today) && !time.isBefore(start) && time.isBefore(end);
    }
    boolean startsToday = matchesDay(schedule, today) && !time.isBefore(start);
    boolean spillsFromYesterday = matchesDay(schedule, today.minusDays(1)) && time.isBefore(end);
    return startsToday || spillsFromYesterday;
  }

  private boolean matchesDay(ManifestSchedule schedule, LocalDate date) {
    if (schedule.startDate() != null && date.isBefore(schedule.startDate())) {
      return false;
    }
    if (schedule.endDate() != null && date.isAfter(schedule.endDate())) {
      return false;
    }
    if (schedule.daysOfWeek() == null || schedule.daysOfWeek().isEmpty()) {
      return true;
    }
    DayOfWeek day = date.getDayOfWeek();
    return schedule.daysOfWeek().contains(day.getValue());
  }

  private ZoneId zoneOf(Manifest manifest) {
    try {
      return manifest.timezone() == null ? ZoneId.systemDefault() : ZoneId.of(manifest.timezone());
    } catch (RuntimeException e) {
      return ZoneId.systemDefault();
    }
  }
}
