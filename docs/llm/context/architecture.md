# Arquitectura de CEFA TV

CEFA TV es un sistema de señalización digital (similar a Xibo o Aracast) formado por dos repositorios:

- `cefa-tv-server` — emisor: backend Spring Boot (`cefa-tv-core`), frontend Next.js (`cefa-tv-app`), PostgreSQL y Garage (almacenamiento S3).
- `cefa-tv-player` — receptor: aplicación Spring Boot en Docker que se instala con un script, reproduce en pantalla completa y se controla en remoto.

Este documento es el contrato entre ambos. Existe una copia idéntica en los dos repositorios (`docs/llm/context/architecture.md`): cualquier cambio se aplica en las dos.

## Decisiones

- **Modelo pull con estado deseado**: el servidor mantiene por reproductor un `desiredVersion` y el reproductor informa de su `appliedVersion`. Si un reproductor está sin conexión, la programación queda pendiente y se entrega sola cuando vuelve (el reproductor sondea cada 30 s y mantiene un WebSocket para avisos instantáneos). El reproductor solo hace conexiones salientes: funciona detrás de NAT y sin abrir puertos.
- **Transporte HTTP(S), no FTP**: es más simple, atraviesa proxies, permite autenticación por token y reanudación con `Range`. La programación viaja como `manifest` JSON comprimido con gzip y los ficheros como zip por lote o descarga directa con `Range` (reanudable), siempre por diferencias: el reproductor solo pide los ficheros (identificados por `sha256`) que no tiene.
- **Reproducción offline**: el reproductor guarda en disco el manifest y los ficheros y decide qué reproducir con su reloj local, sin depender del servidor.
- **Emparejamiento por código**: al arrancar sin estar emparejado, el reproductor muestra en pantalla un código de 6 caracteres (`ABC-123`) y lo imprime el instalador. El administrador lo introduce en el servidor y el reproductor queda emparejado. No hay claves previas que generar ni copiar. El reproductor encuentra el servidor por descubrimiento en la red local (broadcast UDP) y, si no lo encuentra, usa `CEFA_TV_SERVER`.
- **Control remoto vía CDP**: el reproductor lanza Chromium en modo kiosco con `--remote-debugging-port` y lo gobierna por Chrome DevTools Protocol: `Page.startScreencast` para enviar la imagen y `Input.dispatchMouseEvent` / `Input.dispatchKeyEvent` / `Input.insertText` para inyectar ratón y teclado. Sirve para cualquier contenido, incluidas webs y HTML.
- **PDF como imágenes**: al subir un PDF el servidor lo rasteriza con PDFBox a una imagen por página; el reproductor trata el PDF como una secuencia de imágenes.
- **Sin zonas ni capas en el MVP**: cada pantalla reproduce una playlist a pantalla completa. Los layouts multizona quedan fuera del alcance inicial.
- **Sin roles**: los permisos y los reproductores accesibles se definen en grupos de usuarios; un usuario obtiene la unión de los de sus grupos.

## Componentes del servidor

```
cefa-tv-server/
├── cefa-tv-core/        Spring Boot 3.4, Java 21, paquete es.cefa.tv.core
│   ├── compose.yml      postgres + garage + core + discovery
│   └── discovery/       sidecar de descubrimiento (socat, red host)
├── cefa-tv-app/         Next.js 16, React 19, pnpm, i18n es/en/pl
│   └── compose.yml      app + nginx (proxy a /core-api, WebSocket y subidas grandes)
└── Makefile             orquestador (delega en core y app)
```

Puertos por defecto: `4000` interfaz web (nginx), `8080` API del core (los reproductores se conectan aquí), `3900` API S3 de Garage (solo en `127.0.0.1` por defecto), `47808/udp` descubrimiento.

Variables del core (`cefa-tv-core/.env`, no versionado; plantilla en `.env.template`):

