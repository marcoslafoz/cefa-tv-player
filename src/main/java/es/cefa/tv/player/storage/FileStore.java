package es.cefa.tv.player.storage;

import es.cefa.tv.player.shared.config.PlayerProperties;
import es.cefa.tv.player.shared.model.Manifest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Almacén en disco de los ficheros direccionados por sha256 ({@code files/<sha256>}). */
@Slf4j
@Component
public class FileStore {

  private static final String PART_SUFFIX = ".part";

  private final Path filesDir;

  /**
   * Crea el almacén dentro del directorio de datos.
   *
   * @param properties configuración del reproductor
   * @throws IOException si no se puede crear el directorio
   */
  public FileStore(PlayerProperties properties) throws IOException {
    this.filesDir = properties.dataDir().resolve("files");
    Files.createDirectories(filesDir);
  }

  /**
   * Indica si el fichero completo y verificado está en disco.
   *
   * @param sha256 huella del fichero
   * @return {@code true} si existe
   */
  public boolean has(String sha256) {
    return Files.isRegularFile(path(sha256));
  }

  /**
   * Ruta del fichero completo.
   *
   * @param sha256 huella del fichero
   * @return ruta dentro del almacén
   */
  public Path path(String sha256) {
    return filesDir.resolve(sha256);
  }

  /**
   * Ruta del fichero parcial usado para reanudar descargas.
   *
   * @param sha256 huella del fichero
   * @return ruta del parcial
   */
  public Path partPath(String sha256) {
    return filesDir.resolve(sha256 + PART_SUFFIX);
  }

  /**
   * Bytes ya descargados de un fichero parcial.
   *
   * @param sha256 huella del fichero
   * @return tamaño del parcial o 0 si no existe
   * @throws IOException si falla el acceso al disco
   */
  public long partialSize(String sha256) throws IOException {
    Path part = partPath(sha256);
    return Files.exists(part) ? Files.size(part) : 0;
  }

  /**
   * Calcula los ficheros del manifest que aún no están en disco.
   *
   * @param manifest manifest objetivo
   * @return ficheros que faltan
   */
  public List<Manifest.ManifestFile> missing(Manifest manifest) {
    return manifest.filesOrEmpty().stream().filter(file -> !has(file.sha256())).toList();
  }

  /**
   * Guarda un flujo verificando su huella; si no coincide lo descarta.
   *
   * @param sha256 huella esperada
   * @param content contenido
   * @throws IOException si falla la escritura o la huella no coincide
   */
  public void store(String sha256, InputStream content) throws IOException {
    Path part = partPath(sha256);
    try (OutputStream out =
        Files.newOutputStream(
            part, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
      content.transferTo(out);
    }
    commitPart(sha256);
  }

  /**
   * Verifica el parcial completo y lo publica como fichero definitivo.
   *
   * @param sha256 huella esperada
   * @throws IOException si la huella no coincide, en cuyo caso se borra el parcial
   */
  public void commitPart(String sha256) throws IOException {
    Path part = partPath(sha256);
    String actual = digest(part);
    if (!actual.equalsIgnoreCase(sha256)) {
      Files.deleteIfExists(part);
      throw new IOException("La huella del fichero no coincide: esperada " + sha256);
    }
    Files.move(
        part, path(sha256), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
  }

  /**
   * Elimina los ficheros que no están en el conjunto indicado.
   *
   * @param keep huellas que se conservan
   * @return número de ficheros borrados
   * @throws IOException si falla el acceso al disco
   */
  public int retainOnly(Set<String> keep) throws IOException {
    int removed = 0;
    try (Stream<Path> stream = Files.list(filesDir)) {
      for (Path file : stream.collect(Collectors.toList())) {
        String name = file.getFileName().toString();
        String sha = name.endsWith(PART_SUFFIX) ? name.substring(0, name.length() - 5) : name;
        if (!keep.contains(sha) && !name.endsWith(".tmp")) {
          Files.deleteIfExists(file);
          removed++;
        }
      }
    }
    if (removed > 0) {
      log.info("Recolector de ficheros: {} eliminados", removed);
    }
    return removed;
  }

  /** Elimina todos los ficheros del almacén. */
  public void clear() {
    try {
      retainOnly(Set.of());
    } catch (IOException e) {
      log.warn("No se pudo vaciar el almacén: {}", e.getMessage());
    }
  }

  /**
   * Megabytes libres en el disco del almacén.
   *
   * @return espacio libre en MB
   */
  public long freeMegabytes() {
    return filesDir.toFile().getUsableSpace() / (1024 * 1024);
  }

  /**
   * Calcula el sha256 de un fichero.
   *
   * @param file fichero a leer
   * @return huella en hexadecimal
   * @throws IOException si no se puede leer
   */
  public static String digest(Path file) throws IOException {
    try (InputStream in = Files.newInputStream(file)) {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      try (DigestInputStream dis = new DigestInputStream(in, md)) {
        dis.transferTo(OutputStream.nullOutputStream());
      }
      return HexFormat.of().formatHex(md.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
