# CEFA TV Player

Receptor de CEFA TV: aplicación Spring Boot (Java 21) que se ejecuta en Docker en cada pantalla. Se empareja con [`cefa-tv-server`](../cefa-tv-server) mediante un código que muestra en pantalla, descarga la programación y la reproduce a pantalla completa en Chromium (modo kiosco). Funciona sin conexión con lo último que descargó y se puede controlar en remoto (ratón, teclado y terminal).

La arquitectura y el protocolo con el servidor están en [`docs/llm/context/architecture.md`](docs/llm/context/architecture.md).

La guía completa de instalación, actualización, desinstalación y resolución de problemas, con la prueba sobre un Debian limpio, está en el [manual de instalación](docs/installation-manual.md).

## Instalación en una pantalla

En un equipo Debian o Ubuntu (mini PC, Raspberry Pi):

```bash
git clone <url-del-repositorio> cefa-tv-player
cd cefa-tv-player
sudo ./install.sh
```

El script instala Docker si falta, construye la imagen, arranca el contenedor, deja el reproductor habilitado para arrancar con el equipo (servicio `systemd` `cefa-tv-player.service`) y, al final, **imprime el código de emparejamiento** (por ejemplo `ABC-123`). El mismo código aparece en la pantalla del reproductor. Se introduce en CEFA TV Server, en Reproductores, Añadir reproductor, y el reproductor queda emparejado. No hace falta ninguna clave previa.

El reproductor se instala una vez y no hay que volver a tocarlo (ver "Robustez").

| Opción | Descripción |
|---|---|
| `--server URL` | URL del core (por ejemplo `http://192.168.1.10:8080`). Si se omite, el reproductor busca el servidor en la red. |
| `--name NOMBRE` | Nombre solicitado para la pantalla (por defecto, el hostname). |
| `--mode MODO` | `host`, `xorg` o `headless` (ver más abajo). Por defecto se detecta. |
| `--image IMAGEN` | Usa una imagen de un registro en lugar de construirla. |
| `--host-shell` | La terminal remota abre la shell del equipo y no la del contenedor (ver más abajo). Pide confirmación salvo con `--yes`. |
| `--no-shell` | Desactiva la terminal remota. |
| `--dir RUTA` | Directorio de instalación (por defecto `/opt/cefa-tv-player`). |
| `--project NOMBRE` | Nombre del contenedor, volumen y servicio (por defecto `cefa-tv-player`); permite varias instalaciones en un mismo equipo. |
| `--yes` | No pregunta confirmaciones (instala Docker si falta). |
| `--uninstall` | Desinstala el reproductor; delega en `uninstall.sh`. |

El instalador es idempotente: se puede ejecutar de nuevo para cambiar la configuración, reutiliza la que ya hay en `/opt/cefa-tv-player/.env` y **conserva el emparejamiento** (vive en el volumen `/data`).

## Actualizar

```bash
cd cefa-tv-player
sudo ./update.sh
```

`update.sh` hace `git pull --ff-only` en el repositorio clonado (o `docker pull` con `--image IMAGEN`), reconstruye la imagen y recrea el contenedor **sin perder el emparejamiento ni el contenido descargado**. Etiqueta antes la imagen en uso como `cefa-tv-player:previous`, espera a que el contenedor quede sano (`healthcheck`) y, si la versión nueva no queda sana, **vuelve automáticamente a la anterior**. Al terminar imprime la versión y el commit anterior y el nuevo. Es idempotente: si ya está en la última versión no hace nada.

| Opción | Descripción |
|---|---|
| `--image IMAGEN` | Actualiza haciendo `docker pull` de esta imagen en lugar de construirla desde el repositorio. |
| `--force` | Recrea el contenedor aunque la imagen no haya cambiado. |
| `--timeout SEG` | Segundos de espera a que el contenedor quede sano (300 por defecto). |
| `--dir RUTA`, `--project NOMBRE` | Igual que en el instalador. |

## Desinstalar

```bash
sudo ./uninstall.sh
```

Pide confirmación escribiendo `desinstalar` (salvo con `--yes`) y elimina el contenedor, las imágenes `cefa-tv-player:local` y `cefa-tv-player:previous`, el volumen de datos (emparejamiento y contenido descargado), el servicio `systemd` y `/opt/cefa-tv-player`. No toca Docker.

| Opción | Descripción |
|---|---|
| `--keep-data` | Conserva el volumen de datos: al reinstalar, el reproductor sigue emparejado. |
| `--remove-docker` | Desinstala además Docker del equipo (Debian y Ubuntu). Afecta a **todos** los contenedores del equipo y exige escribir `eliminar-docker` (salvo con `--yes`). |
| `--dir RUTA`, `--project NOMBRE`, `--yes` | Igual que en el instalador. |

## Emparejamiento