- Base de datos y almacenamiento: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `S3_ENDPOINT`, `S3_ACCESS_KEY` (`GK` + 24 hex), `S3_SECRET_KEY` (64 hex), `S3_BUCKET` (por defecto `cefa-tv-media`), `S3_REGION` (por defecto `garage`) y `GARAGE_RPC_SECRET` (64 hex).
- Sesión y administrador inicial: `JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_PASSWORD` (crean el primer administrador si no hay usuarios), `PUBLIC_URL` (URL del core visible por los reproductores, opcional), `APP_BASE_URL` (URL pública de la interfaz).
- Microsoft Entra ID: `AZURE_TENANT_ID` (GUID del directorio; se rechazan `common`, `organizations` y los dominios), `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, `AZURE_REDIRECT_URI` (debe ser exactamente `<URL pública>/core-api/login/oauth2/code/azure`), `MICROSOFT_AUTO_PROVISION` (por defecto `false`), `MICROSOFT_DEFAULT_GROUP_ID` (opcional). El inicio de sesión con Microsoft queda apagado mientras falten estos valores.

## Modelo de datos (Flyway, PostgreSQL)

- **Usuarios**: `users(id uuid, username, display_name, email, auth_provider LOCAL|MICROSOFT|HYBRID, microsoft_object_id, password_hash null, enabled, created_at)`, `user_groups(id uuid, name, description, permissions text[], builtin boolean, all_access boolean)`, `user_group_members(user_group_id, user_id)` y `access_grants(id uuid, user_group_id, player_group_id null, site_id null, level VIEW|OPERATE)` (exactamente uno de `player_group_id` y `site_id`).
- **Medios**: `folders(id uuid, name, parent_id null=raíz, created_at, created_by)` (solo de medios, nombres únicos entre hermanas, profundidad máxima 10) y `media_assets(id uuid, name, type IMAGE|VIDEO|PDF|WEB|HTML, folder_id null, mime, size_bytes, sha256, object_key, thumbnail_key, duration_ms, width, height, page_count, url, refresh_sec, pages jsonb, status READY|PROCESSING|FAILED, created_at, created_by)`. `pages` (solo PDF) es una lista de `{index, objectKey, sha256, sizeBytes}`. `url` es para `WEB`. Las playlists no tienen carpetas.
- **Playlists**: `playlists(id uuid, name, description, hidden boolean, created_at, updated_at)` y `playlist_items(id uuid, playlist_id, position, asset_id, duration_sec, fit COVER|CONTAIN, muted boolean, settings jsonb)`. Una playlist `hidden` es la que se crea automáticamente (asistente, contenido por defecto, arrastre de un medio suelto) y no sale en los listados salvo con `includeHidden=true`.
- **Dispositivos**: `sites(id uuid, name único, description, address, city, country, timezone IANA, created_at)`, `player_groups(id uuid, name, icon, color, site_id, idle_logo, fallback_playlist_id)` y `players(id uuid, name, description, group_id, site_id, status ACTIVE|DISABLED, device_token_hash, hostname, ip, hardware jsonb, resolution, orientation, preferred_output null, idle_logo null, player_version, player_build, timezone, last_seen_at, desired_version bigint, applied_version bigint, state, current_playlist_id, current_asset_id, fallback_playlist_id, last_error, system_info jsonb, displays jsonb, created_at)`. `online` se deriva de `last_seen_at` (menos de 90 s) o de tener WebSocket abierto. `neverConnected` se marca cuando un reproductor creado por emparejamiento no se ha conectado nunca en 7 días y se elimina solo a los 14.
- **Emparejamiento**: `pairings(id uuid, code, secret_hash, hostname, ip, hardware jsonb, resolution, player_version, timezone, status PENDING|APPROVED|CONSUMED|EXPIRED, expires_at, player_id, created_at)` y `pairing_audits`.
- **Programación**: `schedules(id uuid, name, playlist_id, priority int, start_date, end_date, start_time, end_time, days_of_week int[] 1=lunes..7=domingo, all_day boolean, enabled boolean)` y `schedule_targets(schedule_id, player_id null, group_id null, site_id null)`.
- **Entrega y estado**: `player_manifests(player_id, version bigint, content_hash, manifest jsonb, created_at)`, `player_deliveries`, `player_commands(…, payload jsonb)`, `player_metrics(player_id, at, cpu_percent, ram_used_mb, disk_free_mb, temp_c)` (se purga a las 24 h) y `shell_audits`.
- **Auditoría**: `audit_logs(id, at, user_id, username, action, entity_type, entity_id, entity_name, outcome SUCCESS|DENIED|FAILED, details jsonb, ip, user_agent)` y `audit_settings(retention_days)`.

Regla de resolución: en un instante dado, el reproductor reproduce la playlist del `schedule` activo (habilitado, fecha dentro de `start_date..end_date`, día de semana incluido, hora dentro de `start_time..end_time`) con mayor `priority`; en empate, el que empezó más tarde. Si no hay ninguno activo, reproduce `fallbackPlaylistId` (el del reproductor manda sobre el de su grupo) y, si tampoco existe, muestra el logo de CEFA.

Zona horaria: el manifest lleva la zona de la sede del reproductor si la tiene; si no, la que reporta el propio reproductor. Los horarios (`start_time`, `end_time`, días) se evalúan en esa zona.

Un `schedule` puede apuntar a reproductores, grupos de reproductores y sedes. Cualquier cambio que afecte a un reproductor (medio, playlist, programación, grupo, sede) recalcula su manifest (con un retardo de 2 s para agrupar cambios). Si el `content_hash` cambia, se inserta una versión nueva, se incrementa `desired_version` y, si el reproductor está conectado por WebSocket, se le envía `SYNC`.

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

Errores con el cuerpo `{ "status": 404, "errorCode": "NOT_FOUND", "message": "...", "details": { "campo": "motivo" } }` (mismo contrato que Hermes). Los 403 llevan `PERMISSION_DENIED`, `SCOPE_DENIED` o `ADMIN_REQUIRED`. Cada ruta exige el permiso que se indica en "Usuarios, grupos y permisos"; una escritura sin permiso o sin alcance sobre el reproductor afectado devuelve 403 y queda auditada.

### Sesión y sistema

| Método y ruta | Descripción |
|---|---|
| `POST /auth/login` `{username,password}` | Inicia sesión (cookie `httpOnly`). Solo para usuarios `LOCAL` o `HYBRID`. |
| `POST /auth/logout`, `POST /auth/password` | Cierra sesión / cambia la propia contraseña. |
| `GET /auth/me` | `{id, username, displayName, email, authProvider, groups:[{id,name}], permissions[], grants:[{playerGroupId\|siteId, level}], admin}`. Un administrador recibe todos los permisos y `grants` vacío. |
| `GET /auth/providers` | Público: `{local:true, microsoft:<bool>}`. La interfaz muestra el botón de Microsoft solo si es `true`. |
| `GET /oauth2/authorization/azure` | Inicio del flujo de Microsoft (redirección de página completa; admite `?redirect=` validado contra redirecciones abiertas). Al volver fija la cookie y redirige a `APP_BASE_URL` o a `/login?error=<código>`. |
| `GET /public/info` | Sin autenticación: `{service:"cefa-tv-server", name, version}`. Lo usa el reproductor para validar el descubrimiento. |
| `GET /dashboard` | Totales: reproductores (online, offline, pendientes de entrega), medios, playlists, almacenamiento; filtrado por lo visible. |

Códigos de error de Microsoft en `?error=`: `microsoft_user_not_found`, `microsoft_user_disabled`, `microsoft_not_allowed`, `microsoft_account_mismatch`, `microsoft_email_missing`, `microsoft_cancelled`, `microsoft_failed`.

### Medios y playlists

| Método y ruta | Descripción |
|---|---|
| `GET /media?type=&q=&folderId=&recursive=&page=&size=` | Página `{content, totalElements, page, size}`. `folderId` admite `root`; con `q` global cada resultado trae `folderPath`. |
| `POST /media` (multipart `files[]`, `folderId`) | Sube imágenes, vídeos, PDF y HTML a una carpeta. Devuelve la lista de medios creados. |
| `POST /media/web` `{name,url,refreshSec,folderId?}` | Crea un medio de tipo web. |
| `GET/PATCH/DELETE /media/{id}` | Detalle, renombrar / duración por defecto / `folderId` / `moveToRoot`, borrar (409 si lo usa una playlist). |
| `GET /media/{id}/content`, `GET /media/{id}/thumbnail` | Contenido y miniatura. |
| `POST /media/move` `{ids, folderId}` | Movimiento masivo (`folderId` nulo = raíz). |
| `GET /folders`, `GET /folders/{id}/path` | Lista plana con `parentId` y contadores de elementos y subcarpetas / migas de pan. |
| `POST /folders` `{name,parentId}`, `PATCH /folders/{id}` `{name,parentId}`, `DELETE /folders/{id}` | Crear, renombrar o mover, borrar (409 si no está vacía salvo `?moveContentsToParent=true`). Sin ciclos. |
| `GET/POST /playlists`, `GET/PUT/DELETE /playlists/{id}` | `GET` admite `q` e `includeHidden`. `PUT` reemplaza la lista de items `{assetId,durationSec,fit,muted}` en orden. Incluye `totalDurationSec`. `POST/PUT` aceptan `hidden`. |

### Dispositivos

| Método y ruta | Descripción |
|---|---|
| `GET/POST /groups`, `GET/PUT/DELETE /groups/{id}` | Grupos de reproductores con `siteId`/`clearSite` (la respuesta trae `siteId` y `siteName`; ver "Sede de un grupo"), `icon` (`^Icon[A-Za-z0-9]+$`, nombre de un icono de tabler), `color` (`slate, blue, green, amber, red, violet, pink, teal`) `fallbackPlaylistId` e `idleLogo`/`clearIdleLogo` (solo administradores; la respuesta trae `idleLogo`). Crear y borrar: solo administradores. Fijar solo la playlist de reposo exige `SCHEDULE_MANAGE` y `OPERATE`. |
| `GET/POST /sites`, `GET/PUT/DELETE /sites/{id}` | Sedes con `playerCount` y `onlineCount`. Crear, editar y borrar: solo administradores. `DELETE` da 409 `SITE_SCHEDULED` si tiene programaciones, 409 `SITE_NOT_EMPTY` si tiene reproductores y 409 `SITE_HAS_GROUPS` si tiene grupos, salvo con `?reassignTo=<siteId\|none>`, que mueve también los grupos y los reproductores. |
| `GET /players?siteId=`, `GET /players/{id}` | Incluyen `online`, `desiredVersion`, `appliedVersion`, `pending`, `lastSeenAt`, `state`, `description`, `siteId`, `siteName`, `preferredOutput`, `idleLogo`, `effectiveIdleLogo`, `idleLogoSource` (`PLAYER`, `GROUP` o `DEFAULT`), `neverConnected`, `previewAt`, `currentAssetThumbnailUrl`, `playerVersion`, `playerBuild`, `system` y `displays`. |
| `PATCH /players/{id}` | `name`, `description`, `groupId`, `clearGroup`, `siteId`, `clearSite`, `orientation`, `status`, `fallbackPlaylistId`, `clearFallbackPlaylist`, `preferredOutput`, `clearPreferredOutput`, `idleLogo`, `clearIdleLogo` (400 `DISPLAY_NOT_FOUND` si la salida no existe entre las conocidas). Quitar grupo o sede exige administrador. |
| `DELETE /players/{id}` | Da de baja y envía `UNENROLL` si está conectado. |
| `POST /players/{id}/resync`, `/restart`, `/reload` | Fuerza recálculo y entrega / reinicia el reproductor / recarga la vista. |
| `GET /players/{id}/deliveries` | Historial de versiones y su estado (`QUEUED`, `DOWNLOADING`, `APPLIED`, `FAILED`). |
| `GET /players/{id}/metrics?hours=` | Serie `[{at, cpuPercent, ramUsedMb, diskFreeMb, tempC}]` (máximo 24 h). |
| `POST /players/{id}/display/redetect`, `/display/resolution` `{output,width,height}` | Encola `DISPLAY_REDETECT` / `SET_RESOLUTION`. |
| `GET /players/{id}/preview` | Último JPEG cacheado de la vista previa (404 si no hay; `X-Preview-At`, `Cache-Control: no-store`). |
| `GET /players/{id}/shell-audits` | Auditoría de sesiones de terminal. |
| `GET /pairings/pending`, `POST /pairings/claim` `{code,name,groupId?}` | Lista los reproductores que esperan emparejarse (hostname, IP, hardware; **sin el código**, que solo se ve en la pantalla física) y aprueba uno por su código. Límite de 5 códigos erróneos por minuto y usuario (429 `TOO_MANY_ATTEMPTS`). Devuelve el reproductor creado. |

### Programación

| Método y ruta | Descripción |
|---|---|
| `GET/POST /schedules`, `GET/PUT/DELETE /schedules/{id}` | Crear admite `playlistId` o `assetId` (con `durationSec`) para el arrastre directo de un medio. `name` obligatorio (máx. 120). `targets: {playerIds[], groupIds[], siteIds[]}`. La respuesta incluye `thumbnailUrls` (hasta 8, de los primeros elementos), `itemCount` y `totalDurationSec`, y el estado calculado en el servidor con la zona de cada destino: `status` (`ACTIVE_NOW` dentro de su franja, sin tener en cuenta la prioridad; `SCHEDULED`, `ENDED`, `DISABLED`), `nextRunAt` (inicio de la próxima emisión posterior al instante actual) y `timezone` (solo si todos los destinos comparten zona; si no, `null` y `timezones[]` las lista). Las franjas no pueden cruzar la medianoche (la hora de fin debe ser posterior a la de inicio). |
| `GET /schedules/timeline?playerIds=&groupIds=&siteIds=&from=&to=` | Ocurrencias expandidas por día para pintar el timeline y los calendarios, de varios destinos en una sola petición (parámetros repetibles o separados por comas; `playerId` sigue admitido). Cada ocurrencia trae `playerId`, `playerName`, `timezone`, `status` (pendiente, en curso o pasada), `nextRunAt` y las mismas miniaturas. Solo se devuelven los reproductores visibles; el rango máximo es de 62 días y 200 reproductores; sin destinos responde 400 `TARGETS_REQUIRED`, y 403 si el reproductor indicado no es visible. |

### Sede de un grupo

Un grupo de reproductores puede tener una sede. Si la tiene, la sede de todos sus reproductores es la del grupo: al asignarla o cambiarla pasan a ella en la misma transacción, y un reproductor que entra en el grupo (`PATCH` con `groupId` o `POST /pairings/claim` con `groupId`) la hereda. Un `siteId` distinto o `clearSite` en un reproductor de ese grupo responde 409 `SITE_INHERITED_FROM_GROUP`. Quitar la sede del grupo (`clearSite`) deja a los reproductores con la que tenían y vuelve a permitir editarla; sacar un reproductor del grupo (`clearGroup`) conserva su sede. El cambio recalcula los manifests (cambia la zona horaria) y se audita como `GROUP_SITE_ASSIGN` (con `affectedPlayers`) y `PLAYER_SITE_ASSIGN` por reproductor. Solo un administrador puede cambiar la sede de un grupo.

### Usuarios, grupos y permisos

Ver la sección siguiente. Rutas: `GET/POST /users`, `GET/PUT/DELETE /users/{id}`, `POST /users/{id}/password`, `GET/POST /user-groups`, `PUT/DELETE /user-groups/{id}`, `GET /permissions`.

### Auditoría

| Método y ruta | Descripción |
|---|---|
| `GET /audit?from&to&userId&action&entityType&entityId&outcome&q&page&size` | Eventos ordenados por fecha descendente. |
| `GET /audit/{id}`, `GET /audit/actions`, `GET /audit/stats?from&to` | Detalle con `changes:[{field,before,after}]`, catálogo de acciones y estadísticas (acciones más frecuentes, actividad por usuario y por día). |
| `GET /audit/export.csv` | Exportación con los mismos filtros, en streaming y con tope. |
| `GET/PUT /settings/audit` `{retentionDays}` | Retención configurable (por defecto 365, mínimo 30) con purga diaria que se audita a sí misma. |

## Auditoría

Un interceptor central registra toda operación de escritura de la API de administración, los 403, el inicio y cierre de sesión (correctos y fallidos, incluidos los rechazos de Microsoft), el cambio de contraseña, los emparejamientos reclamados y fallidos, las órdenes a pantallas (resincronizar, reiniciar, recargar, resolución, salida de vídeo), las sesiones de control remoto y de terminal y los cambios de permisos y de usuarios. Las acciones tienen nombres estables (por ejemplo `PLAYLIST_UPDATE`, `SCHEDULE_CREATE`, `USER_GROUP_UPDATE`, `PLAYER_SITE_ASSIGN`, `PLAYER_OUTPUT_CHANGE`, `SITE_CREATE`, `FOLDER_MOVE`, `MEDIA_MOVE`). Nunca se guardan contraseñas, tokens, secretos, el código de emparejamiento ni el contenido de los archivos. Ver el registro exige el permiso `AUDIT_VIEW`.

## Usuarios, grupos y permisos

No hay roles. Un **usuario** pertenece a cero o más **grupos de usuarios** y recibe la unión de los permisos y los accesos de todos ellos. Sin grupos no ve nada.

- **Permisos** (catálogo de `GET /permissions`): `MEDIA_MANAGE`, `PLAYLIST_MANAGE`, `SCHEDULE_MANAGE`, `PLAYER_VIEW`, `PLAYER_MANAGE` (editar, dar de baja, emparejar, reiniciar), `PLAYER_REMOTE`, `PLAYER_TERMINAL`, `USER_MANAGE` (usuarios y grupos de usuarios) y `AUDIT_VIEW`.
- **Grupo de usuarios**: define `permissions`, `memberIds` y `grants:[{playerGroupId|siteId, level}]` (nivel `VIEW` para ver, `OPERATE` para programar, controlar en remoto o editar). Con `allAccess` el grupo tiene acceso a todos los reproductores, grupos y sedes y todos los permisos: quien está en un grupo así es administrador.
- **Grupo integrado `Administradores`** (`builtin`, `allAccess`): no se puede renombrar, cambiar ni borrar y no puede quedarse sin su último miembro activo. El sistema impide dejar sin administrador activo (`LAST_ADMIN`) o sin ninguno que pueda entrar con contraseña si Microsoft no está configurado (`LAST_PASSWORD_ADMIN`).
- **Escalada**: quien gestiona usuarios sin ser administrador no puede dar permisos que no tiene (`PRIVILEGE_ESCALATION`) ni tocar los grupos de acceso total (`ADMIN_REQUIRED`).
- **Regla de decisión**: un usuario puede hacer la acción P sobre el reproductor X si algún grupo suyo tiene P y es de acceso total, o tiene una concesión con nivel suficiente sobre el grupo de reproductores o la sede de X. Un reproductor sin grupo ni sede solo lo gestiona un administrador. Una programación solo puede apuntar a reproductores, grupos y sedes sobre los que el usuario tiene `OPERATE`; los listados de reproductores, programaciones, métricas y el dashboard se filtran por lo visible. Medios y playlists son una biblioteca compartida (permiso `MEDIA_MANAGE` o `PLAYLIST_MANAGE`). La terminal exige `PLAYER_TERMINAL` y `OPERATE`; el control remoto, `PLAYER_REMOTE` y `OPERATE`. Los permisos efectivos se cachean 5 minutos y se invalidan al editar usuarios, grupos, membresías, grupos de reproductores o sedes.
- **Usuarios**: `POST/PUT /users` aceptan `username`, `displayName`, `email`, `authProvider` (`LOCAL`, `MICROSOFT` o `HYBRID`), `password` (opcional; no se admite con `MICROSOFT`) y `groupIds`; la respuesta devuelve `groupIds`, `groups`, `admin` y `microsoftLinked`. Errores: `PASSWORD_NOT_ALLOWED`, `PASSWORD_REQUIRED`, `EMAIL_REQUIRED`, `EMAIL_EXISTS`, `UNKNOWN_PERMISSION`, `USER_GROUP_BUILTIN`.
- **Microsoft**: el usuario de Microsoft se vincula con el local primero por `oid` ya enlazado y después por correo sin distinguir mayúsculas (`email`, `preferred_username` o `upn`). Un usuario `LOCAL` con ese correo no se enlaza hasta que un administrador lo cambie a `HYBRID` o `MICROSOFT`. Un usuario inexistente o deshabilitado se rechaza; con `MICROSOFT_AUTO_PROVISION=true` se crea sin grupos (o en `MICROSOFT_DEFAULT_GROUP_ID`, nunca de acceso total).
- El administrador inicial sigue saliendo de `ADMIN_USERNAME` / `ADMIN_PASSWORD`, y se añade al grupo `Administradores`.

## API del reproductor (`/player-api`, cabecera `Authorization: Bearer <deviceToken>`)

| Método y ruta | Descripción |
|---|---|
| `POST /player-api/pairing/request` (sin token) `{hostname,hardware,resolution,playerVersion,playerBuild,timezone}` | Crea un emparejamiento pendiente y devuelve `{pairingId, code, secret, expiresAt, pollIntervalSec}`. `code` es de 6 caracteres sin ambiguos (`ABC-123`), único entre los pendientes, caduca a los 15 minutos. |
| `GET /player-api/pairing/{pairingId}` (cabecera `X-Pairing-Secret`) | Estado `PENDING`, `EXPIRED` o `APPROVED`; en `APPROVED` devuelve `{playerId, deviceToken, name}`. Mientras el reproductor no haga su primera llamada autenticada, cada consulta devuelve un token nuevo e invalida el anterior (así una respuesta perdida no lo deja sin credenciales), hasta 24 h tras la aprobación aunque el código ya haya caducado. Un identificador desconocido devuelve 200 `{status:"EXPIRED"}` y un secreto incorrecto, 401 `INVALID_PAIRING_SECRET`. |
| `GET /player-api/manifest` | Manifest actual, con `Content-Encoding: gzip` y `ETag: "<version>"` (acepta `If-None-Match`, 304). |
| `POST /player-api/files` `{sha256:[...]}` | Devuelve un `application/zip` con una entrada por `sha256` pedido (DEFLATED con nivel 0 para formatos ya comprimidos). |
| `GET /player-api/files/{sha256}` | Descarga directa con soporte de `Range` y `ETag`; para ficheros grandes. |
| `POST /player-api/heartbeat` `{appliedVersion,downloadingVersion,state,currentPlaylistId,currentAssetId,diskFreeMb,uptimeSec,ip,error,system,displays}` | Actualiza el estado; devuelve `{desiredVersion, commands:[{type, output?, width?, height?}], config:{preferredOutput, idleLogo}}`. `commands` pendientes: `RELOAD`, `RESTART`, `UNENROLL`, `DISPLAY_REDETECT`, `SET_RESOLUTION`. |
| `POST /player-api/ack` `{version,status:APPLIED\|FAILED,error}` | Confirma la aplicación de una versión. |

Un reproductor dado de baja y desconectado recibe 401 `INVALID_DEVICE_TOKEN` en su siguiente llamada; uno desactivado, 403 `PLAYER_DISABLED`. El reproductor trata el 401 como baja: borra su estado y vuelve a pedir un código de emparejamiento.

## WebSocket

`/ws/player?token=<deviceToken>` (reproductor), `/ws/remote/{playerId}`, `/ws/shell/{playerId}` y `/ws/previews` (navegador, cookie de sesión). Mensajes de texto JSON con campo `type`; los fotogramas del control remoto son mensajes binarios con los bytes de un JPEG.

Servidor a reproductor, tras `HELLO` en este orden: `CONFIG {preferredOutput, idleLogo}`, `SYNC` (si hay una versión nueva) y `PREVIEW_START` (si hay observadores). Además: `REMOTE_START {fps, quality, maxWidth}`, `REMOTE_STOP`, `INPUT`, `RELOAD`, `RESTART`, `UNENROLL`, `DISPLAY_REDETECT`, `SET_RESOLUTION {output,width,height}`, `PREVIEW_START {intervalSec, maxWidth, quality}`, `PREVIEW_STOP` y los mensajes `SHELL_*`.
Reproductor a servidor: `HELLO {appliedVersion}` (primer mensaje), binarios JPEG mientras haya sesión remota, `STATE {state, currentPlaylistId, currentAssetId}`, `TELEMETRY {system, displays}`, `CURSOR {css}`, `PREVIEW_FRAME {w, h, jpegBase64, at}` y los mensajes `SHELL_*`.
Navegador a servidor (`/ws/remote`): `START`, `STOP` e `INPUT`; el servidor los retransmite al reproductor y reenvía al navegador los fotogramas, el `CURSOR` y un `STATE {online}`.

`INPUT` (coordenadas normalizadas 0..1 sobre el fotograma):

```json
{ "type": "INPUT", "kind": "MOUSE_MOVE|MOUSE_DOWN|MOUSE_UP|MOUSE_WHEEL|KEY_DOWN|KEY_UP|TEXT",
  "x": 0.5, "y": 0.5, "button": "left|middle|right",
  "deltaX": 0, "deltaY": 120, "key": "Enter", "code": "Enter", "windowsVirtualKeyCode": 13,
  "text": "hola", "modifiers": 0 }
