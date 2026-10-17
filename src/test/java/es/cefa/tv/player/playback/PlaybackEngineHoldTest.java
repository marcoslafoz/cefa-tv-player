package es.cefa.tv.player.playback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.cefa.tv.player.browser.BrowserController;
import es.cefa.tv.player.shared.model.Manifest;
import es.cefa.tv.player.status.PlayerStatus;
import es.cefa.tv.player.storage.ManifestStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlaybackEngineHoldTest {

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
  private final PlaybackBroadcaster broadcaster = mock(PlaybackBroadcaster.class);
  private final BrowserController browser = mock(BrowserController.class);
  private final List<Object> shown = new java.util.concurrent.CopyOnWriteArrayList<>();
  private PlaybackEngine engine;

  @BeforeEach
  void setUp() {
    Manifest manifest =
        new Manifest(1, null, "p", "UTC", null, List.of(), Map.of(), Map.of(), List.of());
    ManifestStore manifests = mock(ManifestStore.class);
    when(manifests.current()).thenReturn(Optional.of(manifest));
    ScheduleResolver resolver = mock(ScheduleResolver.class);
    when(resolver.activePlaylistId(any(), any())).thenReturn(Optional.of("playlist"));
    PlaylistExpander expander = mock(PlaylistExpander.class);
    when(expander.expand(any(), any()))
        .thenReturn(
            List.of(
                new Slide("a", "IMAGE", "a".repeat(64), null, 1, "COVER", true, 0),
                new Slide("b", "IMAGE", "b".repeat(64), null, 1, "COVER", true, 0)));
    org.mockito.Mockito.doAnswer(
            invocation -> {
              shown.add(invocation.getArgument(0));
              return null;
            })
        .when(broadcaster)
        .publish(any());
    engine =
        new PlaybackEngine(
            manifests,
            resolver,
            expander,
            broadcaster,
            browser,
            new PlayerStatus("test"),
            hold,
            mock(IdleLogoService.class));
    engine.start();
  }

  @AfterEach
  void tearDown() {
    engine.stop();
  }

  @Test
  void doesNotAdvanceOrNavigateWhileHeldAndResumesAfterTheGrace() throws Exception {
    await().atMost(Duration.ofSeconds(5)).until(() -> !shown.isEmpty());
    hold.hold();
    Thread.sleep(700);
    int frozenAt = shown.size();

    Thread.sleep(3000);

    assertThat(shown).hasSize(frozenAt);
    org.mockito.Mockito.verify(browser, org.mockito.Mockito.never()).navigate(any());
    org.mockito.Mockito.verify(browser, org.mockito.Mockito.never()).reload();

    hold.release();
    Thread.sleep(2000);
    assertThat(shown).hasSize(frozenAt);

    now.set(now.get().plus(PlaybackHold.GRACE).plusSeconds(1));
    await().atMost(Duration.ofSeconds(5)).until(() -> shown.size() > frozenAt);
  }
}
