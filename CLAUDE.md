# Idioma

Responde siempre en español, en todas las respuestas de esta conversación y de cualquier conversación futura sobre este proyecto, sin importar en qué idioma esté escrito el mensaje del usuario o el contenido que se esté leyendo (código, documentación, capturas, etc.).

# Contexto

Este repositorio es **CEFA TV Player**. Antes de trabajar en cualquier tarea, lee el contexto de la aplicación en `docs/`:

- [`docs/llm/context/context.md`](docs/llm/context/context.md) — qué es la aplicación, para quién y por qué existe, sin entrar en detalle técnico.
- [`docs/llm/context/reference-data.md`](docs/llm/context/reference-data.md) — datos de referencia del proyecto: URLs de entornos, servidores (IP, usuario, rutas), sistemas externos y dónde viven las credenciales (nunca sus valores).
- [`docs/llm/context/architecture.md`](docs/llm/context/architecture.md) — arquitectura de CEFA TV, contrato de APIs y protocolo emisor-receptor. **Antes de tocar cualquier API, protocolo o modelo de datos.** Existe la misma copia en `cefa-tv-server` y `cefa-tv-player`: cualquier cambio se aplica en las dos.

# Notion

Este proyecto tiene su carpeta en Notion (`CEFA Informática / Projects / CEFA TV`), con un Kanban de tareas y la Documentación. Notion es la fuente de verdad para las tareas: antes de empezar a trabajar consulta el Kanban, y al terminar actualiza el estado de la tarea. Las normas completas están en [`notion.md`](docs/llm/rules/common/notion.md) y los enlaces del proyecto en la sección "Notion" de [`reference-data.md`](docs/llm/context/reference-data.md).

# Reglas

Todo lo dirigido a la IA vive en `docs/llm/`, organizado en cuatro carpetas:

- `docs/llm/rules/` — biblioteca de reglas de estilo y convenciones comunes a todos los proyectos de CEFA, organizada en una carpeta por ámbito. Todas están instaladas; **aplica solo las que correspondan** a lo que estás tocando (si el repo no usa Next.js, ignora la carpeta `nextjs/`):
  - `common/` — válidas para cualquier stack:
    - [`general.md`](docs/llm/rules/common/general.md) — reglas globales de código, emojis, secretos y documentación. **Siempre.**
    - [`commits.md`](docs/llm/rules/common/commits.md) — formato y política de commits. **Siempre que se vaya a comitear.**
    - [`assets.md`](docs/llm/rules/common/assets.md) — formato y optimización de imágenes (WebP). Aplícala al añadir o cambiar imágenes.
    - [`makefile.md`](docs/llm/rules/common/makefile.md) — objetivos estándar y convenciones de los Makefiles. Aplícala al crear o modificar un `Makefile`.
    - [`notion.md`](docs/llm/rules/common/notion.md) — estructura de la carpeta del proyecto en Notion, uso del Kanban y de la Documentación. Aplícala al empezar o terminar una tarea y al cambiar algo que deba documentarse.
  - `nextjs/` — frontend con Next.js / React:
    - [`nextjs.md`](docs/llm/rules/nextjs/nextjs.md) — pnpm, imports, shadcn e i18n. Aplícala al trabajar con código React/Next.js.
    - [`ui-patterns.md`](docs/llm/rules/nextjs/ui-patterns.md) — patrones de interfaz de las aplicaciones web de CEFA (`EnhancedTable`, navegación, buscador global). Aplícala solo si el proyecto tiene esos componentes; si no, pregunta antes de crearlos.
  - `springboot/` — backend Java:
    - [`springboot.md`](docs/llm/rules/springboot/springboot.md) — paquetes por dominio, Javadoc y caché. Aplícala al trabajar con código Java/Spring.
  - `astro/` — webs y landings con Astro:
    - [`astro.md`](docs/llm/rules/astro/astro.md) — convenciones de Astro: **sin comentarios de línea**, pnpm, estructura, imports, contenido, variables de entorno. Aplícala al trabajar con código Astro.
    - [`seo.md`](docs/llm/rules/astro/seo.md) — SEO: metadatos, semántica, sitemap, datos estructurados y rendimiento. Aplícala al crear o modificar cualquier página pública de un sitio Astro.
- `docs/llm/workflows/` — procesos paso a paso que debe seguir la IA:
  - [`commits.md`](docs/llm/workflows/commits.md) — cómo ejecutar un commit.
- `docs/llm/context/` — contexto adicional para la IA sobre la aplicación.
- `docs/llm/tasks/` — planes de implementación de tareas concretas (se añaden aquí según surjan, y cada uno se enlaza desde este fichero).

# Desarrollo local y Makefiles

Cada proyecto dispone de un `Makefile` con comandos estandarizados para unificar las operaciones cotidianas. Convenciones completas en [`makefile.md`](docs/llm/rules/common/makefile.md).

Comandos estándar:
- `make help` — Muestra los comandos disponibles y su descripción.
- `make build` — Construye los artefactos locales.
- `make up` — Levanta los servicios con Docker Compose.
- `make down` — Detiene los servicios de Docker Compose.
- `make rebuild` — Reconstruye las imágenes Docker y vuelve a levantar los servicios.
- `make format` — Aplica el formateador configurado.
- `make lint` — Ejecuta comprobaciones de lint y estilo sin modificar archivos.
- `make test` — Ejecuta la batería de pruebas disponible.
- `make check` — Ejecuta de forma conjunta validaciones de formato, lint y pruebas (imprescindible antes de PR).
- `make clean` — Elimina artefactos temporales generados por la compilación.

# Ante la duda, pregunta

Nunca asumas por tu cuenta decisiones importantes (nombres, dominios, estructura, qué se
conserva o descarta, alcance de una tarea, etc.). Si algo no está explícito o hay más de una
opción razonable, pregunta al usuario antes de actuar en vez de dar por hecho la respuesta más
probable.

# Mantenimiento de la documentación

Cualquier cambio en `docs/` (crear, editar, eliminar o mover un fichero) debe reflejarse de
inmediato en `CLAUDE.md` y en cualquier otro fichero que lo referencie (`AGENTS.md`, otros
ficheros de `docs/llm/`, etc.), para que ninguna referencia quede rota ni desactualizada.

# Estándares CEFA

Las reglas de `docs/llm/rules/` y el workflow `docs/llm/workflows/commits.md` proceden de la
skill `cefa-dev-skills` y son comunes a todos los proyectos de CEFA. Para actualizarlos a la última
versión, usa la skill (sincronizar) en lugar de editarlos a mano; los cambios locales en esos
ficheros se perderían en la siguiente sincronización. Lo específico de este proyecto va en
`docs/llm/context/`, `docs/llm/tasks/`, `docs/llm/rules/project/` (reglas que solo valen para este repositorio) y en las secciones propias de este `CLAUDE.md`.

Versión de `cefa-dev-skills` aplicada: 2.2.0
