package es.cefa.tv.player.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.cefa.tv.player.shared.config.PlayerProperties;
import es.cefa.tv.player.status.PlayerStatus;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ServerLocatorTest {

  private final List<Long> sleeps = new ArrayList<>();
  private final PlayerStatus status = new PlayerStatus("test");
  private final DiscoveryClient discovery = mock(DiscoveryClient.class);

  private ServerLocator locator(String server, NetworkProbe network) {
    PlayerProperties properties =
        new PlayerProperties(Path.of("."), server, "", 47808, 30, null, "headless", null);
    return new ServerLocator(
        properties, discovery, network, status, sleeps::add, Clock.systemUTC());
  }

  @Test
  void aFixedServerIsReturnedWithoutDiscovery() throws Exception {
    ServerLocator locator = locator("http://fijo:8080/", new NetworkProbe());
    assertThat(locator.locate()).isEqualTo("http://fijo:8080");
    assertThat(locator.isFixed()).isTrue();
    verify(discovery, never()).discoverOnLan(anyInt(), any());
  }

  @Test
  void waitsForTheNetworkAndKeepsRetryingUntilTheServerAnswers() throws Exception {
    AtomicInteger checks = new AtomicInteger();
    NetworkProbe network =
        new NetworkProbe() {
          @Override
          public boolean isAvailable() {
            return checks.incrementAndGet() > 4;
          }
        };
    when(discovery.discoverOnLan(anyInt(), any(Duration.class)))
        .thenReturn(Optional.empty(), Optional.of("http://192.168.1.10:8080"));

    String found = locator("", network).locate();

    assertThat(found).isEqualTo("http://192.168.1.10:8080");
    assertThat(sleeps).hasSizeGreaterThanOrEqualTo(3);
    assertThat(sleeps).allSatisfy(delay -> assertThat(delay).isBetween(2000L, 60000L));
    assertThat(status.retryAt()).isNotNull();
  }
}
