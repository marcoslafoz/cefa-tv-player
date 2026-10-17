# Manual de instalación de CEFA TV Player

Este manual explica cómo instalar el reproductor en un equipo con Debian, cómo emparejarlo con CEFA TV Server y cómo mantenerlo. Los pasos de la sección 3 se ejecutaron de verdad sobre una imagen limpia de Debian 13, tal como se describe en la sección 9.

## 1. Qué se instala

Un contenedor Docker (`cefa-tv-player`) que incluye la aplicación Spring Boot, Chromium en modo kiosco y, según el modo, su propio servidor gráfico. El instalador deja todo en `/opt/cefa-tv-player`, crea el servicio `systemd` que lo arranca con el equipo y muestra en pantalla un código de emparejamiento. No hay que generar ninguna clave previa en el servidor.

El reproductor solo hace conexiones salientes hacia el servidor: no hay que abrir puertos en el equipo ni en la red de la pantalla.

## 2. Requisitos

| Requisito | Detalle |
|---|---|
| Sistema | Debian o Ubuntu, como `root` o con `sudo`. Probado únicamente en Debian 13; el instalador está pensado también para Debian 12 y Ubuntu, pero esos no se han probado. |
| Arquitectura | `x86_64` o `arm64` (Raspberry Pi 4 o 5 con sistema de 64 bits). |
| Recursos | 2 GB de RAM como mínimo y unos 5 GB libres en disco (la imagen del reproductor ronda los 1,6 GB). |
| Red | Salida a internet para instalar Docker y construir la imagen, y acceso TCP al puerto `8080` del servidor CEFA TV. Para el descubrimiento automático, la pantalla y el servidor deben estar en la misma red local (UDP `47808`). |
| Pantalla | Un monitor conectado, salvo en el modo de pruebas `headless`. |
| Software previo | Solo `bash` y `git`. El instalador instala `curl`, Docker y lo demás si faltan. |

## 3. Instalación paso a paso

Se parte de un Debian recién instalado, sin nada más.

1. **Instalar `git`.** En un Debian mínimo no viene:

   ```bash
   apt-get update
   apt-get install -y git ca-certificates
   ```

2. **Descargar el repositorio.** El repositorio es público:

   ```bash
   git clone https://github.com/marcoslafoz/cefatv-player.git
   cd cefatv-player
   ```

3. **Ejecutar el instalador.** Si el servidor y la pantalla están en la misma red local, basta con:

   ```bash
   sudo ./install.sh
   ```

   Si el servidor está en otra red o prefieres no depender del descubrimiento, indica su dirección:

   ```bash
   sudo ./install.sh --server http://192.168.1.10:8080
   ```

   El instalador, por este orden:

   - comprueba que se ejecuta como `root` y que el sistema es Debian o Ubuntu;
   - instala `curl` si falta y, si no hay Docker, lo instala con el script oficial de Docker (pide confirmación, salvo con `--yes`);
   - detecta el modo de pantalla (ver sección 5);
   - construye la imagen del reproductor, lo que tarda unos minutos y se hace una sola vez;
   - escribe `/opt/cefa-tv-player/compose.yml` y `/opt/cefa-tv-player/.env`;
   - crea y habilita el servicio `cefa-tv-player.service` de `systemd` y habilita Docker en el arranque;
   - arranca el contenedor, espera a que responda y muestra el código de emparejamiento.

4. **Anotar el código.** Al terminar, el instalador muestra un recuadro como este (el mismo código aparece en la pantalla del reproductor):

   ```
   ==========================================================
     CODIGO DE EMPAREJAMIENTO:  WCN-PN2

     Introducelo en CEFA TV Server:
     Reproductores > Anadir reproductor
     Servidor: http://192.168.1.10:8080
     (el mismo codigo se muestra en la pantalla del reproductor)
   ==========================================================
   ```

5. **Emparejar en el servidor.** En CEFA TV Server, entra en **Dispositivos, Reproductores, Añadir reproductor**, escribe el código tal como aparece, pon un nombre y, si procede, el grupo y la sede, y pulsa Añadir. El reproductor pasa a estar en línea en unos segundos.

6. **Comprobar.** Desde el propio equipo:

   ```bash
   curl -s http://127.0.0.1:8081/api/status
   docker ps
   ```

   La respuesta debe mostrar `"phase":"ONLINE"` y el contenedor debe aparecer como `healthy`.