```

`modifiers` es la máscara de CDP (1 Alt, 2 Ctrl, 4 Meta, 8 Shift). Solo hay una sesión remota por reproductor a la vez; los fotogramas solo se envían mientras haya `REMOTE_START` activo. El servidor descarta tipos desconocidos y textos de más de 4096 caracteres.

### Control remoto: ratón y teclado completos

- El lienzo del navegador muestra un cursor de ratón normal que se adapta al elemento bajo el puntero (flecha, mano, texto): el reproductor consulta el estilo `cursor` con `Runtime.evaluate` en `elementFromPoint` (como máximo cada 100 ms) y envía `CURSOR {css}`.
- El navegador captura el teclado en un campo oculto para soportar cualquier distribución, AltGr, símbolos, acentos, teclas muertas y composición (IME). Todo carácter imprimible va como `TEXT {text}` (`Input.insertText`); las teclas no imprimibles y los atajos (Enter, Tab, flechas, Supr, Esc, Ctrl+A/C/V/X/Z/Y...) van como `KEY_DOWN`/`KEY_UP` con `key`, `code`, `windowsVirtualKeyCode` y `modifiers`. Ctrl+V pega el portapapeles del operador como `TEXT`.
- Mientras hay sesión remota, y 10 s después de cerrarla, el motor de reproducción no avanza de elemento ni recarga la página, para no perder lo escrito; al terminar reanuda con la programación vigente. Si el screencast no envía fotogramas (página estática), el reproductor captura con `Page.captureScreenshot` a unos 4 fotogramas por segundo.

### Vista previa en vivo

Ligera y bajo demanda. El navegador abre `/ws/previews` (permiso `PLAYER_VIEW`, solo reproductores visibles) y envía `SUBSCRIBE {playerIds[] (máx. 48), intervalSec (5..60, 10 por defecto)}` y `UNSUBSCRIBE {playerIds[]}`; renovar el `SUBSCRIBE` mantiene la suscripción (30 s sin renovar o cerrar el WebSocket la da de baja). El servidor cuenta los suscriptores por reproductor: al primero le envía `PREVIEW_START {intervalSec, maxWidth:320, quality:45}` y al último `PREVIEW_STOP`. Mientras está activo, el reproductor captura una miniatura por intervalo (`Page.captureScreenshot` con `clip` y `scale`), omite el envío si la imagen es idéntica a la anterior (salvo un latido cada 60 s), no captura durante una sesión remota y limita cada fotograma a unos 25 KB. El servidor guarda solo el último fotograma por reproductor y lo reenvía a los suscriptores como mensaje binario: 36 bytes ASCII con el `playerId` (UUID) seguidos de los bytes del JPEG. Sin suscriptores no viaja nada.

## Reproductores Aracast

Un player Aracast (caja Linux de Aranova, `AraCastPlayer` 2.6 o 4.0) se da de alta como un `Player` más (`players.kind = ARACAST`, datos de conexión en `aracast_players`) y se gestiona igual que un reproductor de CEFA TV. A diferencia de este, no descarga nada: el servidor empuja la programación por la red del player (comandos JSON por TCP 5401, FTP 5481 y SSH).

- **Entrega.** `ManifestService` calcula la versión como siempre. `AracastDeliveryService` la traduce con `AracastPlanner` y la publica con `AracastPublisher`; después marca la entrega (`APPLIED` o `FAILED`) y `appliedVersion`. Si el player no responde la versión queda en cola y se reintenta cada 30 s; cada hora se recalcula la ventana de siete días.
- **Traducción.** Las prioridades y los rangos de fechas se resuelven en el servidor con la misma regla que el reproductor (gana la programación activa de mayor prioridad y, en empate, la que empezó más tarde) para los próximos siete días; los eventos iguales se agrupan por día de la semana en un `events.ics` con `RRULE`. Cada playlist es una plantilla (`Texture`, `Slideshow`, `Video` o `HTML5`); una playlist mixta o con duraciones distintas se expande como un ciclo de plantillas dentro de cada franja. Un día termina a las 23:59:30, como en la aplicación de escritorio. Si no hay eventos se deja la plantilla `basic_json` (el logo).
- **Nombres.** El player usa los nombres de plantilla como patrones, así que se sanean (solo letras, números, espacios, guion y guion bajo) y llevan al final la marca `~ctv-<ocho caracteres del id de la playlist>[-<paso>]`, que permite reconocer y borrar las que ya no se usan. Los contenidos se guardan como `<nombre>_<sha1>.<ext>`.
- **Sobrescribir al momento.** Tras publicar se ejecuta `Template.changeTemplate` con la plantilla que toca ahora si el player muestra otra o si la que muestra se ha sustituido, de modo que una programación actualizada reemplaza a la que se está emitiendo sin esperar al siguiente cambio del calendario.
- **Estado.** `AracastHealthMonitor` consulta cada 30 s a los Aracast (versión, MAC, zona horaria, disco, plantilla en emisión) y mantiene `lastSeenAt`, el estado `PLAYING` o `IDLE` y la playlist y el medio en emisión.
- **Mismo protocolo hacia el navegador.** `AracastBridge` implementa `VirtualPlayerBridge` y `PlayerSessionRegistry` le entrega los mensajes `REMOTE_START`, `REMOTE_STOP`, `INPUT` y `SHELL_*` de un Aracast como si fuera un reproductor con WebSocket: la pantalla en vivo y el teclado y ratón se hacen por SSH (`ffmpeg x11grab` y XTest), la terminal es un shell con PTY y la vista previa captura una miniatura mientras alguien la mira (`PreviewDemandEvent`). El `RESTART` reinicia el equipo; `RELOAD` y las órdenes de pantalla no existen en un Aracast.
- **Credenciales.** Cada Aracast puede tener contraseña SSH y FTP propias, guardadas cifradas con AES-256-GCM (`ARACAST_SECRET_KEY`, o el secreto de sesión). Al guardar una SSH se valida contra el player y, si no se indica FTP, se lee de `/root/.aracast/data/custom.cfg`. `ARACAST_SSH_PASSWORDS` y `ARACAST_FTP_PASSWORD` son el respaldo común.

| Método y ruta | Función |
|---|---|
| `GET /players/aracast/probe?host=` | Consulta un player sin darlo de alta. 422 `ARACAST_UNREACHABLE` o `ARACAST_INVALID_RESPONSE`. |
| `POST /players/aracast` `{host,name?,description?,groupId?,siteId?,sshPassword?,ftpPassword?}` | Alta. 409 `ARACAST_EXISTS`; 422 `ARACAST_SSH_AUTH_FAILED` si el player rechaza la contraseña. |
| `GET /players/aracast/{id}` | Datos de conexión; indica si hay contraseñas propias, nunca las devuelve. |
| `PUT /players/aracast/{id}/credentials` `{sshPassword?,ftpPassword?}` | Cambia las contraseñas (nulo no cambia, vacío vuelve a las globales). |
| `GET /players/{id}/aracast/clock`, `POST` `{mode:SERVER\|NTP,server?}` | Hora del player y sincronización. |
| `GET /players/{id}/aracast/network`, `PUT` `{hostname,ip,netmask,gateway,dnsPrimary,dnsSecondary?,reboot}` | Red del player; solo administradores. Cambiar la dirección reinicia el equipo. |
| `POST /sites/{siteId}/aracast/discover` | Barre los rangos de la sede (UDP 30333) y devuelve lo encontrado, si ya está registrado y los Aracast de la sede que no responden. 422 `SITE_WITHOUT_RANGES`. |
| `GET /players/install` | Comando de instalación del reproductor con la URL de `PLAYER_REPO_URL`. |

Variables propias: `PLAYER_REPO_URL`, `ARACAST_SSH_PASSWORDS`, `ARACAST_FTP_PASSWORD`, `ARACAST_SECRET_KEY`, `ARACAST_NTP_SERVERS`, `ARACAST_COMMAND_PORT`, `ARACAST_FTP_PORT`, `ARACAST_DISCOVERY_PORT`. Las sedes ganan `ipRanges` (CIDR, intervalo o IP suelta) para el descubrimiento.

## Descubrimiento

El sidecar `cefa-tv-discovery` (socat en red host, UDP `47808`) responde a la petición `CEFATV_DISCOVER 1` con una línea JSON `{"service":"cefa-tv","version":1,"apiUrl":"http://<ip-del-servidor>:8080"}`, calculando la IP con `ip route get <ip del cliente>`. El reproductor envía el broadcast a `255.255.255.255:47808` y a la dirección de broadcast de cada interfaz, espera 3 s, reintenta con espera creciente y valida la respuesta con `GET {apiUrl}/api/v1/public/info`. El contenedor del reproductor usa red host para que el broadcast salga a la LAN.

## Emparejamiento por código

1. El reproductor, sin estado guardado, localiza el servidor (descubrimiento o `CEFA_TV_SERVER`) y llama a `POST /player-api/pairing/request`.
2. Muestra en pantalla el código (`ABC-123`), con el logo de CEFA y la dirección del servidor, y lo expone en `GET http://127.0.0.1:8081/api/status` (con `phase`: `SEARCHING_SERVER`, `PAIRING`, `CONNECTING`, `ONLINE`) para que el instalador lo imprima. Guarda `pairing.json` en `/data` mientras espera para sobrevivir a reinicios.
3. Sondea `GET /player-api/pairing/{pairingId}` cada 3 s con el secreto. El administrador introduce el código en **Dispositivos, Reproductores, Añadir reproductor** (`POST /pairings/claim`), elige nombre y grupo y el servidor crea el reproductor y emite el token.
4. El reproductor guarda `state.json`, borra `pairing.json` y empieza a sincronizar. Si el servidor responde que el emparejamiento está caducado o es desconocido, pide un código nuevo; ante un corte de red o un token no recibido sigue sondeando el mismo emparejamiento, para no dejar reproductores huérfanos.

