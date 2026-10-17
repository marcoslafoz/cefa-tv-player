package es.cefa.tv.player.display;

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
 * Persistencia de la salida de vídeo preferida en {@code display.json}, para que sobreviva a los
 * reinicios aunque el servidor no esté disponible.
 */
@Component
public class DisplayPreferenceStore {

  private final Path file;
  private final ObjectMapper mapper;

  /**
   * Crea el almacén.
   *
   * @param properties configuración del reproductor
   * @param mapper serializador JSON
   */
  public DisplayPreferenceStore(PlayerProperties properties, ObjectMapper mapper) {
    this.file = properties.dataDir().resolve("display.json");
    this.mapper = mapper;
  }

  /**
   * Lee la salida preferida guardada.
   *
   * @return nombre de la salida, o vacío si no hay preferencia o el fichero está dañado
   */
  public Optional<String> load() {
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(
              mapper.readValue(file.toFile(), Preference.class).preferredOutput())
          .filter(output -> !output.isBlank());
    } catch (JsonProcessingException e) {
      clear();
      return Optional.empty();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Guarda la salida preferida; un valor nulo borra la preferencia.
   *
   * @param output nombre de la salida o {@code null} para la selección automática
   */
  public void save(String output) {
    if (output == null) {
      clear();
      return;
    }
    try {
      AtomicFiles.write(file, mapper.writeValueAsBytes(new Preference(output)));
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
  private record Preference(String preferredOutput) {}
}
