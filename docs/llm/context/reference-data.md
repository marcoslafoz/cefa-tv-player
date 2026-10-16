# Datos del proyecto

Datos de referencia sobre CEFA TV Player (URLs, servidores, sistemas externos...). Este fichero es la única fuente de estos datos: los demás documentos lo enlazan en vez de copiarlos.

Nunca escribas aquí contraseñas, tokens, claves privadas ni cualquier otro secreto. Para cada credencial indica solo dónde vive.

## Entornos

| Entorno | URL |
|---|---|
| Producción | Pendiente de definir |

## Notion

| Elemento | Enlace |
|---|---|
| Carpeta del proyecto (`CEFA Informática / Projects / CEFA TV Player`) | Pendiente de definir |
| Kanban | Pendiente de definir |
| Documentación | Pendiente de definir |

## Servidores

| Entorno | Host / IP | Usuario | Acceso | Ruta del proyecto | Servicios |
|---|---|---|---|---|---|
| Producción | Pendiente de definir | Pendiente de definir | Pendiente de definir | Pendiente de definir | Pendiente de definir |

## Sistemas externos

Ninguno por ahora. El emisor y los receptores solo hablan entre sí (ver [`architecture.md`](./architecture.md)).

## Credenciales y secretos

Los secretos del servidor (contraseñas de PostgreSQL y MinIO, `JWT_SECRET`, contraseña del administrador inicial) viven en el fichero `.env` de cada proyecto (no versionado); la plantilla es `.env.template`. El reproductor guarda su token de dispositivo en el volumen `/data` del contenedor.