El código no sirve para nada una vez usado y no aparece en ninguna API de administración. Los intentos erróneos se limitan por usuario y se auditan. Las peticiones sin reclamar se borran a las 24 h.

## Reproductor

```
cefa-tv-player/        Spring Boot 3.4, Java 21, paquete es.cefa.tv.player
├── src/
├── docker/            entrypoint.sh, política de Chromium y ficheros del contenedor
├── install.sh         instalador (instala, arranca y muestra el código de emparejamiento)
├── update.sh          actualizador con reversión automática
├── uninstall.sh       desinstalador
├── docs/installation-manual.md
├── Dockerfile
└── compose.yml
```

Estado persistente en el volumen `/data` (`state.json` con servidor y token, `pairing.json` durante el emparejamiento, `display.json` con la salida preferida, `manifest.json`, `files/<sha256>`). Dominios del código: `pairing`, `discovery`, `sync`, `storage`, `playback`, `browser` (lanzador de Chromium y cliente CDP), `display`, `telemetry`, `shell`, `remote`, `command`, `status`, `shared`.

Reproducción: un motor Java evalúa el manifest con el reloj local, elige la playlist activa y ordena a la página local `/player` (servida por el propio reproductor, fondo oscuro, sin parpadeos) qué mostrar mediante SSE: imagen, vídeo, página de PDF. Para medios `WEB` el motor navega Chromium a la URL por CDP (no usa iframe, porque muchas webs lo bloquean) y vuelve a `/player` al terminar; los `HTML` se sirven localmente desde `files/`. La pantalla muestra siempre algo útil: logo de CEFA cuando no hay programación, el código de emparejamiento mientras está sin emparejar y "Buscando el servidor" o "Conectando con el servidor" (con el último error y la cuenta atrás del siguiente reintento) mientras reintenta.

