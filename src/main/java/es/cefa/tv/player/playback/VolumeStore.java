package es.cefa.tv.player.playback;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.shared.config.PlayerProperties;
import es.cefa.tv.player.storage.AtomicFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;
import org.springframework.stereotype.Component;

/**
 * Persistencia del volumen de los vídeos en {@code volume.json}, junto a la salida de vídeo
 * preferida, para que sobreviva a los reinicios aunque el servidor no esté.
 */
@Component
public class VolumeStore {

  private final Path file;
  private final ObjectMapper mapper;

  /**
   * Crea el almacén.
   *
   * @param properties configuración del reproductor
   * @param mapper serializador JSON
   */
  public VolumeStore(PlayerProperties properties, ObjectMapper mapper) {
    this.file = properties.dataDir().resolve("volume.json");
    this.mapper = mapper;
  }

  /**
   * Lee el volumen guardado.
   *
   * @return volumen de 0 a 100, o vacío si no hay ninguno o el fichero está dañado
   */
  public OptionalInt load() {
    if (!Files.isRegularFile(file)) {
      return OptionalInt.empty();
    }
    try {
      Integer volume = mapper.readValue(file.toFile(), Stored.class).volume();
      return volume == null ? OptionalInt.empty() : OptionalInt.of(VolumeService.clamp(volume));
    } catch (JsonProcessingException e) {
      clear();
      return OptionalInt.empty();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Guarda el volumen.
   *
   * @param volume volumen de 0 a 100
   */
  public void save(int volume) {
    try {
      AtomicFiles.write(file, mapper.writeValueAsBytes(new Stored(volume)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void clear() {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Stored(Integer volume) {}
}
