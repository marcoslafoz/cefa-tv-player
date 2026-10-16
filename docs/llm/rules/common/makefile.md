# Reglas — Makefiles

Cada proyecto de CEFA expone las operaciones cotidianas mediante `make`, con **los mismos
objetivos y el mismo significado en todos los repositorios**. Así cualquier persona (o la IA) sabe
cómo construir, levantar o validar un proyecto sin leer su documentación. Las plantillas de
partida están en la skill `cefa-dev-skills` (`assets/makefiles/`).

## Organización

- Un `Makefile` por proyecto desplegable (backend, frontend...), en su carpeta.
- Si el repositorio es un monorepo con varios proyectos, un `Makefile` en la raíz que **solo
  orquesta**: cada objetivo delega en los de los proyectos con `$(MAKE) -C <proyecto> <objetivo>`.
  La raíz no contiene lógica de build propia.
- Un repositorio con un único proyecto lleva solo el `Makefile` de su stack, en la raíz.

## Objetivos estándar

Todo `Makefile` de proyecto define estos objetivos, aunque alguno sea un mensaje informativo
(p. ej. `test` en un frontend sin suite de pruebas):

| Objetivo | Qué hace |
|---|---|
| `help` | Lista los objetivos con su descripción (objetivo por defecto recomendado). |
| `build` | Construye los artefactos locales (sin tests en backend). |
| `up` | Asegura la red Docker compartida (`<proyecto>-net`) y levanta los servicios con Docker Compose. |
| `down` | Detiene los servicios de Docker Compose. |
| `rebuild` | Reconstruye las imágenes y vuelve a levantar los servicios. |
| `format` | Aplica el formateador (Spotless en Spring Boot, Prettier en Next.js). |
| `lint` | Comprueba estilo/lint **sin modificar** ficheros. |
| `test` | Ejecuta las pruebas. |
| `check` | `lint` + `test` (+ comprobación de formato en frontend). Imprescindible antes de abrir una PR. |
| `clean` | Elimina artefactos de compilación temporales. |

Objetivos opcionales de proyecto: `run` / `dev` (arranque local sin Docker), `logs` (seguir logs de
los contenedores), `format-check` (comprobar formato sin modificar, usado por `check`). Solo la
raíz de un monorepo puede añadir `prune` (`docker system prune -af`, destructivo: nunca desde
proyectos individuales ni encadenado desde otro objetivo).

## Convenciones

- Cada objetivo lleva un comentario `## descripción` en la misma línea: `help` lo extrae con
  `grep`/`awk`, así que el listado nunca se desincroniza. Todo objetivo va en `.PHONY`.
- Descripciones y mensajes de `echo` en español, comandos silenciados con `@`.
- `APP_NAME := <proyecto>` al inicio y usado en los mensajes; la red Docker se llama
  `<proyecto>-net` y se crea solo si no existe
  (`docker network inspect X >/dev/null 2>&1 || docker network create X`).
- Backend Java: siempre `./mvnw` (wrapper), nunca un Maven global. Frontend: siempre `pnpm`
  (ver [`nextjs.md`](../nextjs/nextjs.md), si existe en este repositorio).
- `check` debe fallar con código distinto de cero si algo falla, para poder usarse en CI.
- No metas en el `Makefile` secretos ni URLs de entornos; viven en `.env` (no versionado).
- Añadir o renombrar un objetivo estándar es un cambio de estándar: se hace en la skill
  `cefa-dev-skills`, no en un solo repositorio.

## Checklist de calidad (además de la de `general.md`)

- [ ] Todos los objetivos estándar existen y se comportan como en la tabla
- [ ] `make help` lista todos los objetivos con descripción
- [ ] `make check` pasa antes de comitear
- [ ] `CLAUDE.md` refleja los objetivos del proyecto