Variables: `CEFA_TV_SERVER` (opcional), `CEFA_TV_PLAYER_NAME` (opcional), `DISPLAY_MODE` (`host`, `xorg`, `headless`), `CEFA_TV_RESOLUTION` (solo `headless`), `CEFA_TV_SHELL_ENABLED` y `CEFA_TV_SHELL_MODE`. El nombre, la versión (`playerVersion`) y el commit (`playerBuild`, inyectado en la construcción de la imagen) se informan en el emparejamiento, el heartbeat, la telemetría y `/api/status`.

Modos de pantalla del contenedor: `host` usa el servidor X del anfitrión (monta `/tmp/.X11-unix`), `xorg` arranca su propio Xorg a pantalla completa (contenedor privilegiado, equipos dedicados) y `headless` usa Xvfb (pruebas y control remoto sin pantalla física).

### Navegador sin interfaz

Chromium corre en kiosco con una política gestionada (`docker/chromium/cefa-tv.json`) que desactiva traducción, gestor de contraseñas, autocompletar, sincronización, notificaciones y descargas, idioma `es-ES` y un perfil limpio en cada arranque (se conservan las cookies). El cursor está oculto salvo durante una sesión de control remoto.

### Logo de la pantalla sin programación

La pantalla sin programación muestra un logo blanco sobre fondo oscuro: `CEFA` (por defecto) o `ALGONTEC` (enum `IdleLogo`). El valor se configura por reproductor y por grupo de reproductores (`idleLogo`, `null` = heredar): el del reproductor manda, después el de su grupo y, si no hay ninguno, `CEFA`. El servidor entrega al reproductor el valor efectivo en `config.idleLogo` del heartbeat y en el mensaje `CONFIG {preferredOutput, idleLogo}` (tras `HELLO` y al cambiar, también cuando cambia el del grupo o el grupo del reproductor). El reproductor lo guarda en `/data`, lo aplica en vivo sin recargar la página y usa `CEFA` si el valor falta o es desconocido. Los ficheros están en `static/brand/` del reproductor y en `public/brand/` de la app (`cefa-logo-white.svg`, `algontec-logo-white.svg`). Cambiarlo se audita como `PLAYER_IDLE_LOGO_CHANGE` y `GROUP_IDLE_LOGO_CHANGE`. La pantalla de emparejamiento sigue mostrando el logo de CEFA.

