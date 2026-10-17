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
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Persistencia del logo de la pantalla sin programación en {@code idle-logo.json}, junto a la
 * salida de vídeo preferida, para que sobreviva a los reinicios aunque el servidor no esté.
 */
@Component
public class IdleLogoStore {

  private final Path file;
  private final ObjectMapper mapper;

  /**
   * Crea el almacén.
   *
   * @param properties configuración del reproductor
   * @param mapper serializador JSON
   */
  public IdleLogoStore(PlayerProperties properties, ObjectMapper mapper) {
    this.file = properties.dataDir().resolve("idle-logo.json");
    this.mapper = mapper;
  }

  /**
   * Lee el logo guardado.
   *
   * @return logo guardado, o vacío si no hay ninguno o el fichero está dañado
   */
  public Optional<IdleLogo> load() {
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(mapper.readValue(file.toFile(), Stored.class).idleLogo())
          .map(IdleLogo::parse);
    } catch (JsonProcessingException e) {
      clear();
      return Optional.empty();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Guarda el logo.
   *
   * @param logo logo a persistir
   */
  public void save(IdleLogo logo) {
    try {
      AtomicFiles.write(file, mapper.writeValueAsBytes(new Stored(logo.name())));
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
  private record Stored(String idleLogo) {}
}
