# Prueba de dual boot: AraCast 4.x en un player antiguo (2.x, 32 bits)

Prueba hecha el 19 y 20 de octubre de 2026 en el banco de pruebas (`ARAPROD03`, `10.21.34.33`). La tarea del Kanban es "Actualizar players antiguos (2.x, 32 bits) al sistema moderno (4.x) para renderizar webs actuales".

## Resultado

- El sistema moderno 4.x (Gentoo x86_64, kernel 6.1.66, AraCastPlayer 4.0.4, WebKitGTK 4.1) **arranca y funciona en el hardware antiguo** (Celeron N2840, BIOS con GRUB legacy) desde una carpeta del mismo disco, sin tocar el sistema antiguo.
- Una plantilla de URL con `https://hermes.cefa.es/es/`, que en la 2.6 sale sin estilos, **se ve correctamente** en la 4.x (formato, tipografía, botones y logo).
- Reproducción de vídeo, API de comandos (5401), FTP (5481) y descubrimiento (30333) funcionan igual que en los players 4.x de oficinas.
- **Identidad propia del equipo.** El primer arranque usó la MAC (`mainmac.txt`) y el `license.lic` copiados del player de origen. Después se puso en `mainmac.txt` la MAC real del banco (`eth0`) y se reinició: la aplicación arranca igual, `Player.getVersion` devuelve la MAC real, el log no contiene ninguna línea de licencia y reproduce la programación con normalidad. El `license.lic` que genera al arrancar es idéntico byte a byte al del origen, así que no se deriva de la MAC. Esto no demuestra que el fabricante dé por válida la 4.x en este equipo: el servidor de actualizaciones y licencias está caído y la licencia es un fichero cifrado con clave del fabricante. Se debe consultar a Tecco antes de cualquier despliegue.

## Estado en que quedó el banco

| Elemento | Valor |
|---|---|
| Sistema antiguo | Sigue intacto en `/dev/sda1`; es la primera entrada de GRUB, aunque ya no la predeterminada. |
| Sistema 4.x | Carpeta `/moderno` de `/dev/sda1` (12 GB), arrancado con la tercera entrada de GRUB. |
| Kernel e initramfs | `/boot/vmlinuz-6.1.66-gentoo-x86_64` y `/boot/initramfs-6.1.66-banco.img`; el anterior initramfs queda como `.v1`. |
| GRUB | `/boot/grub/grub.conf` con `timeout 5` y `default 2` (la entrada 4.x arranca por defecto desde el 20/10/2026); copias `grub.conf.orig-antes-dualboot`, `grub.conf.v1-dualboot` y `grub.conf.v2-antes-default2`. Para volver al sistema antiguo por defecto, `default 0`. |
| Red del 4.x | `eth0` con `10.21.34.33/23` fija; `dhcpcd` está activo y añade una segunda IP por DHCP. |
| Contraseña SSH | En el 4.x entra la primera de `ARACAST_SSH_PASSWORDS`; en el 2.6 entraba la segunda. |
| Identidad | `mainmac.txt` con la MAC real del banco; copias `mainmac.txt.origen-64` y `license.lic.origen-64` en `/root/.aracast/data`. |
| Restos de la prueba | Plantilla `prueba-web-4x`, script de diagnóstico `/moderno/usr/local/sbin/dbg-dump.sh` con su línea `dbg::sysinit` en `/moderno/etc/inittab`, y `dmesg_level="7"`. |

## Procedimiento

Todo se hace desde un Mac con acceso SSH a ambos players, sin escribir contraseñas en ficheros (se leen del `.env` en el momento y se pasan por `SSHPASS`).

1. **Copiar el sistema moderno** a `/moderno` del player antiguo con `tar` en streaming: `tar -C / --one-file-system --numeric-owner -cpf -` en origen, con `--exclude=./proc --exclude=./sys --exclude=./dev --exclude=./run --exclude=./tmp --exclude=./var/tmp`, y `tar -C /moderno -xpf -` en destino. Solo lectura sobre el origen; tarda unos 25 minutos para 12 GB. Los sockets se omiten con un aviso inofensivo.
2. **Crear los puntos de montaje que el `tar` omite.** Los `--exclude` quitan también los directorios, y sin ellos el sistema arranca sin `/dev` ni `/run` (OpenRC falla con `/dev/null: No such file or directory`). En `/moderno`: `mkdir dev proc sys run media`, `mkdir tmp var/tmp` con modo `1777`, y los nodos `dev/null` (`c 1 3`, 666) y `dev/console` (`c 5 1`, 600).
3. **Adaptar `/moderno/etc`** (con copia `.orig` de cada fichero):
   - `conf.d/net`: IP del player en `eth0` y ruta por defecto; sin el módulo `wpa_supplicant`.
   - `hostname` y `conf.d/hostname`: el del player destino. El origen trae la IP y el nombre de producción: si no se cambian, el equipo duplica la IP del origen.
   - `fstab`: `/dev/sda1` como raíz y sin las particiones del origen (`mmcblk0*`).
   - `runlevels/default`: quitar `net.enp1s0` y `net.enp2s0`.
