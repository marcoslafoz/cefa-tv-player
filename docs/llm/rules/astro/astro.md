# Reglas — Astro

Reglas específicas para los proyectos construidos con Astro (webs corporativas, landings y sitios
de contenido). Las reglas globales están en [`general.md`](../common/general.md), el SEO tiene su
propia regla en [`seo.md`](./seo.md) y el formato de commits en
[`commits.md`](../common/commits.md).

## Comentarios

Esta regla es más estricta que la de [`general.md`](../common/general.md) y prevalece sobre ella en
los proyectos Astro.

- **No uses comentarios de línea (`//`)** en ningún sitio: frontmatter de `.astro`, scripts de
  `<script>`, ficheros `.ts`, `.tsx` y `.mjs`.
- Tampoco dejes comentarios HTML (`<!-- -->`) en las plantillas: Astro los emite tal cual en el
  HTML final, así que añaden peso y publican texto interno.
- Si una decisión no obvia necesita explicación, escríbela en el mensaje de commit o en `docs/`,
  no en el código. Si el código necesita un comentario para entenderse, reescríbelo.

## Gestor de paquetes

- Usa siempre **pnpm**, nunca `npm` ni `yarn`: `pnpm install`, `pnpm run <script>`, `pnpm add` y
  `pnpm dlx` en lugar de `npx`. Respeta la versión fijada en `packageManager` de `package.json`.
- Los paquetes que necesitan scripts de instalación se autorizan uno a uno (`allowBuilds` de
  `pnpm-workspace.yaml`).

## Estructura

- `src/pages/` solo contiene rutas. La lógica y el marcado viven en componentes de
  `src/components/`: una página es un envoltorio fino, y nunca se duplica marcado entre rutas (por
  ejemplo, entre idiomas).
- `src/layouts/` para los layouts, `src/components/ui/` para las piezas reutilizables y
  `src/lib/` para la lógica compartida.
- Todo el acceso al contenido pasa por una única capa (`src/lib/content.ts` o equivalente), no por
  lecturas sueltas de ficheros en los componentes.
- Los endpoints SSR (`src/pages/api/`) son la excepción: el resto del sitio es estático.

## Imports y componentes

- Usa el alias `@/` para imports (`@/components/ui/Button.astro`), nunca rutas relativas (`./`,
  `../`).
- Antes de crear marcado nuevo, reutiliza los componentes de `src/components/ui/`. Si un patrón
  visual se repite, abstráelo ahí.
- Prefiere componentes `.astro`. Usa React (`.tsx`) solo cuando haga falta interactividad en el
  cliente, con la directiva `client:*` más perezosa posible (`client:visible` o `client:idle` antes
  que `client:load`).

## Estilos

- Si el proyecto usa Tailwind, escribe las utilidades en el propio marcado y usa los colores y
  tokens de marca definidos en la hoja global en lugar de valores hexadecimales sueltos.
- Combina clases condicionales con `clsx` y `tailwind-merge`, no con concatenación manual.

## Contenido y texto visible

- Nunca hardcodees texto visible en los componentes: sale de la capa de contenido o de los
  ficheros de traducción del proyecto.
- En sitios con varios idiomas, un cambio de contenido se aplica en todos los idiomas a la vez, y
  los enlaces internos se construyen con una función única (p. ej. `getLocalePath`), nunca
  concatenando el prefijo a mano.
- Las imágenes y los PDFs siguen [`assets.md`](../common/assets.md): WebP optimizado, y servidos
  desde `public/` o importados con `astro:assets`.

## Variables de entorno

- Las variables secretas de servidor se declaran en el esquema `env` de `astro.config.mjs` y se
  importan desde `astro:env/server`, nunca con `import.meta.env`, que las incrustaría en el build.
- Solo las que empiezan por `PUBLIC_` pueden usarse en el navegador. Las nuevas se añaden también a
  `.env.example` y a la configuración de despliegue.

## Alcance de commits

El formato general está en [`commits.md`](../common/commits.md). En este stack, el alcance es la
sección o área afectada (ej: `hero`, `contact`, `seo`, `docker`).

## Checklist de calidad (además de la de `general.md`)

- [ ] Sin comentarios de línea (`//`) ni comentarios HTML en las plantillas
- [ ] Imports con `@/`, sin rutas relativas
- [ ] Sin texto visible hardcodeado
- [ ] Interactividad en cliente solo donde hace falta, con `client:visible` o `client:idle`
- [ ] Checklist de [`seo.md`](./seo.md) cumplida en las páginas nuevas o modificadas
- [ ] `pnpm run build` y `pnpm run lint` sin errores