Desde ese momento no hay que volver a tocar el equipo: reintenta la conexión él solo, se recupera de caídas y arranca con el sistema.

## 4. Opciones del instalador

| Opción | Efecto |
|---|---|
| `--server URL` | Dirección del servidor (`http://IP:8080`). Si se omite, se busca en la red local. |
| `--name NOMBRE` | Nombre que se propone para la pantalla (por defecto, el nombre del equipo). El administrador puede cambiarlo al emparejar. |
| `--mode MODO` | `host`, `xorg` o `headless` (sección 5). Si se omite, se detecta. |
| `--image IMAGEN` | Usa una imagen de un registro en lugar de construirla desde el repositorio. |
| `--host-shell` | La terminal remota abre la shell del equipo, no la del contenedor. Ejecuta el contenedor privilegiado y con `pid: host`: úsala solo si la necesitas. |
| `--no-shell` | Desactiva la terminal remota. |
| `--dir RUTA` | Directorio de instalación (por defecto `/opt/cefa-tv-player`). |
| `--project NOMBRE` | Nombre del contenedor, el volumen y el servicio (por defecto `cefa-tv-player`). Permite varias instalaciones. |
| `--yes` | No pregunta nada. Instala Docker si falta. |
| `--uninstall` | Desinstala el reproductor (ver sección 7). |

Volver a ejecutar el instalador es seguro: conserva el emparejamiento.

## 5. Modos de pantalla

| Modo | Cuándo usarlo |
|---|---|
| `host` | El equipo ya tiene un entorno gráfico en marcha (un escritorio). Usa su servidor X. Se elige solo si existe el socket de X. |
| `xorg` | Equipo dedicado, sin escritorio. El contenedor arranca su propio Xorg a pantalla completa (se ejecuta privilegiado). Es lo que se elige en un Debian mínimo. |
| `headless` | Solo pruebas y servidores sin monitor: usa una pantalla virtual (Xvfb). El control remoto y las vistas previas funcionan, pero no se ve nada en un monitor. |

El reproductor elige la primera salida de vídeo conectada y adapta la resolución. La salida se puede cambiar desde el servidor, en los ajustes del reproductor, en **Salida de vídeo**.

## 6. Actualizar

Desde el repositorio clonado:

```bash
cd cefatv-player
sudo ./update.sh
```

Hace `git pull --ff-only`, reconstruye la imagen y recrea el contenedor sin perder el emparejamiento ni el contenido descargado. Espera a que el contenedor quede sano y, si la versión nueva no arranca, vuelve automáticamente a la anterior. Si ya estaba actualizado, no hace nada. Al terminar muestra la versión anterior y la nueva (por ejemplo `0.1.0 (build 7578e81)`).

Opciones: `--image IMAGEN` (descarga una imagen de un registro en vez de construirla), `--force` (recrea el contenedor aunque no haya cambios), `--timeout SEG` (espera máxima al contenedor sano, 300 por defecto), `--dir` y `--project`.

## 7. Desinstalar

```bash
cd cefatv-player
sudo ./uninstall.sh
```

Para y elimina el contenedor, la imagen, el volumen de datos (el emparejamiento y el contenido descargado), el servicio `systemd` y `/opt/cefa-tv-player`. Pide confirmación escribiendo `desinstalar`, salvo con `--yes`. No toca Docker.

- `--keep-data` conserva el volumen: al reinstalar, la pantalla sigue emparejada.
- `--remove-docker` desinstala también Docker del equipo, con todos sus contenedores. Úsalo solo si no hay nada más en él.

Tras desinstalar, borra también el reproductor en el servidor (**Dispositivos, Reproductores**) si no vas a reutilizarlo.

## 8. Resolución de problemas

