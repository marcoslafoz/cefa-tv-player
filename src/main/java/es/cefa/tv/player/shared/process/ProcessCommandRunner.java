package es.cefa.tv.player.shared.process;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Implementación de {@link CommandRunner} sobre {@link ProcessBuilder}. */
@Slf4j
@Component
public class ProcessCommandRunner implements CommandRunner {

  private static final long TIMEOUT_SECONDS = 8;

  @Override
  public Optional<String> run(List<String> command) {
    try {
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      byte[] output = process.getInputStream().readAllBytes();
      if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        log.debug("Tiempo agotado ejecutando {}", command);
        return Optional.empty();
      }
      if (process.exitValue() != 0) {
        log.debug("{} terminó con código {}", command, process.exitValue());
        return Optional.empty();
      }
      return Optional.of(new String(output, StandardCharsets.UTF_8));
    } catch (IOException e) {
      log.debug("No se pudo ejecutar {}: {}", command, e.getMessage());
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }
}