### Pantalla del reproductor

- El reproductor detecta las salidas de vídeo con `xrandr --query` (modo `host` y `xorg`) y elige la salida preferida si está conectada; si no, la primera conectada (la marcada como primaria si la hay). Fuerza su modo preferido (`xrandr --output X --auto`) para adaptar la resolución y coloca Chromium en kiosco exactamente sobre esa salida (`--window-position`, `--window-size`). El resto de salidas no se tocan.
- La salida preferida (`preferredOutput`, `null` = automática) llega por `config` del heartbeat y por el mensaje `CONFIG`, se guarda en `/data/display.json` y sobrevive a reinicios sin servidor. Si la salida no está conectada, usa la automática y lo anota en `lastError` ("Salida X no disponible, usando Y"); vuelve a la preferida cuando aparece.
- Cada 10 s comprueba si cambió la salida elegida (monitor desenchufado, enchufado, otra resolución). Si cambia, vuelve a elegir, ajusta la resolución, recoloca Chromium y avisa al servidor. En `headless` informa de la pantalla virtual de Xvfb.
- `DISPLAY_REDETECT` y `SET_RESOLUTION {output, width, height}` llegan por `commands` del heartbeat y por WebSocket. Un modo inexistente se rechaza y se informa en `lastError`; se aceptan modos con sufijo de refresco (`1280x720_60.00`).

