# Arquitectura de CEFA TV

CEFA TV es un sistema de señalización digital (similar a Xibo o Aracast) formado por dos repositorios:

- `cefa-tv-server` — emisor: backend Spring Boot (`cefa-tv-core`), frontend Next.js (`cefa-tv-app`), PostgreSQL y MinIO.
- `cefa-tv-player` — receptor: aplicación Spring Boot en Docker que se instala con un script, reproduce en pantalla completa y se controla en remoto.

Este documento es el contrato entre ambos. Existe una copia idéntica en los dos repositorios (`docs/llm/context/architecture.md`): cualquier cambio se aplica en las dos.

## Decisiones

- **Modelo pull con estado deseado**: el servidor mantiene por reproductor un `desiredVersion` y el reproductor informa de su `appliedVersion`. Si un reproductor está sin conexión, la programación queda pendiente y se entrega sola cuando vuelve (el reproductor sondea cada 30 s y mantiene un WebSocket para avisos instantáneos). El reproductor solo hace conexiones salientes: funciona detrás de NAT y sin abrir puertos.
- **Transporte HTTP(S), no FTP**: es más simple, atraviesa proxies, permite autenticación por token y reanudación con `Range`. La programación viaja como `manifest` JSON comprimido con gzip y los ficheros como zip por lote o descarga directa con `Range` (reanudable), siempre por diferencias: el reproductor solo pide los ficheros (identificados por `sha256`) que no tiene.
- **Reproducción offline**: el reproductor guarda en disco el manifest y los ficheros y decide qué reproducir con su reloj local, sin depender del servidor.
- **Alta por clave**: el administrador genera una clave de alta (`XXXX-XXXX-XXXX`) en la interfaz. El script de instalación del reproductor solo pide esa clave. El reproductor encuentra el servidor por descubrimiento en la red local (broadcast UDP) y, si no lo encuentra, usa `CEFA_TV_SERVER` (URL base del core) si se ha indicado.
- **Control remoto vía CDP**: el reproductor lanza Chromium en modo kiosco con `--remote-debugging-port` y lo gobierna por Chrome DevTools Protocol: `Page.startScreencast` para enviar la imagen y `Input.dispatchMouseEvent` / `Input.dispatchKeyEvent` para inyectar ratón y teclado. Sirve para cualquier contenido, incluidas webs y HTML.
- **PDF como imágenes**: al subir un PDF el servidor lo rasteriza con PDFBox a una imagen por página; el reproductor trata el PDF como una secuencia de imágenes.
- **Sin zonas ni capas en el MVP**: cada pantalla reproduce una playlist a pantalla completa. Los layouts multizona quedan fuera del alcance inicial.

## Componentes del servidor

```
cefa-tv-server/
├── cefa-tv-core/        Spring Boot 3.4, Java 21, paquete es.cefa.tv.core
│   ├── compose.yml      postgres + minio + core + discovery
│   └── discovery/       sidecar de descubrimiento (socat, red host)
├── cefa-tv-app/         Next.js 16, React 19, pnpm
│   └── compose.yml      app + nginx (proxy a /core-api, WebSocket y subidas grandes)
└── Makefile             orquestador (delega en core y app)
```

Puertos por defecto: `4000` interfaz web (nginx), `8080` API del core (los reproductores se conectan aquí), `9000` API de MinIO, `9001` consola de MinIO, `47808/udp` descubrimiento.

Variables del core (`cefa-tv-core/.env`): `DB_URL`, `DB_USER`, `DB_PASSWORD`, `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET` (por defecto `cefa-tv-media`), `JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_PASSWORD` (crean el primer administrador si no hay usuarios), `PUBLIC_URL` (URL del core visible por los reproductores, opcional).

## Modelo de datos (Flyway, PostgreSQL)