4. **Initramfs parcheado.** El del origen es de dracut con systemd, generado para el hardware del origen y en LZ4 *legacy* (sin marca de fin: no se puede anexar un cpio al archivo comprimido). Se descomprime a cpio crudo, se le concatena un cpio `newc` propio (propietario root) y se vuelve a comprimir con `lz4 -l -9`. El cpio propio contiene:
   - `etc/cmdline.d/95root-dev.conf` con `root=/dev/sda1 rootfstype=ext4 rootflags=rw,relatime`; el driver ext4 monta bien la partición ext3.
   - `lib/dracut/hooks/pre-pivot/60-subdir-root.sh`, que hace `mount --bind /sysroot/moderno /sysroot` antes de cambiar de raíz.
   - Cuatro ficheros que sobrescriben, con el mismo nombre (con las barras escritas literalmente como `\x2f`), los hooks `initqueue/finished/devexists-…` y `emergency/80-…` de los dos UUID del origen (la partición EFI y otro disco), que de otro modo hacen esperar al arranque hasta caer en la shell de dracut.
   - El initramfs del origen ya trae `ahci`, `libahci`, `ata_piix`, `sd_mod`, `ext4`, `jbd2` y `mbcache`.
5. **Subir** el kernel (`/boot/vmlinuz-6.1.66-gentoo-x86_64` del origen) y el initramfs a `/boot` del player antiguo con nombres nuevos, y comprobar los md5.
6. **Entrada de GRUB** como tercera, sin cambiar `default 0`, con copia previa de `grub.conf`:

   ```
   title ARACASTPlayer 4.x (prueba dualboot, 64 bits)
   root (hd0,0)
   kernel /boot/vmlinuz-6.1.66-gentoo-x86_64 root=/dev/sda1 rootfstype=ext4 rootflags=rw,relatime rw net.ifnames=0 vmalloc=100M iommu=soft usb-storage.delay_use=5 intel_idle.max_cstate=1 rd.retry=60
   initrd /boot/initramfs-6.1.66-banco.img
   ```

   Sin `quiet` ni `splash`, y `net.ifnames=0` para que la tarjeta se llame `eth0`. Un `ro` en la línea acaba concatenado tras `rootflags=rw` y deja la raíz en solo lectura, por eso va `rw`. GRUB legacy arranca sin problema el bzImage x86_64.
7. **Elegir la entrada en el arranque** (con teclado, `timeout 5`) o poner `default 2` para que arranque sola. Para volver al sistema antiguo, `default 0` o elegir la primera entrada. Desde el 4.x el `/boot` real no se ve (queda tapado por el bind de `/moderno`): hay que montar `/dev/sda1` en otra carpeta para editar `grub.conf`.
8. **No reiniciar la aplicación matándola.** `autostart.sh` termina con `sleep 100; reboot`, así que cerrar `AraCastPlayer.pyc` reinicia el equipo.

## Cómo se diagnosticó el arranque a ciegas

El 4.x silencia la consola (`dmesg_level="1"`) y no deja logs hasta que `/` es escribible. Se añadió a `/moderno/etc/inittab` una línea `dbg::sysinit:/bin/sh -c "/usr/local/sbin/dbg-dump.sh &"` que, en segundo plano a los 90 y a los 200 segundos, vuelca `dmesg`, `ip a`, `ip r`, `rc-status -a` y `ps ax` a `/var/log/dbg-*.txt`. Reiniciando en el sistema antiguo se leen en `/moderno/var/log/`. Fue lo que mostró los errores `/dev/null` y `/run/openrc` inexistentes.

## Fallos encontrados

| Síntoma | Causa | Arreglo |
|---|---|---|
| dracut espera 68 s y cae a `dracut:/#` con `/dev/disk/by-uuid/… does not exist` | Hooks del initramfs que esperan discos del player de origen | Sobrescribirlos con scripts vacíos en el cpio propio |
| El arranque pasa de `Switching root` y la pantalla se queda en `…Attached SCSI removable disk`, sin red ni consola | `/moderno` sin `dev`, `proc`, `sys`, `run` ni `tmp` | Crearlos (paso 2) |
| Raíz en solo lectura | `ro` en la línea de GRUB | Usar `rw` |

## Pendiente

- Decidir qué hacer con la **licencia y la identidad** del player: el 4.x funciona ahora con la MAC y la licencia del player de origen. Cualquier despliegue real en más players antiguos depende de cómo licencie el fabricante (Aracast, distribuido por Tecco) cada equipo, y eso no está resuelto.
- Quitar `dhcpcd` o su configuración si el player debe tener solo la IP fija (la segunda IP por DHCP hace que el descubrimiento UDP 30333 no conteste desde la IP esperada).
- Retirar del banco los restos de la prueba si no se va a seguir.
- Qué ocurre con las actualizaciones automáticas (`auto_update` aparece activo en `System.getConfiguration`) y con los servicios que contacten con el fabricante.
