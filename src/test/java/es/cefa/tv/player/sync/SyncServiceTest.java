package es.cefa.tv.player.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.cefa.tv.player.shared.config.PlayerProperties;
import es.cefa.tv.player.shared.model.Manifest;
import es.cefa.tv.player.shared.model.Manifest.ManifestFile;
import es.cefa.tv.player.status.PlayerStatus;
import es.cefa.tv.player.storage.FileStore;
import es.cefa.tv.player.storage.ManifestStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyncServiceTest {

  private static final long SMALL_LIMIT = 1000;

  @TempDir Path dir;
  private FakeServer server;
  private ObjectMapper mapper;
  private FileStore files;
  private ManifestStore manifests;
  private PlayerStatus status;
  private SyncService sync;
  private final byte[] small = "imagen pequena".getBytes();
  private final byte[] large = new byte[5000];

  static String sha(byte[] data) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
  }

  @BeforeEach
  void setUp() throws Exception {
    new Random(7).nextBytes(large);
    mapper = new ObjectMapper().findAndRegisterModules();
    PlayerProperties properties = new PlayerProperties(dir, "", "", "", 47808, 30, null);
    files = new FileStore(properties);
    manifests = new ManifestStore(properties, mapper);
    status = new PlayerStatus();
    server = new FakeServer();
    server.files.put(sha(small), small);
    server.files.put(sha(large), large);
    server.manifest = manifest(1);
    ServerApi api = new ServerApi(mapper);
    api.configure(server.url(), "token");
    sync = new SyncService(api, manifests, files, status, SMALL_LIMIT);
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  private Manifest manifest(long version) throws Exception {
    return new Manifest(
        version,
        null,
        "player",
        "Europe/Madrid",
        null,
        List.of(
            new ManifestFile(sha(small), small.length, "image/jpeg", "a.jpg"),
            new ManifestFile(sha(large), large.length, "video/mp4", "b.mp4")),
        Map.of(),
        Map.of(),
        List.of());
  }

  @Test
  void downloadsSmallFilesInAZipAndLargeOnesDirectlyThenIgnoresUnchangedManifest()
      throws Exception {
    assertThat(sync.syncOnce()).isTrue();
    assertThat(files.has(sha(small))).isTrue();
    assertThat(files.has(sha(large))).isTrue();
    assertThat(server.zipRequests).containsExactly(sha(small));
    assertThat(manifests.appliedVersion()).isEqualTo(1);
    assertThat(server.acks).containsExactly("1:APPLIED");
    assertThat(sync.syncOnce()).isFalse();
    assertThat(server.acks).hasSize(1);
  }

  @Test
  void resumesAPartialDownloadWithARangeRequest() throws Exception {
    Files.write(files.partPath(sha(large)), Arrays.copyOf(large, 2000));
    assertThat(sync.syncOnce()).isTrue();
    assertThat(server.rangeHeaders).containsExactly("bytes=2000-");
    assertThat(Files.readAllBytes(files.path(sha(large)))).isEqualTo(large);
  }

  @Test
  void keepsPlayingTheLocalVersionWhenTheServerIsDown() throws Exception {
    assertThat(sync.syncOnce()).isTrue();
    server.stop();
    server.manifest = manifest(2);
    assertThat(sync.syncOnce()).isFalse();
    assertThat(manifests.appliedVersion()).isEqualTo(1);
    assertThat(manifests.current()).isPresent();
    assertThat(files.has(sha(small))).isTrue();
  }

  @Test
  void aNewVersionDownloadsOnlyWhatIsMissingAndGarbageCollectsTheRest() throws Exception {
    assertThat(sync.syncOnce()).isTrue();
    byte[] extra = "nuevo".getBytes();
    server.files.put(sha(extra), extra);
    server.manifest =
        new Manifest(
            2,
            null,
            "player",
            "Europe/Madrid",
            null,
            List.of(
                new ManifestFile(sha(small), small.length, "image/jpeg", "a.jpg"),
                new ManifestFile(sha(extra), extra.length, "image/jpeg", "c.jpg")),
            Map.of(),
            Map.of(),
            List.of());
    server.zipRequests.clear();
    assertThat(sync.syncOnce()).isTrue();
    assertThat(server.zipRequests).containsExactly(sha(extra));
    assertThat(files.has(sha(large))).isFalse();
    assertThat(manifests.appliedVersion()).isEqualTo(2);
  }

  @Test
  void rejectsCorruptedFilesAndReportsFailure() throws Exception {
    server.files.put(sha(small), "contenido corrupto".getBytes());
    assertThat(sync.syncOnce()).isFalse();
    assertThat(manifests.appliedVersion()).isZero();
    assertThat(server.acks).containsExactly("1:FAILED");
    assertThat(status.lastError()).isNotNull();
    assertThat(files.has(sha(small))).isFalse();
  }
}
