package es.cefa.tv.player.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Utilidades de escritura atómica de ficheros. */
public final class AtomicFiles {

  private AtomicFiles() {}

  /**
   * Escribe el contenido en un temporal y lo mueve de forma atómica al destino.
   *
   * @param target fichero destino
   * @param content contenido a escribir
   * @throws IOException si falla la escritura
   */
  public static void write(Path target, byte[] content) throws IOException {
    Files.createDirectories(target.getParent());
    Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
    Files.write(tmp, content);
    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
  }
}