- `users(id uuid, username, password_hash, role ADMIN|EDITOR, created_at)`
- `media_assets(id uuid, name, type IMAGE|VIDEO|PDF|WEB|HTML, mime, size_bytes, sha256, object_key, thumbnail_key, duration_ms, width, height, page_count, url, refresh_sec, pages jsonb, status READY|PROCESSING|FAILED, created_at, created_by)`. `pages` (solo PDF) es una lista de `{index, objectKey, sha256, sizeBytes}`. `url` es para `WEB`.
- `playlists(id uuid, name, description, hidden boolean, created_at, updated_at)` y `playlist_items(id uuid, playlist_id, position, asset_id, duration_sec, fit COVER|CONTAIN, muted boolean, settings jsonb)`. Una playlist `hidden` es la que se crea automáticamente al arrastrar un medio suelto al timeline.
- `player_groups(id uuid, name)`
- `players(id uuid, name, group_id, status ACTIVE|DISABLED, device_token_hash, hostname, ip, hardware jsonb, resolution, orientation, player_version, timezone, last_seen_at, desired_version bigint, applied_version bigint, state, current_playlist_id, current_asset_id, last_error)`. `online` se deriva de `last_seen_at` (menos de 90 s) o de tener WebSocket abierto.
- `enrollment_keys(id uuid, code, name, group_id, max_uses, uses, expires_at, revoked, created_at)`
- `schedules(id uuid, name, playlist_id, priority int, start_date, end_date, start_time, end_time, days_of_week int[] 1=lunes..7=domingo, all_day boolean, enabled boolean)` y `schedule_targets(schedule_id, player_id null, group_id null)`.
- `player_manifests(player_id, version bigint, content_hash, manifest jsonb, created_at)`

Regla de resolución: en un instante dado, el reproductor reproduce la playlist del `schedule` activo (habilitado, fecha dentro de `start_date..end_date`, día de semana incluido, hora dentro de `start_time..end_time`) con mayor `priority`; en empate, el que empezó más tarde. Si no hay ninguno activo, reproduce `fallbackPlaylistId` y, si tampoco existe, muestra la pantalla de reposo.

Cualquier cambio que afecte a un reproductor (medio, playlist, programación, grupo) recalcula su manifest (con un retardo de 2 s para agrupar cambios). Si el `content_hash` cambia, se inserta una versión nueva, se incrementa `desired_version` y, si el reproductor está conectado por WebSocket, se le envía `SYNC`.

## Manifest

```json
{
  "version": 12,
  "generatedAt": "2026-10-16T10:00:00Z",
  "playerId": "uuid",
  "timezone": "Europe/Madrid",
  "fallbackPlaylistId": "uuid-or-null",
  "files": [{ "sha256": "...", "sizeBytes": 123, "mime": "video/mp4", "name": "intro.mp4" }],
  "assets": {
    "assetId": {
      "id": "assetId", "type": "IMAGE|VIDEO|PDF|WEB|HTML", "name": "intro",
      "sha256": "... (IMAGE|VIDEO|HTML)", "mime": "...", "durationMs": 15000,
      "url": "https://... (WEB)", "refreshSec": 300,
      "pages": [{ "index": 0, "sha256": "..." }]
    }
  },
  "playlists": {
    "playlistId": {
      "id": "playlistId", "name": "Entrada",
      "items": [{ "assetId": "...", "durationSec": 10, "fit": "COVER", "muted": true }]
    }
  },
  "schedules": [{
    "id": "uuid", "playlistId": "uuid", "priority": 10,
    "startDate": "2026-10-01", "endDate": null,
    "startTime": "08:00", "endTime": "20:00",
    "daysOfWeek": [1, 2, 3, 4, 5], "allDay": false, "enabled": true
  }]
}
```

`files` es la lista de blobs necesarios (los ficheros de medios y las páginas de PDF), identificados por `sha256`; el reproductor los guarda como `files/<sha256>`. Las duraciones de un PDF en la playlist son por página: `durationSec` se aplica a cada página.

## API de administración (`/api/v1`, cookie `ctv_session` con JWT)

Errores con el cuerpo `{ "status": 404, "errorCode": "NOT_FOUND", "message": "...", "details": { "campo": "motivo" } }` (mismo contrato que Hermes).

