package es.cefa.tv.player.status;

import es.cefa.tv.player.pairing.PlayerPhase;
import es.cefa.tv.player.shared.config.BuildInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Estado observable del reproductor, compartido por todos los componentes. */
@Component
public class PlayerStatus {

  private final Instant startedAt = Instant.now();
  private final String version;
  private final String build;
  private volatile String idleLogo = "CEFA";
  private volatile PlayerPhase phase = PlayerPhase.STARTING;
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
  private volatile String pairingCode = "";
  private volatile Instant pairingExpiresAt;
  private volatile String connectionError = "";
  private volatile Instant retryAt;

  /**
   * Crea el estado compartido.
   *
   * @param version versión del reproductor
   * @param build commit corto con el que se construyó la imagen
   */
  @Autowired
  public PlayerStatus(
      @Value("${info.app.version:dev}") String version,
      @Value("${info.app.build:unknown}") String build) {
    this.version = BuildInfo.version(version);
    this.build = BuildInfo.build(build);
  }

  /**
   * Crea el estado compartido sin información de build.
   *
   * @param version versión del reproductor
   */
  public PlayerStatus(String version) {
    this(version, null);
  }

  /**
   * Versión del reproductor.
   *
   * @return versión sin sufijo SNAPSHOT
   */
  public String version() {
    return version;
  }

  /**
   * Registra el logo vigente de la pantalla sin programación.
   *
   * @param idleLogo {@code CEFA} o {@code ALGONTEC}
   */
  public void idleLogo(String idleLogo) {
    this.idleLogo = idleLogo;
  }

  /**
   * Logo vigente de la pantalla sin programación.
   *
   * @return nombre del logo
   */
  public String idleLogo() {
    return idleLogo;
  }

  /**
   * Commit corto con el que se construyó la imagen.
   *
   * @return commit o {@code unknown}
   */
  public String build() {
    return build;
  }

  /**
   * Cambia la fase del ciclo de vida.
   *
   * @param phase nueva fase
   * @param message detalle legible
   */
  public void phase(PlayerPhase phase, String message) {
    this.phase = phase;
    this.message = message;
    if (phase != PlayerPhase.PAIRING) {
      this.pairingCode = "";
      this.pairingExpiresAt = null;
    }
    if (phase == PlayerPhase.ONLINE || phase == PlayerPhase.PAIRING) {
      this.connectionError = "";
      this.retryAt = null;
    }
  }

  /**
   * Registra el código de emparejamiento que se muestra en pantalla.
   *
   * @param code código con formato {@code ABC-123}
   * @param expiresAt instante en que caduca
   * @param serverUrl servidor al que se ha pedido
   */
  public void pairing(String code, Instant expiresAt, String serverUrl) {
    this.serverUrl = serverUrl;
    phase(PlayerPhase.PAIRING, "Esperando que el administrador introduzca el código " + code);
    this.pairingCode = code;
    this.pairingExpiresAt = expiresAt;
  }

  /**
   * Anota un fallo de comunicación y cuándo se volverá a intentar.
   *
   * @param error descripción del fallo
   * @param retryAt instante del siguiente intento
   */
  public void connectionFailure(String error, Instant retryAt) {
    this.connectionError = error == null ? "" : error;
    this.retryAt = retryAt;
  }

  /** Vuelve al estado sin emparejar tras una baja. */
  public void reset() {
    this.serverUrl = "";
    this.playerId = "";
    this.name = "";
    this.connected = false;
    this.downloadingVersion = 0;
    this.state = "IDLE";
    this.currentPlaylistId = null;
    this.currentAssetId = null;
    this.lastError = null;
    this.connectionError = "";
    this.retryAt = null;
    phase(PlayerPhase.STARTING, "");
  }

  /**
   * Registra los datos del servidor tras el emparejamiento.
   *
   * @param serverUrl URL del core
   * @param playerId identificador del reproductor
   * @param name nombre del reproductor
   */
  public void paired(String serverUrl, String playerId, String name) {
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
   * Código de emparejamiento vigente.
   *
   * @return código o cadena vacía
   */
  public String pairingCode() {
    return pairingCode;
  }

  /**
   * Caducidad del código de emparejamiento vigente.
   *
   * @return instante o nulo
   */
  public Instant pairingExpiresAt() {
    return pairingExpiresAt;
  }

  /**
   * Servidor conocido.
   *
   * @return URL base o cadena vacía
   */
  public String serverUrl() {
    return serverUrl;
  }

  /**
   * Último fallo de comunicación con el servidor.
   *
   * @return descripción o cadena vacía
   */
  public String connectionError() {
    return connectionError;
  }

  /**
   * Instante del siguiente reintento de comunicación.
   *
   * @return instante o nulo
   */
  public Instant retryAt() {
    return retryAt;
  }

  /**
   * Fase actual.
   *
   * @return fase
   */
  public PlayerPhase phase() {
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
    map.put("version", version);
    map.put("build", build);
    map.put("idleLogo", idleLogo);
    map.put("phase", phase.name());
    map.put("message", message);
    map.put("pairingCode", pairingCode);
    map.put("pairingExpiresAt", pairingExpiresAt == null ? null : pairingExpiresAt.toString());
    map.put("connectionError", connectionError);
    map.put(
        "retryInSec",
        retryAt == null ? null : Math.max(0, Duration.between(Instant.now(), retryAt).toSeconds()));
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