| Síntoma | Causa probable y solución |
|---|---|
| La pantalla muestra "Buscando el servidor" o "Conectando con el servidor" | El reproductor no encuentra el servidor. Reintenta solo con esperas de 2 a 60 s. Reinstala indicando `--server http://IP:8080`, o comprueba que el servidor está en marcha y que el puerto 8080 es alcanzable (`curl http://IP:8080/api/v1/public/info`). |
| El código ha caducado | Dura 15 minutos y el reproductor pide otro solo. Usa el que se vea en pantalla en ese momento. |
| `El demonio de Docker no responde` | Docker no está en marcha. En un equipo con `systemd`: `systemctl enable --now docker` y repite el instalador. |
| El instalador falla al instalar Docker | Comprueba la salida a internet del equipo y vuelve a ejecutarlo: es idempotente. |
| Pantalla negra con el monitor conectado | Comprueba el modo (`grep DISPLAY_MODE /opt/cefa-tv-player/.env`). En un equipo sin escritorio debe ser `xorg`. Revisa `docker logs cefa-tv-player`. |
| Desde el servidor se ve la pantalla pero el monitor no | El reproductor puede estar usando otra salida de vídeo. Cámbiala en los ajustes del reproductor, en **Salida de vídeo**. |
| El reproductor aparece "Desactualizado" | Ejecuta `sudo ./update.sh` en ese equipo. |
| Se borró el reproductor en el servidor y la pantalla muestra un código nuevo | Es lo esperado: el servidor revocó su credencial y vuelve a pedir emparejamiento. Introduce el código nuevo si quieres darlo de alta otra vez. |

Comandos útiles:

```bash
docker logs -f cefa-tv-player
curl -s http://127.0.0.1:8081/api/status
cat /opt/cefa-tv-player/.env
systemctl status cefa-tv-player
```

## 9. Prueba realizada sobre un Debian limpio

La instalación se probó el 16 de octubre de 2026 sobre una imagen oficial `debian:trixie` (Debian 13, `aarch64`) ejecutada con Docker. Sirve para reproducir el proceso sin un equipo físico.

1. **Preparar el Debian limpio.** Con un volumen para los datos de Docker del contenedor (para que el almacenamiento funcione) y en modo privilegiado, porque dentro va a correr Docker:

   ```bash
   docker volume create debian-player-docker
   docker run -d --name debian-player --hostname pantalla-debian --privileged \
     -v debian-player-docker:/var/lib/docker \
     -v /ruta/al/repositorio/cefa-tv-player:/src:ro \
     debian:trixie sleep infinity
   ```

   Esa imagen solo trae `bash`: no hay `git`, `curl`, `sudo`, `systemctl` ni Docker.

2. **Instalar `git` y copiar el repositorio** (en la prueba se copió el repositorio local en lugar de clonarlo de GitHub, para probar los cambios que aún no estaban subidos):

   ```bash
   docker exec debian-player sh -c 'apt-get update -qq && apt-get install -y -qq git ca-certificates'
   docker exec debian-player sh -c 'cp -a /src /root/cefatv-player'
   ```

3. **Primera ejecución del instalador.** Instaló `curl` y Docker (versión 29.9.1) con el script oficial y terminó con el aviso `El demonio de Docker no responde`. Es lo esperado en este contenedor, que no tiene `systemd` para arrancar Docker. En un Debian real, `systemd` lo arranca y este paso no ocurre.

   ```bash
   docker exec debian-player sh -c 'cd /root/cefatv-player && ./install.sh --yes --mode headless --server http://host.docker.internal:8080'
   ```

4. **Arrancar Docker a mano** (solo por no haber `systemd`):

   ```bash
   docker exec debian-player sh -c '(nohup dockerd > /var/log/dockerd.log 2>&1 &); sleep 12; docker info | grep "Server Version"'
   ```

5. **Segunda ejecución del instalador**, que continuó donde se quedó: construyó la imagen del reproductor (unos 3 minutos en el equipo de la prueba), creó `/opt/cefa-tv-player`, avisó de que el equipo no usa `systemd` (el contenedor se reinicia solo mientras Docker esté activo), arrancó el contenedor y mostró el código de emparejamiento y `Version instalada: 0.1.0 (build 7578e81)`.

6. **Emparejar.** Con el código mostrado se hizo **Añadir reproductor** en el servidor. El reproductor pasó a `phase: ONLINE` y el servidor lo listó en línea con su nombre de equipo (`pantalla-debian`), resolución `1920x1080` y versión `0.1.0`.

Qué no cubre esta prueba, porque no hay monitor, `systemd` ni red local reales: el modo `host` o `xorg` con pantalla física, el servicio `systemd` y el descubrimiento automático por broadcast UDP. En la prueba se usó `--mode headless` y `--server`.

El mismo escenario sobre un Debian físico con `systemd` no necesita los pasos 4 ni el primer intento fallido del paso 3: basta con los pasos de la sección 3.

Para limpiar la prueba:

```bash
docker rm -f debian-player
docker volume rm debian-player-docker
```
