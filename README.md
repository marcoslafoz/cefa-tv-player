# CEFA TV Player

Receptor de CEFA TV: aplicación Spring Boot (Java 21) que se ejecuta en Docker en cada pantalla. Se da de alta en [`cefa-tv-server`](../cefa-tv-server) con una clave, descarga la programación y la reproduce a pantalla completa en Chromium (modo kiosco). Funciona sin conexión con lo último que descargó y se puede controlar en remoto (ratón y teclado).

La arquitectura y el protocolo con el servidor están en [`docs/llm/context/architecture.md`](docs/llm/context/architecture.md).

## Instalación en una pantalla

En un equipo Debian o Ubuntu (mini PC, Raspberry Pi):

```bash
git clone <url-del-repositorio> cefa-tv-player
cd cefa-tv-player
sudo ./install.sh
```

El script pide la clave de alta (la que se genera en la interfaz de CEFA TV Server), instala Docker si falta, construye la imagen, arranca el contenedor y espera a que el reproductor se dé de alta. Opciones:

| Opción | Descripción |
|---|---|
| `--key CLAVE` | Clave de alta; si falta se pregunta. |
| `--server URL` | URL del core (por ejemplo `http://192.168.1.10:8080`). Si se omite, el reproductor busca el servidor en la red. |
| `--name NOMBRE` | Nombre de la pantalla (por defecto, el hostname). |
| `--mode MODO` | `host`, `xorg` o `headless` (ver más abajo). Por defecto se detecta. |
| `--image IMAGEN` | Usa una imagen de un registro en lugar de construirla. |
| `--host-shell` | La terminal remota abre la shell del equipo y no la del contenedor (ver más abajo). Pide confirmación salvo con `--yes`. |
| `--no-shell` | Desactiva la terminal remota. |
| `--uninstall` | Desinstala el reproductor y borra su alta. |

El instalador es idempotente: se puede ejecutar de nuevo para cambiar la configuración y reutiliza la que ya hay en `/opt/cefa-tv-player/.env`.

## Modos de pantalla

| Modo | Uso |
|---|---|
| `host` | El equipo ya tiene un escritorio (servidor X). El contenedor dibuja sobre él montando `/tmp/.X11-unix`. |
| `xorg` | Equipo dedicado sin escritorio: el contenedor arranca su propio Xorg (contenedor privilegiado). |
| `headless` | Xvfb sin pantalla física. Sirve para pruebas y para controlar el contenido solo en remoto. |

## Pantalla y resolución

En los modos `host` y `xorg` el reproductor detecta los monitores con `xrandr` y reproduce en la primera salida conectada (la primaria si existe y, si no, la primera por orden de `xrandr`). Fuerza el modo preferido del monitor para adaptar la resolución y coloca Chromium en kiosco exactamente sobre esa salida. El resto de salidas no se tocan. Cada 10 segundos comprueba si se ha enchufado, desenchufado o cambiado un monitor: si es así, vuelve a elegir salida, recoloca el navegador y avisa al servidor. En `headless` informa de la pantalla virtual de Xvfb.

Desde el servidor se puede pedir `DISPLAY_REDETECT` (volver a detectar) y `SET_RESOLUTION` (otro modo de una salida, que se mantiene mientras el monitor siga conectado).

## Telemetría

En cada latido y, con el WebSocket abierto, cada 15 segundos (`TELEMETRY`) el reproductor envía datos del equipo (CPU, memoria, disco, temperatura, IP, MAC, versiones, tiempo de actividad) y las pantallas detectadas. Se leen de `/proc`, `/sys` y la JVM, sin dependencias adicionales. Lo que no se pueda calcular se envía como `null`.

## Terminal remota

Un administrador del servidor puede abrir una terminal en el reproductor. No hay demonio SSH ni puertos abiertos: la sesión viaja por el WebSocket saliente que el reproductor ya mantiene y usa un pseudoterminal (`pty4j`). Solo hay una sesión a la vez y se corta si cae la conexión con el servidor.

| Variable | Valor | Efecto |
|---|---|---|
| `CEFA_TV_SHELL_ENABLED` | `true` (por defecto) / `false` | Con `false` rechaza las sesiones y lo informa al servidor (`system.shellEnabled`). |
| `CEFA_TV_SHELL_MODE` | `container` (por defecto) / `host` | `container` abre `/bin/bash` dentro del contenedor. `host` abre la shell del equipo con `nsenter -t 1 -m -u -n -i`. |

El modo `host` exige contenedor privilegiado y `pid: host`, y da acceso de root al equipo. El instalador solo lo configura con `--host-shell`, tras una advertencia y una confirmación (salvo con `--yes`). Para desactivar la terminal: `--no-shell`.

## Descubrimiento y alta

El contenedor usa red `host`. Con la clave configurada, el reproductor envía un broadcast UDP al puerto `47808` y valida la respuesta con `/api/v1/public/info`. Si el servidor está en otra red, indica `--server`. Una clave inválida se reintenta cada 30 s y se muestra en pantalla y en `http://127.0.0.1:8081/`.

## Funcionamiento sin conexión

La programación y los ficheros se guardan en el volumen `/data`. El reproductor decide qué emitir con su reloj local y la zona horaria del manifest. Si el servidor no responde, sigue reproduciendo y reintenta cada 30 s; cuando vuelve la conexión descarga la versión pendiente (solo los ficheros que faltan) y la aplica.

## Desarrollo

```bash
make build     # compila el JAR
make test      # pruebas unitarias
make check     # formato (Spotless) y pruebas
make rebuild   # reconstruye la imagen y levanta el contenedor (modo headless por defecto)
```

Para ejecutar en local sin Docker ni navegador:

```bash
CEFA_TV_CHROMIUM_ENABLED=false CEFA_TV_ENROLLMENT_KEY=XXXX-XXXX-XXXX CEFA_TV_SERVER=http://localhost:8080 ./mvnw spring-boot:run
```

Variables: `CEFA_TV_ENROLLMENT_KEY`, `CEFA_TV_SERVER`, `CEFA_TV_PLAYER_NAME`, `DISPLAY_MODE`, `CEFA_TV_RESOLUTION` (solo el tamaño de la pantalla virtual en `headless`), `CEFA_TV_DATA_DIR`, `CEFA_TV_SHELL_ENABLED`, `CEFA_TV_SHELL_MODE`. Plantilla en `.env.template`.

Páginas locales (solo accesibles desde el propio equipo): `http://127.0.0.1:8081/` estado y `http://127.0.0.1:8081/player` vista de reproducción.