Sin estado guardado, el reproductor localiza el servidor, pide un código y lo muestra en pantalla (con el logo de CEFA y la dirección del servidor). También lo expone en `http://127.0.0.1:8081/api/status` (`pairingCode`) y lo imprime el instalador. Cuando el administrador lo aprueba en el servidor, recibe su token y lo guarda. Si el código caduca (15 minutos) o el servidor lo da por perdido, pide uno nuevo solo; el que se ve en pantalla es siempre el vigente. Si el servidor da de baja al reproductor, este borra su estado y vuelve a mostrar un código nuevo sin reiniciar el proceso.

El contenedor usa red `host`. Para localizar el servidor envía un broadcast UDP al puerto `47808` y valida la respuesta con `/api/v1/public/info`. Si el servidor está en otra red, indica `--server`.

## Robustez

- **Arranque**: Docker queda habilitado en el arranque del equipo, el contenedor tiene `restart: unless-stopped` y el instalador crea el servicio `cefa-tv-player.service` (`docker compose up -d` tras Docker y la red). Los registros de Docker rotan (10 MB, 3 ficheros).
- **Sin servidor o sin red**: el reproductor espera la red y reintenta el descubrimiento y la conexión indefinidamente, con espera creciente de 2 s a 60 s y variación aleatoria. Nunca termina el proceso por no encontrar el servidor. Si el servidor cambia de IP, lo vuelve a descubrir por broadcast tras varios fallos seguidos.
- **Procesos de pantalla**: un supervisor reinicia Xorg (o Xvfb), el gestor de ventanas y el ocultador de cursor si caen, y el reproductor relanza Chromium en la salida correcta.
- **Salud**: el contenedor define un `HEALTHCHECK` real (API local, servidor gráfico y Chromium) y un vigilante interno reinicia el contenedor si no se recupera durante unos minutos.
- **Pantalla**: siempre muestra algo útil: el logo de CEFA sin programación, el código mientras no está emparejado y "Conectando con el servidor" (con el último error y el próximo reintento) cuando no tiene nada descargado.

## Navegador sin interfaz

Chromium se lanza en kiosco, sin barras ni avisos. La imagen incluye una política gestionada (`docker/chromium/cefa-tv.json`, instalada en `/etc/chromium/policies/managed/`) que desactiva traducción, gestor de contraseñas, autocompletar, historial, sincronización, inicio de sesión, métricas, notificaciones, ventanas emergentes, geolocalización y descargas. Los flags de `ChromiumLauncher` ocultan el resto de avisos (sesión restaurada, navegador predeterminado, infobars), fijan el idioma de la interfaz y `Accept-Language` en español y, en cada arranque, borran las preferencias y la sesión del perfil para que no reaparezcan avisos (las cookies se conservan, porque una web puede necesitar su sesión). El cursor se oculta con `unclutter`.

## Modos de pantalla

| Modo | Uso |
|---|---|
| `host` | El equipo ya tiene un escritorio (servidor X). El contenedor dibuja sobre él montando `/tmp/.X11-unix`. |
| `xorg` | Equipo dedicado sin escritorio: el contenedor arranca su propio Xorg (contenedor privilegiado). |
| `headless` | Xvfb sin pantalla física. Sirve para pruebas y para controlar el contenido solo en remoto. |

## Pantalla y resolución

En los modos `host` y `xorg` el reproductor detecta los monitores con `xrandr` y reproduce en la primera salida conectada (la primaria si existe y, si no, la primera por orden de `xrandr`). Fuerza el modo preferido del monitor para adaptar la resolución y coloca Chromium en kiosco exactamente sobre esa salida. El resto de salidas no se tocan. Cada 10 segundos comprueba si se ha enchufado, desenchufado o cambiado un monitor: si es así, vuelve a elegir salida, recoloca el navegador y avisa al servidor. En `headless` informa de la pantalla virtual de Xvfb.

Desde el servidor se puede pedir `DISPLAY_REDETECT` (volver a detectar) y `SET_RESOLUTION` (otro modo de una salida, que se mantiene mientras el monitor siga conectado).

El servidor también puede fijar una salida preferida (`config.preferredOutput` en el latido y mensaje `CONFIG`): si está conectada se usa en lugar de la primaria; si no, se usa la automática, se anota "Salida X no disponible, usando Y" en `lastError` y se vuelve a la preferida cuando aparece. Se guarda en `/data/display.json` y se informa en `system.preferredOutput`.

## Logo de la pantalla sin programación

El servidor envía el logo efectivo (`CEFA` o `ALGONTEC`) en `config.idleLogo` del latido y en el mensaje `CONFIG`. Se guarda en `/data/idle-logo.json`, se informa en `system.idleLogo` y en `/api/status`, y la página `/player` cambia de logo en vivo por SSE sin recargarse. Un valor desconocido equivale a `CEFA`, y las pantallas de emparejamiento y conexión siempre muestran el logo de CEFA. Ambos logos se dibujan dentro de un recuadro de como máximo el 30 % del alto y el 60 % del ancho, centrados y sin deformarse.

## Vista previa

