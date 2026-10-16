package es.cefa.tv.player.shell;

import com.pty4j.PtyProcess;
import java.io.IOException;

/** Crea el proceso de la shell conectado a un pseudoterminal. */
public interface ShellLauncher {

  /**
   * Lanza una shell interactiva.
   *
   * @param cols columnas iniciales del terminal
   * @param rows filas iniciales del terminal
   * @return proceso con pseudoterminal
   * @throws IOException si no se puede lanzar
   */
  PtyProcess launch(int cols, int rows) throws IOException;
}
