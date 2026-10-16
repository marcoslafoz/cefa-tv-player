package es.cefa.tv.player.shared.process;

import java.util.List;
import java.util.Optional;

/** Ejecuta programas del sistema y devuelve su salida estándar. */
public interface CommandRunner {

  /**
   * Ejecuta un programa y espera a que termine.
   *
   * @param command programa y argumentos
   * @return salida estándar si el programa terminó con éxito; vacío si no existe, falla o agota el
   *     tiempo de espera
   */
  Optional<String> run(List<String> command);
}
