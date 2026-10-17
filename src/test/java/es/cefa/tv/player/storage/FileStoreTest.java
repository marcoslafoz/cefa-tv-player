package es.cefa.tv.player.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.cefa.tv.player.shared.config.PlayerProperties;
import es.cefa.tv.player.shared.model.Manifest;
import es.cefa.tv.player.shared.model.Manifest.ManifestFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileStoreTest {

  @TempDir Path dir;
  private FileStore store;

  static String sha(byte[] data) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
  }

  @BeforeEach
  void setUp() throws IOException {
    store = new FileStore(new PlayerProperties(dir, "", "", 47808, 30, null, "headless", null));
  }

  @Test
  void storesVerifiedContentAndReportsMissingFiles() throws Exception {
    byte[] one = "uno".getBytes(StandardCharsets.UTF_8);
    byte[] two = "dos".getBytes(StandardCharsets.UTF_8);
    store.store(sha(one), new ByteArrayInputStream(one));
    Manifest manifest =
        new Manifest(
            1,
            null,
            "p",
            null,
            null,
            List.of(
                new ManifestFile(sha(one), 3, "text/plain", "uno"),
                new ManifestFile(sha(two), 3, "text/plain", "dos")),
            Map.of(),
            Map.of(),
            List.of());
    assertThat(store.missing(manifest)).extracting(ManifestFile::name).containsExactly("dos");
  }

  @Test
  void rejectsContentWithWrongHashAndLeavesNothingBehind() throws Exception {
    byte[] data = "contenido".getBytes(StandardCharsets.UTF_8);
    String wrong = sha("otro".getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> store.store(wrong, new ByteArrayInputStream(data)))
        .isInstanceOf(IOException.class);
    assertThat(store.has(wrong)).isFalse();
    assertThat(Files.exists(store.partPath(wrong))).isFalse();
  }

  @Test
  void retainOnlyRemovesFilesNotInTheKeepSet() throws Exception {
    byte[] a = "a".getBytes(StandardCharsets.UTF_8);
    byte[] b = "b".getBytes(StandardCharsets.UTF_8);
    store.store(sha(a), new ByteArrayInputStream(a));
    store.store(sha(b), new ByteArrayInputStream(b));
    int removed = store.retainOnly(Set.of(sha(a)));
    assertThat(removed).isEqualTo(1);
    assertThat(store.has(sha(a))).isTrue();
    assertThat(store.has(sha(b))).isFalse();
  }
}
