package es.cefa.tv.player.discovery;

import es.cefa.tv.player.pairing.Backoff;
import es.cefa.tv.player.shared.config.PlayerProperties;
import es.cefa.tv.player.shared.time.Sleeper;
import es.cefa.tv.player.status.PlayerStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Localiza el servidor sin rendirse nunca: usa la URL fijada a mano o descubre el servidor por
 * broadcast, esperando la red y reintentando con espera creciente.
 */
@Slf4j
@Component
public class ServerLocator {

  private static final Duration DISCOVERY_WAIT = Duration.ofSeconds(3);

  private final PlayerProperties properties;
  private final DiscoveryClient discovery;
  private final NetworkProbe network;
  private final PlayerStatus status;
  private final Sleeper sleeper;
  private final Clock clock;

  /**
   * Crea el localizador.
   *
   * @param properties configuración del reproductor
   * @param discovery cliente de descubrimiento
   * @param network comprobación de la red
   * @param status estado compartido
   * @param sleeper espera interrumpible
   * @param clock reloj
   */
  public ServerLocator(
      PlayerProperties properties,
      DiscoveryClient discovery,
      NetworkProbe network,
      PlayerStatus status,
      Sleeper sleeper,
      Clock clock) {
    this.properties = properties;
    this.discovery = discovery;
    this.network = network;
    this.status = status;
    this.sleeper = sleeper;
    this.clock = clock;
  }

  /**
   * Devuelve la URL base del core, esperando todo lo necesario.
   *
   * @return URL base del core
   * @throws InterruptedException si el hilo se interrumpe mientras espera
   */
  public String locate() throws InterruptedException {
    if (properties.hasServer()) {
      return DiscoveryClient.stripTrailingSlash(properties.server());
    }
    Backoff backoff = new Backoff();
    while (true) {
      Optional<String> found = discoverOnce();
      if (found.isPresent()) {
        return found.get();
      }
      long delay = backoff.nextDelayMs();
      status.connectionFailure(
          network.isAvailable()
              ? "No se ha encontrado el servidor en la red"
              : "Esperando a que la red esté disponible",
          clock.instant().plusMillis(delay));
      log.info("Servidor no encontrado, nuevo intento en {} ms", delay);
      sleeper.sleep(delay);
    }
  }

  /**
   * Hace un único intento de descubrimiento por broadcast.
   *
   * @return URL base del core, o vacío si no hay red o nadie responde
   */
  public Optional<String> discoverOnce() {
    if (!network.isAvailable()) {
      return Optional.empty();
    }
    return discovery.discoverOnLan(1, DISCOVERY_WAIT);
  }

  /**
   * Indica si la URL del servidor se fijó a mano y, por tanto, no se redescubre.
   *
   * @return {@code true} si {@code CEFA_TV_SERVER} tiene valor
   */
  public boolean isFixed() {
    return properties.hasServer();
  }
}
