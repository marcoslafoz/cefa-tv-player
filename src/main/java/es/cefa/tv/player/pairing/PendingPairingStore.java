package es.cefa.tv.player.pairing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.shared.config.PlayerProperties;
import es.cefa.tv.player.storage.AtomicFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Persistencia de {@code pairing.json}, el emparejamiento en curso, en el directorio de datos. */
@Component
public class PendingPairingStore {

  private final Path file;
  private final ObjectMapper mapper;

  /**
   * Crea el almacén.
   *
   * @param properties configuración del reproductor
   * @param mapper serializador JSON
   */
  public PendingPairingStore(PlayerProperties properties, ObjectMapper mapper) {
    this.file = properties.dataDir().resolve("pairing.json");
    this.mapper = mapper;
  }

  /**
   * Lee el emparejamiento en curso.
   *
   * @return emparejamiento guardado, o vacío si no hay ninguno o el fichero está dañado
   */
  public Optional<PendingPairing> load() {
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      PendingPairing pending = mapper.readValue(file.toFile(), PendingPairing.class);
      boolean complete =
          pending.serverUrl() != null && pending.pairingId() != null && pending.secret() != null;
      return complete ? Optional.of(pending) : Optional.empty();
    } catch (JsonProcessingException e) {
      clear();
      return Optional.empty();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Guarda el emparejamiento en curso.
   *
   * @param pending emparejamiento a persistir
   */
  public void save(PendingPairing pending) {
    try {
      AtomicFiles.write(file, mapper.writeValueAsBytes(pending));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Borra el emparejamiento en curso. */
  public void clear() {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