### Telemetría

El reproductor envía `system` y `displays` en cada heartbeat y, mientras tenga el WebSocket abierto, en un mensaje `TELEMETRY` cada 15 s (y al instante cuando cambian las pantallas):

```json
{
  "system": {
    "hostname": "pantalla-recepcion", "os": "Debian 13", "kernel": "6.1.0", "arch": "x86_64",
    "cpuModel": "Intel N100", "cpuCount": 4, "cpuPercent": 12.5, "load1": 0.4,
    "ramTotalMb": 7934, "ramUsedMb": 1900, "diskTotalMb": 237000, "diskFreeMb": 200000,
    "tempC": 48.0, "uptimeSec": 123456, "ips": ["192.168.1.50"], "macs": ["aa:bb:cc:dd:ee:ff"],
    "playerVersion": "0.1.0", "playerBuild": "7578e81", "javaVersion": "21.0.5", "chromiumVersion": "155.0",
    "timezone": "Europe/Madrid", "displayMode": "host", "preferredOutput": null,
    "shellEnabled": true, "shellMode": "container"
  },
  "displays": [
    { "output": "HDMI-1", "connected": true, "primary": true, "selected": true, "width": 1920, "height": 1080,
      "refreshHz": 60.0, "xOffset": 0, "yOffset": 0, "modes": ["1920x1080", "1280x720"] }
  ]
}
```

