package es.cefa.tv.player.playback;

import es.cefa.tv.player.status.PlayerStatus;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Logo vigente de la pantalla sin programación. Lo fija el servidor, se guarda en disco y, al
 * cambiar, avisa a la página local por SSE para que lo cambie sin recargarse.
 */
@Slf4j
@Service
public class IdleLogoService {

  private final IdleLogoStore store;
  private final PlaybackBroadcaster broadcaster;
  private final PlayerStatus status;
  private volatile IdleLogo current = IdleLogo.CEFA;

  /**
   * Crea el servicio.
   *
   * @param store logo persistido
   * @param broadcaster difusión a la página local
   * @param status estado compartido donde se informa el logo
   */
  public IdleLogoService(
      IdleLogoStore store, PlaybackBroadcaster broadcaster, PlayerStatus status) {
    this.store = store;
    this.broadcaster = broadcaster;
    this.status = status;
  }

  /** Carga el logo guardado; sin nada guardado se usa el de CEFA. */
  @PostConstruct
  public void load() {
    current = store.load().orElse(IdleLogo.CEFA);
    status.idleLogo(current.name());
  }

  /**
   * Fija el logo pedido por el servidor.
   *
   * @param raw {@code CEFA} o {@code ALGONTEC}; nulo o desconocido equivale a {@code CEFA}
   */
  public synchronized void set(String raw) {
    IdleLogo requested = IdleLogo.parse(raw);
    if (requested == current) {
      return;
    }
    current = requested;
    store.save(requested);
    status.idleLogo(requested.name());
    log.info("Logo de la pantalla sin programación: {}", requested);
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "CONFIG");
    event.put("idleLogo", requested.name());
    broadcaster.publishTransient(event);
  }

  /**
   * Logo vigente.
   *
   * @return nombre del logo, nunca nulo
   */
  public String current() {
    return current.name();
  }
}