Con `PREVIEW_START {intervalSec, maxWidth, quality}` el reproductor captura la pantalla cada `intervalSec` segundos con una sola llamada a Chromium (`Page.captureScreenshot` con `clip` y `scale`, sin screencast continuo) y envía `PREVIEW_FRAME {w, h, jpegBase64, at}` de como máximo 25 KB. No envía imágenes idénticas a la anterior salvo un latido cada 60 s, no captura durante una sesión de control remoto ni sin una petición activa, y se detiene con `PREVIEW_STOP` o si cae el WebSocket.

## Control remoto

El servidor retransmite la pantalla de Chromium (screencast por DevTools) y aplica el ratón y el teclado del operador. El teclado admite símbolos, acentos, teclas muertas, AltGr, pegado y atajos de edición: los caracteres imprimibles llegan como `TEXT` (`Input.insertText`) y el resto como `KEY_DOWN`/`KEY_UP` con su código de tecla virtual. Con la sesión activa, el reproductor consulta el cursor del elemento bajo el puntero (como máximo cada 100 ms) y envía `CURSOR {css}` para que el visor muestre el cursor correcto (flecha, mano, texto).

## Telemetría

En cada latido y, con el WebSocket abierto, cada 15 segundos (`TELEMETRY`) el reproductor envía datos del equipo (CPU, memoria, disco, temperatura, IP, MAC, versiones, tiempo de actividad) y las pantallas detectadas. Se leen de `/proc`, `/sys` y la JVM, sin dependencias adicionales. Lo que no se pueda calcular se envía como `null`. Cada reproductor informa su versión (`playerVersion`, la del `pom.xml` sin `-SNAPSHOT`) y su build (`playerBuild`, el commit corto de git con el que se construyó la imagen, `unknown` si no había git) en el emparejamiento, el latido, la telemetría, `/api/status` y la página de estado local.

## Modo mantenimiento

Con el teclado conectado al equipo, **Ctrl+Shift+M** cierra el kiosco (también si la pantalla muestra una web externa) y deja de reabrirlo, para poder trabajar en el equipo aunque no tenga red. El atajo se instala en Chromium por CDP, así que funciona sin conexión con el servidor.

Para volver al kiosco, ejecuta en el anfitrión `docker exec cefa-tv-player cefa-tv-kiosk` (o `cefa-tv-kiosk` desde una terminal del contenedor). Si no se hace, el kiosco se reabre solo a los 15 minutos. El estado se consulta en `http://127.0.0.1:8081/maintenance`, solo accesible desde el propio equipo.

## Terminal remota

Un administrador del servidor puede abrir una terminal en el reproductor. No hay demonio SSH ni puertos abiertos: la sesión viaja por el WebSocket saliente que el reproductor ya mantiene y usa un pseudoterminal (`pty4j`). Solo hay una sesión a la vez y se corta si cae la conexión con el servidor.

| Variable | Valor | Efecto |
|---|---|---|
| `CEFA_TV_SHELL_ENABLED` | `true` (por defecto) / `false` | Con `false` rechaza las sesiones y lo informa al servidor (`system.shellEnabled`). |
| `CEFA_TV_SHELL_MODE` | `container` (por defecto) / `host` | `container` abre `/bin/bash` dentro del contenedor. `host` abre la shell del equipo con `nsenter -t 1 -m -u -n -i`. |

El modo `host` exige contenedor privilegiado y `pid: host`, y da acceso de root al equipo. El instalador solo lo configura con `--host-shell`, tras una advertencia y una confirmación (salvo con `--yes`). Para desactivar la terminal: `--no-shell`.

## Funcionamiento sin conexión

La programación y los ficheros se guardan en el volumen `/data`. El reproductor decide qué emitir con su reloj local y la zona horaria del manifest. Si el servidor no responde, sigue reproduciendo y reintenta; cuando vuelve la conexión descarga la versión pendiente (solo los ficheros que faltan) y la aplica.

## Desarrollo

```bash
make build     # compila el JAR
make test      # pruebas unitarias
make check     # formato (Spotless) y pruebas
make rebuild   # reconstruye la imagen y levanta el contenedor (modo headless por defecto)
```

Para ejecutar en local sin Docker ni navegador:

```bash
CEFA_TV_CHROMIUM_ENABLED=false CEFA_TV_SERVER=http://localhost:8080 ./mvnw spring-boot:run
```

Variables: `CEFA_TV_SERVER`, `CEFA_TV_PLAYER_NAME`, `DISPLAY_MODE`, `CEFA_TV_RESOLUTION` (solo el tamaño de la pantalla virtual en `headless`), `CEFA_TV_DATA_DIR`, `CEFA_TV_SHELL_ENABLED`, `CEFA_TV_SHELL_MODE`. Plantilla en `.env.template`.

Los scripts `install.sh`, `update.sh` y `uninstall.sh` exigen root; para probarlos con un directorio temporal en un equipo sin root se puede usar `CEFA_TV_ALLOW_NON_ROOT=1` junto con `--dir` y `--project`.

Páginas locales (solo accesibles desde el propio equipo): `http://127.0.0.1:8081/` estado, `http://127.0.0.1:8081/api/status` estado en JSON y `http://127.0.0.1:8081/player` vista de reproducción.
