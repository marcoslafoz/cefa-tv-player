# CEFA TV Player

## Qué es

Receptor de CEFA TV. Es una aplicación Spring Boot que se ejecuta en Docker en cada pantalla, se registra en el emisor con una clave, descarga la programación y la reproduce a pantalla completa en Chromium en modo kiosco.

## Por qué existe

Permite que cualquier equipo con pantalla (mini PC, Raspberry Pi) se convierta en una pantalla de CEFA TV con un único script de instalación, sin configurar direcciones IP.

## Para quién

Técnicos de CEFA que instalan pantallas. El contenido lo gestiona el equipo que usa `cefa-tv-server`.

## Qué resuelve

- Instalación con un script que solo pide la clave de alta.
- Descubrimiento automático del servidor en la red local.
- Reproducción sin conexión: guarda programación y ficheros en disco y decide con su reloj local.
- Recuperación de programaciones enviadas mientras estaba desconectado.
- Control remoto de ratón y teclado desde el servidor.

## Documentos relacionados

- [`architecture.md`](./architecture.md) — arquitectura, contrato de APIs y protocolo con el servidor.
- [`reference-data.md`](./reference-data.md) — datos varios de referencia (URLs de entornos, sistemas externos, etc.).