Los campos que el equipo no pueda calcular (p. ej. `tempC` en un contenedor sin sensores, `refreshHz` en Xvfb) van a `null`. El servidor guarda lo último en `players.system_info` y `players.displays` (jsonb) y una muestra por heartbeat en `player_metrics` (se purga a las 24 h). El `resolution` del reproductor pasa a ser la de la salida elegida.

### Terminal remota

Una terminal interactiva en el reproductor, sin abrir puertos ni instalar un demonio SSH: el reproductor ya mantiene un WebSocket saliente y por él viaja una sesión de shell con pseudoterminal (PTY). Exige `PLAYER_TERMINAL` y `OPERATE` sobre el reproductor.

- Navegador: `/ws/shell/{playerId}`. Mensajes JSON del navegador al servidor: `OPEN {cols, rows}`, `INPUT {data}`, `RESIZE {cols, rows}`, `CLOSE`. Del servidor al navegador: `OPENED`, `OUTPUT {data}`, `EXIT {code}`, `ERROR {message}` (códigos `PLAYER_OFFLINE`, `SHELL_DISABLED`, `SHELL_BUSY`, `PLAYER_DISCONNECTED`, y avisos `IDLE_TIMEOUT` y `MAX_DURATION`). `data` es texto UTF-8.
- Servidor a reproductor: `SHELL_OPEN {sessionId, cols, rows}`, `SHELL_INPUT {sessionId, data}`, `SHELL_RESIZE {sessionId, cols, rows}`, `SHELL_CLOSE {sessionId}`. Reproductor a servidor: `SHELL_OUTPUT {sessionId, data}` y `SHELL_EXIT {sessionId, code, error?}`. El servidor genera el `sessionId`. Un rechazo del reproductor va como `SHELL_EXIT {code:-1, error}` (`SHELL_DISABLED`, `SHELL_BUSY`, `SHELL_LAUNCH_FAILED`).
- Una sola sesión activa por reproductor. Cierre automático tras 15 min sin entrada o 2 h de duración (`cefa.tv.shell.idle-timeout-sec`, `cefa.tv.shell.max-duration-sec`).
- Auditoría en `shell_audits` (usuario, instante, bytes de entrada y salida, motivo de cierre); no se guarda el contenido de la sesión.
- Reproductor: `CEFA_TV_SHELL_ENABLED` (por defecto `true`) y `CEFA_TV_SHELL_MODE` (`container` por defecto: shell dentro del contenedor; `host`: shell del anfitrión con `nsenter -t 1 -m -u -n -i`, exige `pid: host` y modo privilegiado, que el instalador solo configura con `--host-shell`).

## Robustez del reproductor

El reproductor se instala una vez y no se vuelve a tocar:

- Docker se habilita en el arranque del sistema y el contenedor tiene `restart: unless-stopped`; el instalador crea además la unidad `systemd` `cefa-tv-player.service` (`docker compose up -d` tras la red y Docker) y rotación de logs de Docker (`max-size`, `max-file`). En equipos sin `systemd` avisa y confía en el reinicio automático de Docker.
- Al arrancar, espera la red; si no encuentra el servidor, reintenta el descubrimiento y la conexión indefinidamente con espera creciente (de 2 s a 60 s, con jitter), sin cerrar el servicio. Si el servidor cambia de IP, vuelve a descubrirlo por broadcast tras varios fallos.
- Un supervisor reinicia Xorg o Xvfb, el gestor de ventanas y Chromium si caen, y un vigilante reinicia el contenedor si no se recupera; el contenedor tiene `HEALTHCHECK` real (API, X y Chromium).
- `update.sh` actualiza (`git pull --ff-only` o `docker pull`), reconstruye, recrea el contenedor sin perder `/data`, espera a que quede sano y vuelve a la versión anterior si no lo hace. `uninstall.sh` elimina todo con `--keep-data` para conservar el emparejamiento y `--remove-docker` opcional.
- Todo lo ya descargado sigue reproduciéndose sin conexión.
