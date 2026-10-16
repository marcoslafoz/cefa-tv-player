package es.cefa.tv.player.enrollment;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.shared.config.PlayerProperties;
import es.cefa.tv.player.storage.AtomicFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Persistencia de {@code state.json} en el directorio de datos. */
@Component
public class StateStore {

  private final Path file;
  private final ObjectMapper mapper;

  /**
   * Crea el almacén de estado.
   *
   * @param properties configuración del reproductor
   * @param mapper serializador JSON
   */
  public StateStore(PlayerProperties properties, ObjectMapper mapper) {
    this.file = properties.dataDir().resolve("state.json");
    this.mapper = mapper;
  }

  /**
   * Lee el estado guardado.
   *
   * @return estado, o vacío si el reproductor no está dado de alta
   */
  public Optional<PlayerState> load() {
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      return Optional.of(mapper.readValue(file.toFile(), PlayerState.class));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Guarda el estado.
   *
   * @param state estado a persistir
   */
  public void save(PlayerState state) {
    try {
      AtomicFiles.write(file, mapper.writeValueAsBytes(state));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Borra el estado, devolviendo el reproductor al estado sin alta. */
  public void clear() {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
