# Reglas — SEO (Astro)

El SEO es un requisito de toda página pública de un sitio Astro, no un extra. Estas reglas se
aplican al crear o modificar cualquier página, layout o componente que afecte a lo que ven los
buscadores o a lo que se comparte. Las convenciones del stack están en [`astro.md`](./astro.md).

## Metadatos de cada página

- Centraliza los metadatos en un único componente (`SEOMeta`, `BaseHead` o equivalente) que usan
  todos los layouts. No escribas `<title>` ni `<meta>` sueltos en las páginas.
- Cada página tiene un `<title>` y una `meta description` **únicos y descriptivos**: título de unos
  50 a 60 caracteres y descripción de unos 120 a 160, con la palabra clave principal de la página.
- Define siempre la URL canónica (absoluta) y el atributo `lang` del `<html>`.
- Open Graph y Twitter Card completos: `og:title`, `og:description`, `og:type`, `og:url`,
  `og:image` y `twitter:card`. La imagen para compartir mide 1200×630 y tiene su `alt`.
- Las páginas que no deben indexarse (borradores, gracias, privadas) llevan `noindex` y no entran
  en el sitemap.

## Estructura y semántica

- Una sola `h1` por página, y una jerarquía `h2`, `h3`... sin saltos de nivel.
- HTML semántico: `header`, `nav`, `main`, `section`, `article` y `footer` en lugar de `div`
  genéricos.
- Los enlaces son `<a href>` reales con texto descriptivo (nada de "pulse aquí"). Una sección
  importante es una página con URL propia, no solo un ancla `#`.
- Toda imagen informativa lleva `alt` descriptivo; las decorativas llevan `alt=""`.

## URLs, sitemap y robots

- URLs en minúsculas y kebab-case, estables y sin extensión. Si una URL cambia, añade una
  redirección 301 desde la antigua.
- Configura `site` en `astro.config.mjs` y genera el sitemap con `@astrojs/sitemap`. Al añadir,
  quitar o renombrar una página, comprueba que el sitemap la refleja.
- `public/robots.txt` existe e incluye la URL del sitemap.
- Hay una página 404 propia y útil, con enlaces de vuelta al sitio.
- Mantén un criterio único de barra final (`trailingSlash` en la configuración) y no mezcles las
  dos formas.

## Varios idiomas

- Una URL por idioma, cada una con su `lang`, y alternates `hreflang` entre todas las versiones
  más `x-default`.
- Título, descripción y contenido se traducen; no se duplican en el idioma original.

## Datos estructurados

- Añade JSON-LD adecuado a cada tipo de página: `Organization` y `WebSite` en el sitio,
  `BreadcrumbList` en las páginas interiores y `Article`, `Product` o `FAQPage` cuando
  corresponda.
- Genera el JSON-LD desde los mismos datos que el contenido visible, de modo que nunca se
  contradigan. Valídalo con la herramienta de resultados enriquecidos de Google.

## Rendimiento (Core Web Vitals)

- Estático por defecto: SSR solo donde haga falta. Hidrata lo mínimo, con `client:visible` o
  `client:idle` antes que `client:load`.
- Imágenes con `astro:assets` (`Image` o `Picture`), en WebP según [`assets.md`](../common/assets.md)
  y con `width` y `height` para evitar saltos de maquetación. La imagen principal (LCP) se carga con
  `loading="eager"` y `fetchpriority="high"`; el resto con `loading="lazy"`.
- Fuentes autoalojadas, con `font-display: swap` y precarga de las críticas. No cargues fuentes ni
  scripts de terceros innecesarios.
- No introduzcas dependencias de cliente pesadas para algo que se resuelve con HTML y CSS.

## Checklist de calidad (además de la de `astro.md`)

- [ ] `title` y `description` únicos, con canonical, `lang` y Open Graph
- [ ] Una `h1`, jerarquía de títulos correcta y HTML semántico
- [ ] `alt` en todas las imágenes, con `width` y `height`
- [ ] Sitemap y `robots.txt` actualizados si cambian las páginas
- [ ] JSON-LD válido y coherente con el contenido
- [ ] `hreflang` y `x-default` si el sitio tiene varios idiomas
- [ ] Sin regresiones en Lighthouse (SEO, rendimiento y accesibilidad) en las páginas tocadas
