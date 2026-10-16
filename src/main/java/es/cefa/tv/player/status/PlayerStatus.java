package es.cefa.tv.player.status;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Estado observable del reproductor, compartido por todos los componentes. */
@Component
public class PlayerStatus {

  private final Instant startedAt = Instant.now();
  private volatile String phase = "STARTING";
  private volatile String message = "";
  private volatile String serverUrl = "";
  private volatile String playerId = "";
  private volatile String name = "";
  private volatile boolean connected;
  private volatile long downloadingVersion;
  private volatile String state = "IDLE";
  private volatile String currentPlaylistId;
  private volatile String currentAssetId;
  private volatile String lastError;

  /**
   * Cambia la fase del ciclo de vida.
   *
   * @param phase fase (WAITING_KEY, DISCOVERING, ENROLLING, RUNNING...)
   * @param message detalle legible
   */
  public void phase(String phase, String message) {
    this.phase = phase;
    this.message = message;
  }

  /**
   * Registra los datos del servidor tras el alta.
   *
   * @param serverUrl URL del core
   * @param playerId identificador del reproductor
   * @param name nombre del reproductor
   */
  public void enrolled(String serverUrl, String playerId, String name) {
    this.serverUrl = serverUrl;
    this.playerId = playerId;
    this.name = name;
  }

  /**
   * Marca si hay comunicación con el servidor.
   *
   * @param connected resultado del último contacto
   */
  public void connected(boolean connected) {
    this.connected = connected;
  }

  /**
   * Indica la versión que se está descargando.
   *
   * @param version versión o 0 si no hay descarga
   */
  public void downloadingVersion(long version) {
    this.downloadingVersion = version;
  }

  /**
   * Registra lo que se está reproduciendo.
   *
   * @param state IDLE, PLAYING o ERROR
   * @param playlistId playlist actual
   * @param assetId medio actual
   */
  public void playing(String state, String playlistId, String assetId) {
    this.state = state;
    this.currentPlaylistId = playlistId;
    this.currentAssetId = assetId;
  }

  /**
   * Registra el último error.
   *
   * @param error descripción o {@code null} para limpiarlo
   */
  public void error(String error) {
    this.lastError = error;
  }

  /**
   * Estado general informado en los latidos.
   *
   * @return DOWNLOADING mientras se descarga; si no, el estado de reproducción
   */
  public String heartbeatState() {
    if (lastError != null && "ERROR".equals(state)) {
      return "ERROR";
    }
    return downloadingVersion > 0 ? "DOWNLOADING" : state;
  }

  /**
   * Versión en descarga.
   *
   * @return versión o 0
   */
  public long downloadingVersion() {
    return downloadingVersion;
  }

  /**
   * Playlist actual.
   *
   * @return identificador o nulo
   */
  public String currentPlaylistId() {
    return currentPlaylistId;
  }

  /**
   * Medio actual.
   *
   * @return identificador o nulo
   */
  public String currentAssetId() {
    return currentAssetId;
  }

  /**
   * Último error.
   *
   * @return descripción o nulo
   */
  public String lastError() {
    return lastError;
  }

  /**
   * Segundos desde el arranque.
   *
   * @return tiempo activo
   */
  public long uptimeSec() {
    return Duration.between(startedAt, Instant.now()).toSeconds();
  }

  /**
   * Detalle legible de la fase actual.
   *
   * @return mensaje
   */
  public String message() {
    return message;
  }

  /**
   * Fase actual.
   *
   * @return fase
   */
  public String phase() {
    return phase;
  }

  /**
   * Resumen para la página de estado.
   *
   * @param appliedVersion versión aplicada
   * @return mapa serializable
   */
  public Map<String, Object> snapshot(long appliedVersion) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("phase", phase);
    map.put("message", message);
    map.put("serverUrl", serverUrl);
    map.put("playerId", playerId);
    map.put("name", name);
    map.put("connected", connected);
    map.put("appliedVersion", appliedVersion);
    map.put("downloadingVersion", downloadingVersion);
    map.put("state", heartbeatState());
    map.put("currentPlaylistId", currentPlaylistId);
    map.put("currentAssetId", currentAssetId);
    map.put("lastError", lastError);
    map.put("uptimeSec", uptimeSec());
    return map;
  }
}
