package es.cefa.tv.player.sync;

import es.cefa.tv.player.shared.model.Manifest;
import es.cefa.tv.player.shared.model.Manifest.ManifestFile;
import es.cefa.tv.player.status.PlayerStatus;
import es.cefa.tv.player.storage.FileStore;
import es.cefa.tv.player.storage.ManifestStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Mantiene el reproductor al día con el servidor: descarga el manifest y solo los ficheros que
 * faltan, y lo aplica cuando todo está en disco. Si el servidor no responde se sigue con lo local.
 */
@Slf4j
@Service
public class SyncService {

  static final long SMALL_FILE_LIMIT = 20L * 1024 * 1024;
  static final int BATCH_MAX_FILES = 50;
  static final long BATCH_MAX_BYTES = 100L * 1024 * 1024;
  private static final int LARGE_FILE_ATTEMPTS = 3;

  private final long smallFileLimit;
  private final ServerApi api;
  private final ManifestStore manifests;
  private final FileStore files;
  private final PlayerStatus status;
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "sync");
            thread.setDaemon(true);
            return thread;
          });
  private final Object gate = new Object();
  private boolean running;
  private boolean queued;
  private volatile boolean recheck;
  private volatile long knownDesired;

  /**
   * Crea el servicio.
   *
   * @param api cliente del servidor
   * @param manifests almacén del manifest aplicado
   * @param files almacén de ficheros
   * @param status estado compartido
   */
  @Autowired
  public SyncService(ServerApi api, ManifestStore manifests, FileStore files, PlayerStatus status) {
    this(api, manifests, files, status, SMALL_FILE_LIMIT);
  }

  SyncService(
      ServerApi api,
      ManifestStore manifests,
      FileStore files,
      PlayerStatus status,
      long smallFileLimit) {
    this.smallFileLimit = smallFileLimit;
    this.api = api;
    this.manifests = manifests;
    this.files = files;
    this.status = status;
  }

  /**
   * Registra la versión que el servidor desea y lanza la sincronización si difiere de la aplicada.
   *
   * @param desiredVersion versión deseada informada en el último latido
   */
  public void onDesiredVersion(long desiredVersion) {
    this.knownDesired = desiredVersion;
    if (desiredVersion != manifests.appliedVersion()
        && desiredVersion != status.downloadingVersion()) {
      requestSync(false);
    }
  }

  /**
   * Solicita una sincronización asíncrona; si ya hay una en curso se vuelve a ejecutar al acabar.
   *
   * @param recheckManifest si hay que comprobar durante la descarga si existe una versión nueva
   */
  public void requestSync(boolean recheckManifest) {
    if (recheckManifest) {
      recheck = true;
    }
    synchronized (gate) {
      if (running) {
        queued = true;
        return;
      }
      running = true;
    }
    executor.submit(this::loop);
  }

  private void loop() {
    boolean again;
    do {
      try {
        syncOnce();
      } catch (RuntimeException e) {
        log.warn("Sincronización fallida: {}", e.getMessage());
      }
      synchronized (gate) {
        again = queued;
        queued = false;
        if (!again) {
          running = false;
        }
      }
    } while (again);
  }

  /**
   * Ejecuta un ciclo completo de sincronización en el hilo llamante.
   *
   * @return {@code true} si se aplicó una versión nueva
   */
  public boolean syncOnce() {
    long applied = manifests.appliedVersion();
    Optional<Manifest> fetched;
    try {
      fetched = api.fetchManifest(applied);
    } catch (RuntimeException e) {
      status.connected(false);
      log.info("Sin acceso al manifest, se sigue con la versión {}: {}", applied, e.getMessage());
      return false;
    }
    status.connected(true);
    if (fetched.isEmpty()) {
      return false;
    }
    Manifest target = fetched.get();
    status.downloadingVersion(target.version());
    try {
      download(target);
      manifests.save(target);
      status.downloadingVersion(0);
      status.error(null);
      files.retainOnly(
          target.filesOrEmpty().stream().map(ManifestFile::sha256).collect(Collectors.toSet()));
      log.info("Versión {} aplicada", target.version());
      acknowledge(target.version(), "APPLIED", null);
      return true;
    } catch (SyncAbortedException e) {
      status.downloadingVersion(0);
      log.info("Descarga de la versión {} sustituida por una más reciente", target.version());
      synchronized (gate) {
        queued = true;
      }
      return false;
    } catch (IOException | RuntimeException e) {
      status.downloadingVersion(0);
      status.error(e.getMessage());
      log.warn("No se pudo aplicar la versión {}: {}", target.version(), e.getMessage());
      acknowledge(target.version(), "FAILED", e.getMessage());
      return false;
    }
  }

  private void acknowledge(long version, String result, String error) {
    try {
      api.ack(version, result, error);
    } catch (RuntimeException e) {
      log.debug("No se pudo confirmar la versión {}: {}", version, e.getMessage());
    }
  }

  private void download(Manifest target) throws IOException {
    List<ManifestFile> missing = new ArrayList<>(files.missing(target));
    List<ManifestFile> large = new ArrayList<>();
    List<ManifestFile> batch = new ArrayList<>();
    long batchBytes = 0;
    for (ManifestFile file : missing) {
      if (file.sizeBytes() > smallFileLimit) {
        large.add(file);
        continue;
      }
      batch.add(file);
      batchBytes += file.sizeBytes();
      if (batch.size() >= BATCH_MAX_FILES || batchBytes >= BATCH_MAX_BYTES) {
        large.addAll(downloadBatch(target, batch));
        batch = new ArrayList<>();
        batchBytes = 0;
      }
    }
    if (!batch.isEmpty()) {
      large.addAll(downloadBatch(target, batch));
    }
    for (ManifestFile file : large) {
      downloadLarge(target, file);
    }
  }

  private List<ManifestFile> downloadBatch(Manifest target, List<ManifestFile> batch) {
    if (superseded(target.version())) {
      throw new SyncAbortedException();
    }
    Set<String> requested = batch.stream().map(ManifestFile::sha256).collect(Collectors.toSet());
    api.downloadZip(
        List.copyOf(requested),
        (name, content) -> {
          if (requested.contains(name)) {
            files.store(name, content);
          }
        },
        () -> superseded(target.version()));
    return batch.stream().filter(file -> !files.has(file.sha256())).toList();
  }

  private void downloadLarge(Manifest target, ManifestFile file) throws IOException {
    IOException last = null;
    for (int attempt = 1; attempt <= LARGE_FILE_ATTEMPTS; attempt++) {
      if (superseded(target.version())) {
        throw new SyncAbortedException();
      }
      try {
        api.downloadRanged(
            file.sha256(), files.partPath(file.sha256()), () -> superseded(target.version()));
        files.commitPart(file.sha256());
        return;
      } catch (IOException e) {
        last = e;
      }
    }
    throw last;
  }

  private boolean superseded(long version) {
    if (knownDesired > version) {
      return true;
    }
    if (recheck) {
      recheck = false;
      try {
        return api.fetchManifest(version).isPresent();
      } catch (RuntimeException e) {
        return false;
      }
    }
    return false;
  }
}