| Método y ruta | Descripción |
|---|---|
| `POST /auth/login` `{username,password}` | Inicia sesión (cookie `httpOnly`), devuelve `{id,username,role}`. |
| `POST /auth/logout`, `GET /auth/me` | Cierra sesión / usuario actual. |
| `GET /public/info` | Sin autenticación: `{service:"cefa-tv-server", name, version}`. Lo usa el reproductor para validar el descubrimiento. |
| `GET /dashboard` | Totales: reproductores (online, offline, pendientes de entrega), medios, playlists, almacenamiento. |
| `GET /media?type=&q=&page=&size=` | Página `{content, totalElements, page, size}`. |
| `POST /media` (multipart `files[]`) | Sube imágenes, vídeos, PDF y HTML. Devuelve la lista de medios creados. |
| `POST /media/web` `{name,url,refreshSec}` | Crea un medio de tipo web. |
| `GET/PATCH/DELETE /media/{id}` | Detalle, renombrar / duración por defecto, borrar (409 si lo usa una playlist). |
| `GET /media/{id}/content`, `GET /media/{id}/thumbnail` | Contenido y miniatura. |
| `GET/POST /playlists`, `GET/PUT/DELETE /playlists/{id}` | `PUT` reemplaza la lista de items `{assetId,durationSec,fit,muted}` en orden. Incluye `totalDurationSec`. |
| `GET/POST /groups`, `PUT/DELETE /groups/{id}` | Grupos de reproductores. |
| `GET /players`, `GET /players/{id}` | Incluyen `online`, `desiredVersion`, `appliedVersion`, `pending` (`desired > applied`), `lastSeenAt`, `state`. |
| `PATCH /players/{id}` | `name`, `groupId`, `orientation`, `status`. |
| `DELETE /players/{id}` | Da de baja y envía `UNENROLL` si está conectado. |
| `POST /players/{id}/resync`, `/restart`, `/reload` | Fuerza recálculo y entrega / reinicia el reproductor / recarga la vista. |
| `GET /players/{id}/deliveries` | Historial de versiones y su estado (`QUEUED`, `DOWNLOADING`, `APPLIED`, `FAILED`). |
| `GET/POST /enrollment-keys`, `DELETE /enrollment-keys/{id}` | Crear `{name,groupId?,maxUses?,expiresInHours?}` devuelve el `code`. Borrar revoca. |
| `GET/POST /schedules`, `GET/PUT/DELETE /schedules/{id}` | Crear admite `playlistId` o `assetId` (con `durationSec`) para el arrastre directo de un medio. `targets: {playerIds[], groupIds[]}`. |
| `GET /schedules/timeline?playerId=&from=&to=` | Ocurrencias expandidas por día para pintar el timeline. |

## API del reproductor (`/player-api`, cabecera `Authorization: Bearer <deviceToken>`)

| Método y ruta | Descripción |
|---|---|
| `POST /player-api/enroll` (sin token) `{enrollmentKey,hostname,hardware,resolution,playerVersion,timezone}` | Valida la clave (no revocada, no caducada, usos disponibles), crea el reproductor y devuelve `{playerId,deviceToken,name,pollIntervalSec}`. Una clave inválida devuelve 401 con `errorCode: "INVALID_ENROLLMENT_KEY"`. |
| `GET /player-api/manifest` | Manifest actual, con `Content-Encoding: gzip` y `ETag: "<version>"` (acepta `If-None-Match`, 304). |
| `POST /player-api/files` `{sha256:[...]}` | Devuelve un `application/zip` con una entrada por `sha256` pedido (DEFLATED para texto, STORED para formatos ya comprimidos). |
| `GET /player-api/files/{sha256}` | Descarga directa con soporte de `Range` y `ETag`; para ficheros grandes. |
| `POST /player-api/heartbeat` `{appliedVersion,downloadingVersion,state,currentPlaylistId,currentAssetId,diskFreeMb,uptimeSec,ip,error}` | Actualiza el estado; devuelve `{desiredVersion, commands:[{type}]}`. `commands` pendientes: `RELOAD`, `RESTART`, `UNENROLL`. |
| `POST /player-api/ack` `{version,status:APPLIED|FAILED,error}` | Confirma la aplicación de una versión. |

