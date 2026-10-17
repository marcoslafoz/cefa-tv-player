package es.cefa.tv.player.pairing;

import es.cefa.tv.player.discovery.ServerLocator;
import es.cefa.tv.player.shared.time.Sleeper;
import es.cefa.tv.player.status.PlayerStatus;
import es.cefa.tv.player.sync.ServerApi;
import es.cefa.tv.player.sync.ServerException;
import java.time.Clock;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Máquina de estados del emparejamiento: localiza el servidor, pide un código, lo sondea hasta que
 * el administrador lo aprueba y no se rinde nunca. Solo pide un código nuevo si el servidor dice
 * que el actual caducó o no lo conoce; un corte de red, una respuesta perdida o un reinicio retoman
 * el mismo emparejamiento para no dejar reproductores huérfanos en el servidor.
 */
@Slf4j
@Component
public class PairingService {

  private static final int DEFAULT_POLL_SEC = 3;
  private static final int RELOCATE_AFTER_FAILURES = 5;

  private final ServerApi api;
  private final ServerLocator locator;
  private final PairingRequestFactory requests;
  private final PlayerStatus status;
  private final StateStore stateStore;
  private final PendingPairingStore pendingStore;
  private final Sleeper sleeper;
  private final Clock clock;

  /**
   * Crea el servicio.
   *
   * @param api cliente del servidor
   * @param locator localizador del servidor
   * @param requests fábrica de la petición de emparejamiento
   * @param status estado compartido
   * @param stateStore estado persistente
   * @param pendingStore emparejamiento en curso persistente
   * @param sleeper espera interrumpible
   * @param clock reloj
   */
  public PairingService(
      ServerApi api,
      ServerLocator locator,
      PairingRequestFactory requests,
      PlayerStatus status,
      StateStore stateStore,
      PendingPairingStore pendingStore,
      Sleeper sleeper,
      Clock clock) {
    this.api = api;
    this.locator = locator;
    this.requests = requests;
    this.status = status;
    this.stateStore = stateStore;
    this.pendingStore = pendingStore;
    this.sleeper = sleeper;
    this.clock = clock;
  }

  /**
   * Bloquea hasta conseguir un emparejamiento aprobado y lo guarda.
   *
   * @return estado persistido del reproductor ya emparejado
   * @throws InterruptedException si el hilo se interrumpe
   */
  public PlayerState pair() throws InterruptedException {
    Backoff backoff = new Backoff();
    while (true) {
      PendingPairing pending = resumePending();
      if (pending == null) {
        status.phase(PlayerPhase.SEARCHING_SERVER, "Buscando el servidor de CEFA TV");
        String server = locator.locate();
        try {
          pending = PendingPairing.of(server, api.requestPairing(server, requests.create()));
          backoff.reset();
        } catch (RuntimeException e) {
          failAndWait(e, backoff);
          continue;
        }
        pendingStore.save(pending);
        log.info("Código de emparejamiento {} pedido a {}", pending.code(), server);
      }
      Optional<PlayerState> approved = await(pending);
      if (approved.isPresent()) {
        stateStore.save(approved.get());
        pendingStore.clear();
        log.info("Emparejado como {} ({})", approved.get().name(), approved.get().playerId());
        return approved.get();
      }
      pendingStore.clear();
    }
  }

  /** Descarta el emparejamiento en curso guardado, porque ya existe un alta definitiva. */
  public void discardPending() {
    pendingStore.clear();
  }

  private PendingPairing resumePending() throws InterruptedException {
    Optional<PendingPairing> stored = pendingStore.load();
    if (stored.isEmpty()) {
      return null;
    }
    if (locator.isFixed() && !stored.get().serverUrl().equals(locator.locate())) {
      pendingStore.clear();
      return null;
    }
    log.info(
        "Se retoma el emparejamiento {} con el código {}",
        stored.get().pairingId(),
        stored.get().code());
    return stored.get();
  }

  private Optional<PlayerState> await(PendingPairing initial) throws InterruptedException {
    PendingPairing pending = initial;
    showCode(pending);
    long pollMs =
        (pending.pollIntervalSec() > 0 ? pending.pollIntervalSec() : DEFAULT_POLL_SEC) * 1000L;
    Backoff errors = new Backoff();
    while (true) {
      sleeper.sleep(pollMs);
      try {
        PairingPoll poll =
            api.pairingStatus(pending.serverUrl(), pending.pairingId(), pending.secret());
        if (errors.attempts() > 0) {
          errors.reset();
          showCode(pending);
        }
        if (poll.approved()) {
          if (poll.deviceToken() == null || poll.deviceToken().isBlank()) {
            log.warn("Emparejamiento aprobado sin token, se sigue sondeando");
            continue;
          }
          return Optional.of(
              new PlayerState(
                  pending.serverUrl(),
                  poll.playerId(),
                  poll.deviceToken(),
                  poll.name(),
                  poll.pollIntervalSec()));
        }
        if (poll.expired()) {
          log.info("El servidor da por caducado el código {}", pending.code());
          return Optional.empty();
        }
      } catch (ServerException e) {
        if (isUnknownPairing(e)) {
          log.info(
              "El servidor no reconoce el emparejamiento {}: {}", pending.code(), e.getMessage());
          return Optional.empty();
        }
        failAndWait(e, errors);
        pending = relocate(pending, errors);
      } catch (RuntimeException e) {
        failAndWait(e, errors);
        pending = relocate(pending, errors);
      }
    }
  }

  private boolean isUnknownPairing(ServerException e) {
    int code = e.getStatus();
    return code == 404 || code == 410 || code == 401;
  }

  private PendingPairing relocate(PendingPairing pending, Backoff errors)
      throws InterruptedException {
    if (errors.attempts() < RELOCATE_AFTER_FAILURES || locator.isFixed()) {
      return pending;
    }
    String server = locator.locate();
    errors.reset();
    PendingPairing moved =
        server.equals(pending.serverUrl())
            ? pending
            : new PendingPairing(
                server,
                pending.pairingId(),
                pending.code(),
                pending.secret(),
                pending.expiresAt(),
                pending.pollIntervalSec());
    if (moved != pending) {
      pendingStore.save(moved);
      log.info("El servidor ha cambiado de dirección a {}", server);
    }
    showCode(moved);
    return moved;
  }

  private void showCode(PendingPairing pending) {
    status.pairing(pending.code(), pending.expiresAt(), pending.serverUrl());
  }

  private void failAndWait(RuntimeException error, Backoff backoff) throws InterruptedException {
    long delay = backoff.nextDelayMs();
    log.warn("Fallo en el emparejamiento ({}), nuevo intento en {} ms", error.getMessage(), delay);
    status.connectionFailure(error.getMessage(), clock.instant().plusMillis(delay));
    sleeper.sleep(delay);
  }
}
