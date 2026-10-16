package es.cefa.tv.player.shell;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import es.cefa.tv.player.shared.config.PlayerProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Lanza {@code /bin/bash} en el contenedor o, en modo host, en el anfitrión con {@code nsenter}.
 */
@Component
public class PtyShellLauncher implements ShellLauncher {

  private static final List<String> CONTAINER_COMMAND = List.of("/bin/bash", "-l");
  private static final List<String> HOST_COMMAND =
      List.of("nsenter", "-t", "1", "-m", "-u", "-n", "-i", "/bin/bash", "-l");

  private final PlayerProperties properties;

  /**
   * Crea el lanzador.
   *
   * @param properties configuración del reproductor
   */
  public PtyShellLauncher(PlayerProperties properties) {
    this.properties = properties;
  }

  @Override
  public PtyProcess launch(int cols, int rows) throws IOException {
    Map<String, String> environment = new HashMap<>(System.getenv());
    environment.put("TERM", "xterm-256color");
    environment.putIfAbsent("LANG", "C.UTF-8");
    List<String> command = properties.shell().hostMode() ? HOST_COMMAND : CONTAINER_COMMAND;
    Path home = Path.of(environment.getOrDefault("HOME", "/root"));
    return new PtyProcessBuilder(command.toArray(String[]::new))
        .setEnvironment(environment)
        .setDirectory(Files.isDirectory(home) ? home.toString() : "/")
        .setInitialColumns(cols)
        .setInitialRows(rows)
        .setConsole(false)
        .start();
  }
}