## WebSocket

`/ws/player?token=<deviceToken>` (reproductor) y `/ws/remote/{playerId}` (navegador, cookie de sesión). Mensajes de texto JSON con campo `type`; los fotogramas son mensajes binarios con los bytes de un JPEG.

Servidor a reproductor: `SYNC` (hay una versión nueva), `REMOTE_START {fps, quality, maxWidth}`, `REMOTE_STOP`, `INPUT`, `RELOAD`, `RESTART`, `UNENROLL`.
Reproductor a servidor: `HELLO {appliedVersion}` (primer mensaje), binarios JPEG mientras haya sesión remota, `STATE {state, currentPlaylistId, currentAssetId}`.
Navegador a servidor: `START`, `STOP` e `INPUT`; el servidor los retransmite al reproductor y reenvía al navegador los fotogramas y un `STATE {online}`.

`INPUT` (coordenadas normalizadas 0..1 sobre el fotograma):

```json
{ "type": "INPUT", "kind": "MOUSE_MOVE|MOUSE_DOWN|MOUSE_UP|MOUSE_WHEEL|KEY_DOWN|KEY_UP|TEXT",
  "x": 0.5, "y": 0.5, "button": "left|middle|right",
  "deltaX": 0, "deltaY": 120, "key": "Enter", "code": "Enter", "text": "hola", "modifiers": 0 }
```

`modifiers` es la máscara de CDP (1 Alt, 2 Ctrl, 4 Meta, 8 Shift). Solo hay una sesión remota por reproductor a la vez; los fotogramas solo se envían mientras haya `REMOTE_START` activo.

## Descubrimiento

El sidecar `cefa-tv-discovery` (socat en red host, UDP `47808`) responde a la petición `CEFATV_DISCOVER 1` con una línea JSON `{"service":"cefa-tv","version":1,"apiUrl":"http://<ip-del-servidor>:8080"}`, calculando la IP con `ip route get <ip del cliente>`. El reproductor envía el broadcast a `255.255.255.255:47808` y a la dirección de broadcast de cada interfaz, espera 3 s, reintenta con espera creciente y valida la respuesta con `GET {apiUrl}/api/v1/public/info`. El contenedor del reproductor usa red host para que el broadcast salga a la LAN.

## Reproductor

```
cefa-tv-player/        Spring Boot 3.4, Java 21, paquete es.cefa.tv.player
├── src/
├── docker/            entrypoint.sh y ficheros del contenedor
├── install.sh         instalador (pide la clave de alta)
├── Dockerfile
└── compose.yml
```

Estado persistente en el volumen `/data` (`state.json` con servidor y token, `manifest.json`, `files/<sha256>`). Dominios del código: `enrollment`, `discovery`, `sync`, `storage`, `playback`, `browser` (lanzador de Chromium y cliente CDP), `remote` (WebSocket y entrada), `status`, `shared`.

Reproducción: un motor Java evalúa el manifest con el reloj local, elige la playlist activa y ordena a la página local `/player` (servida por el propio reproductor, fondo negro, sin parpadeos) qué mostrar mediante SSE: imagen, vídeo, página de PDF. Para medios `WEB` el motor navega Chromium a la URL por CDP (no usa iframe, porque muchas webs lo bloquean) y vuelve a `/player` al terminar; los `HTML` se sirven localmente desde `files/`.

Variables: `CEFA_TV_ENROLLMENT_KEY`, `CEFA_TV_SERVER` (opcional), `CEFA_TV_PLAYER_NAME` (opcional), `DISPLAY_MODE` (`host`, `xorg`, `headless`).

Modos de pantalla del contenedor: `host` usa el servidor X del anfitrión (monta `/tmp/.X11-unix`), `xorg` arranca su propio Xorg a pantalla completa (contenedor privilegiado, equipos dedicados) y `headless` usa Xvfb (pruebas y control remoto sin pantalla física).
