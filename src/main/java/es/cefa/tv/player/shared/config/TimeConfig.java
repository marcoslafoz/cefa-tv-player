package es.cefa.tv.player.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj del sistema como bean, para poder sustituirlo en las pruebas. */
@Configuration
public class TimeConfig {

  /**
   * Reloj de la zona UTC.
   *
   * @return reloj del sistema
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
