package es.cefa.tv.player.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.discovery.DiscoveryClient;
import es.cefa.tv.player.discovery.NetworkProbe;
import es.cefa.tv.player.discovery.ServerLocator;
import es.cefa.tv.player.shared.config.PlayerProperties;
import es.cefa.tv.player.shared.time.Sleeper;
import es.cefa.tv.player.status.PlayerStatus;
import es.cefa.tv.player.sync.ServerApi;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PairingServiceTest {

  @TempDir Path dir;

  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
  private final TestClock clock = new TestClock(Instant.parse("2026-10-16T10:00:00Z"));
  private final List<Long> sleeps = new ArrayList<>();
  private FakePairingServer server;
  private PlayerStatus status;
  private StateStore stateStore;
  private PendingPairingStore pendingStore;
  private PairingService service;
  private Sleeper sleeper;

  @BeforeEach
  void setUp() throws Exception {
    server = new FakePairingServer(clock::instant);
    sleeper =
        millis -> {
          sleeps.add(millis);
          clock.advance(Duration.ofMillis(millis));
        };
    PlayerProperties properties =
        new PlayerProperties(dir, server.url(), "", 47808, 30, null, "headless", null);
    status = new PlayerStatus("test");
    stateStore = new StateStore(properties, mapper);
    pendingStore = new PendingPairingStore(properties, mapper);
    ServerLocator locator =
        new ServerLocator(
            properties,
            new DiscoveryClient(properties, mapper),
            new NetworkProbe(),
            status,
            sleeper,
            clock);
    PairingRequestFactory factory =
        () -> new PairingRequest("pantalla", Map.of(), "1920x1080", "test", "abc1234", "UTC");
    service =
        new PairingService(
            new ServerApi(mapper),
            locator,
            factory,
            status,
            stateStore,
            pendingStore,
            sleeper,
            clock);
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @Test
  void showsTheCodeWhilePendingAndStoresTheStateWhenApproved() throws Exception {
    List<String> codesShown = new ArrayList<>();
    server.onPoll = () -> codesShown.add(status.pairingCode());
    server.pollReplies.add(FakePairingServer.pending());
    server.pollReplies.add(FakePairingServer.pending());
    server.pollReplies.add(FakePairingServer.approved("token-1"));

    PlayerState state = service.pair();

    assertThat(codesShown).containsExactly("CODE-1", "CODE-1", "CODE-1");
    assertThat(server.secretsSeen).containsOnly("secret-1");
    assertThat(state.playerId()).isEqualTo("player-1");
    assertThat(state.deviceToken()).isEqualTo("token-1");
    assertThat(state.serverUrl()).isEqualTo(server.url());
    assertThat(stateStore.load()).contains(state);
    assertThat(status.pairingCode()).isEqualTo("CODE-1");
  }

  @Test
  void persistsThePairingWhileWaitingAndClearsItOnceApproved() throws Exception {
    List<Optional<PendingPairing>> storedWhilePolling = new ArrayList<>();
    server.onPoll = () -> storedWhilePolling.add(pendingStore.load());
    server.pollReplies.add(FakePairingServer.pending());
    server.pollReplies.add(FakePairingServer.approved("token-1"));

    service.pair();

    assertThat(storedWhilePolling).hasSize(2);
    assertThat(storedWhilePolling)
        .allSatisfy(
            stored -> {
              assertThat(stored).isPresent();
              assertThat(stored.get().pairingId()).isEqualTo("pairing-1");
              assertThat(stored.get().secret()).isEqualTo("secret-1");
              assertThat(stored.get().code()).isEqualTo("CODE-1");
            });
    assertThat(pendingStore.load()).isEmpty();
  }

  @Test
  void asksForANewCodeWhenTheServerReportsItExpired() throws Exception {
    server.pollReplies.add(FakePairingServer.expired());
    server.pollReplies.add(FakePairingServer.approved("token-2"));

    PlayerState state = service.pair();

    assertThat(server.requests.get()).isEqualTo(2);
    assertThat(server.secretsSeen).containsExactly("secret-1", "secret-2");
    assertThat(state.deviceToken()).isEqualTo("token-2");
    assertThat(pendingStore.load()).isEmpty();
  }

  @Test
  void asksForANewCodeWhenTheServerDoesNotKnowThePairing() throws Exception {
    server.pollReplies.add(FakePairingServer.notFound());
    server.pollReplies.add(FakePairingServer.approved("token-8"));

    PlayerState state = service.pair();

    assertThat(server.requests.get()).isEqualTo(2);
    assertThat(server.secretsSeen).containsExactly("secret-1", "secret-2");
    assertThat(state.deviceToken()).isEqualTo("token-8");
  }

  @Test
  void asksForANewCodeWhenTheServerRejectsTheSecret() throws Exception {
    server.pollReplies.add(FakePairingServer.unauthorized());
    server.pollReplies.add(FakePairingServer.approved("token-9"));

    PlayerState state = service.pair();

    assertThat(server.requests.get()).isEqualTo(2);
    assertThat(state.deviceToken()).isEqualTo("token-9");
  }

  @Test
  void keepsTheSamePairingWhenTheLocalClockPassesItsExpiry() throws Exception {
    server.codeLifetime = Duration.ofSeconds(3);
    for (int i = 0; i < 8; i++) {
      server.pollReplies.add(FakePairingServer.pending());
    }
    server.pollReplies.add(FakePairingServer.approved("token-3"));

    PlayerState state = service.pair();

    assertThat(server.requests.get()).isEqualTo(1);
    assertThat(server.secretsSeen).containsOnly("secret-1");
    assertThat(state.deviceToken()).isEqualTo("token-3");
  }

  @Test
  void retriesForeverWithGrowingWaitsWhenTheServerFails() throws Exception {
    for (int i = 0; i < 8; i++) {
      server.requestReplies.add(FakePairingServer.failure());
    }
    server.pollReplies.add(FakePairingServer.approved("token-4"));

    PlayerState state = service.pair();

    List<Long> backoffs = sleeps.subList(0, 8);
    assertThat(backoffs).allSatisfy(delay -> assertThat(delay).isBetween(2000L, 60000L));
    assertThat(backoffs.get(7)).isGreaterThan(backoffs.get(0));
    assertThat(backoffs.get(7)).isGreaterThanOrEqualTo(40000L);
    assertThat(state.deviceToken()).isEqualTo("token-4");
  }

  @Test
  void keepsPollingTheSamePairingThroughALongNetworkOutageAfterTheClaim() throws Exception {
    for (int i = 0; i < 12; i++) {
      server.pollReplies.add(FakePairingServer.failure());
    }
    server.pollReplies.add(FakePairingServer.approved("token-5"));

    PlayerState state = service.pair();

    assertThat(server.requests.get()).isEqualTo(1);
    assertThat(server.secretsSeen).hasSize(13).containsOnly("secret-1");
    assertThat(state.deviceToken()).isEqualTo("token-5");
    assertThat(status.connectionError()).isEmpty();
  }

  @Test
  void keepsPollingTheSamePairingWhenApprovedWithoutToken() throws Exception {
    server.pollReplies.add(FakePairingServer.approved(null));
    server.pollReplies.add(FakePairingServer.approved("token-6"));

    PlayerState state = service.pair();

    assertThat(server.requests.get()).isEqualTo(1);
    assertThat(state.deviceToken()).isEqualTo("token-6");
  }

  @Test
  void resumesAPersistedPairingAfterARestartWithoutAskingForANewCode() throws Exception {
    pendingStore.save(
        new PendingPairing(
            server.url(),
            "pairing-77",
            "OLD-CODE",
            "secret-77",
            clock.instant().minus(Duration.ofMinutes(30)),
            1));
    server.pollReplies.add(FakePairingServer.approved("token-10"));

    PlayerState state = service.pair();

    assertThat(server.requests.get()).isZero();
    assertThat(server.secretsSeen).containsExactly("secret-77");
    assertThat(state.deviceToken()).isEqualTo("token-10");
    assertThat(pendingStore.load()).isEmpty();
  }

  @Test
  void showsTheStoredCodeWhileResumingAPersistedPairing() throws Exception {
    List<String> codesShown = new ArrayList<>();
    server.onPoll = () -> codesShown.add(status.pairingCode());
    pendingStore.save(
        new PendingPairing(
            server.url(),
            "pairing-77",
            "STORED-1",
            "secret-77",
            clock.instant().plusSeconds(600),
            1));
    server.pollReplies.add(FakePairingServer.approved("token-11"));

    service.pair();

    assertThat(codesShown).containsExactly("STORED-1");
  }

  @Test
  void asksForANewCodeWhenAPersistedPairingIsExpiredOnTheServer() throws Exception {
    pendingStore.save(
        new PendingPairing(
            server.url(),
            "pairing-77",
            "OLD-CODE",
            "secret-77",
            clock.instant().minusSeconds(5),
            1));
    server.pollReplies.add(FakePairingServer.expired());
    server.pollReplies.add(FakePairingServer.approved("token-12"));

    PlayerState state = service.pair();

    assertThat(server.requests.get()).isEqualTo(1);
    assertThat(server.secretsSeen).containsExactly("secret-77", "secret-1");
    assertThat(state.deviceToken()).isEqualTo("token-12");
  }

  @Test
  void discardsAPersistedPairingThatBelongsToAnotherFixedServer() throws Exception {
    pendingStore.save(
        new PendingPairing(
            "http://127.0.0.1:1", "pairing-77", "OLD-CODE", "secret-77", clock.instant(), 1));
    server.pollReplies.add(FakePairingServer.approved("token-13"));

    PlayerState state = service.pair();

    assertThat(server.requests.get()).isEqualTo(1);
    assertThat(server.secretsSeen).containsExactly("secret-1");
    assertThat(state.deviceToken()).isEqualTo("token-13");
  }

  @Test
  void discardPendingRemovesTheStoredPairing() {
    pendingStore.save(
        new PendingPairing(server.url(), "pairing-1", "CODE", "secret", clock.instant(), 3));

    service.discardPending();

    assertThat(pendingStore.load()).isEmpty();
  }

  @Test
  void startsInThePairingPhaseOnceTheCodeIsKnown() throws Exception {
    server.onPoll = () -> assertThat(status.phase()).isEqualTo(PlayerPhase.PAIRING);
    server.pollReplies.add(FakePairingServer.approved("token-7"));

    service.pair();

    assertThat(status.snapshot(0)).containsEntry("pairingCode", "CODE-1");
  }
}
