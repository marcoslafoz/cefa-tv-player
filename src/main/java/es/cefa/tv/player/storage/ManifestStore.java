package es.cefa.tv.player.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.shared.config.PlayerProperties;
import es.cefa.tv.player.shared.model.Manifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Guarda en disco y mantiene en memoria el manifest aplicado. */
@Slf4j
@Component
public class ManifestStore {

  private final Path file;
  private final ObjectMapper mapper;
  private volatile Manifest current;

  /**
   * Carga el manifest guardado, si existe.
   *
   * @param properties configuración del reproductor
   * @param mapper serializador JSON
   */
  public ManifestStore(PlayerProperties properties, ObjectMapper mapper) {
    this.file = properties.dataDir().resolve("manifest.json");
    this.mapper = mapper;
    if (Files.isRegularFile(file)) {
      try {
        this.current = mapper.readValue(file.toFile(), Manifest.class);
        log.info("Manifest local cargado, versión {}", current.version());
      } catch (IOException e) {
        log.warn("Manifest local ilegible, se ignora: {}", e.getMessage());
      }
    }
  }

  /**
   * Manifest aplicado actualmente.
   *
   * @return manifest, o vacío si todavía no hay ninguno
   */
  public Optional<Manifest> current() {
    return Optional.ofNullable(current);
  }

  /**
   * Versión aplicada.
   *
   * @return versión del manifest o 0 si no hay
   */
  public long appliedVersion() {
    Manifest manifest = current;
    return manifest == null ? 0 : manifest.version();
  }

  /**
   * Persiste y publica un manifest nuevo.
   *
   * @param manifest manifest cuyos ficheros ya están descargados
   * @throws IOException si falla la escritura
   */
  public void save(Manifest manifest) throws IOException {
    AtomicFiles.write(file, mapper.writeValueAsBytes(manifest));
    this.current = manifest;
  }

  /** Olvida el manifest, en memoria y en disco. */
  public void clear() {
    this.current = null;
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("No se pudo borrar el manifest: {}", e.getMessage());
    }
  }
}
