package es.cefa.tv.player;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Punto de entrada del receptor de CEFA TV. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class PlayerApplication {

  /**
   * Arranca la aplicación.
   *
   * @param args argumentos de línea de comandos
   */
  public static void main(String[] args) {
    SpringApplication.run(PlayerApplication.class, args);
  }
}
