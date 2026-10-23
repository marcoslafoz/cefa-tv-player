package es.cefa.tv.player.playback;

import es.cefa.tv.player.status.PlayerStatus;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Volumen de los vídeos del reproductor. Lo fija el servidor (0 a 100, 100 por defecto), se
 * guarda en disco y, al cambiar, avisa a la página local por SSE para aplicarlo sin recargarla. No
 * toca el volumen del sistema: solo escala los vídeos que no están marcados como {@code muted}.
 */
@Slf4j
@Service
public class VolumeService {

  /** Volumen por defecto, también cuando el servidor no lo envía. */
  public static final int DEFAULT = 100;

  private final VolumeStore store;
  private final PlaybackBroadcaster broadcaster;
  private final PlayerStatus status;
  private volatile int current = DEFAULT;

  /**
   * Crea el servicio.
   *
   * @param store volumen persistido
   * @param broadcaster difusión a la página local
   * @param status estado compartido donde se informa el volumen
   */
  public VolumeService(VolumeStore store, PlaybackBroadcaster broadcaster, PlayerStatus status) {
    this.store = store;
    this.broadcaster = broadcaster;
    this.status = status;
  }

  /** Carga el volumen guardado; sin nada guardado se usa 100. */
  @PostConstruct
  public void load() {
    current = store.load().orElse(DEFAULT);
    status.volume(current);
  }

  /**
   * Fija el volumen pedido por el servidor.
   *
   * @param requested volumen de 0 a 100 (se acota a ese rango); nulo equivale a 100
   */
  public synchronized void set(Integer requested) {
    int volume = requested == null ? DEFAULT : clamp(requested);
    if (volume == current) {
      return;
    }
    current = volume;
    store.save(volume);
    status.volume(volume);
    log.info("Volumen de los vídeos: {}", volume);
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "CONFIG");
    event.put("volume", volume);
    broadcaster.publishTransient(event);
  }

  /**
   * Volumen vigente.
   *
   * @return volumen de 0 a 100
   */
  public int current() {
    return current;
  }

  static int clamp(int volume) {
    return Math.max(0, Math.min(100, volume));
  }
}
