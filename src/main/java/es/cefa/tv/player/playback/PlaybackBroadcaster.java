package es.cefa.tv.player.playback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Difunde por SSE las órdenes del motor a la página local de reproducción. */
@Slf4j
@Component
public class PlaybackBroadcaster {

  private final ObjectMapper mapper;
  private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
  private volatile String lastEvent;

  /**
   * Crea el difusor.
   *
   * @param mapper serializador JSON
   */
  public PlaybackBroadcaster(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * Suscribe una página y le reenvía la última orden para que retome la reproducción.
   *
   * @return emisor SSE de la suscripción
   */
  public SseEmitter subscribe() {
    SseEmitter emitter = new SseEmitter(0L);
    emitters.add(emitter);
    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> emitters.remove(emitter));
    emitter.onError(error -> emitters.remove(emitter));
    String replay = lastEvent;
    if (replay != null) {
      send(emitter, replay);
    }
    return emitter;
  }

  /**
   * Publica una orden a todas las páginas suscritas.
   *
   * @param event orden serializable
   */
  public void publish(Object event) {
    try {
      String json = mapper.writeValueAsString(event);
      lastEvent = json;
      emitters.forEach(emitter -> send(emitter, json));
    } catch (JsonProcessingException e) {
      log.warn("Orden de reproducción no serializable: {}", e.getMessage());
    }
  }

  /** Mantiene vivas las conexiones SSE. */
  @Scheduled(fixedDelay = 15000)
  public void keepAlive() {
    emitters.forEach(
        emitter -> {
          try {
            emitter.send(SseEmitter.event().comment("ping"));
          } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
          }
        });
  }

  private void send(SseEmitter emitter, String json) {
    try {
      emitter.send(SseEmitter.event().data(json));
    } catch (IOException | IllegalStateException e) {
      emitters.remove(emitter);
    }
  }
}
