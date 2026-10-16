# Reglas — Patrones de interfaz (aplicaciones web de CEFA)

Patrones visuales y de navegación para mantener coherentes las aplicaciones web de CEFA construidas
con Next.js. Complementan a [`nextjs.md`](./nextjs.md); las reglas globales están en
[`general.md`](../common/general.md).

Aplícalas solo si el proyecto dispone de los componentes y ficheros que se citan aquí
(`EnhancedTable`, `lib/nav-config.ts`, `lib/search-index.ts`...). Si el proyecto no los tiene
todavía, no los inventes por tu cuenta: pregunta al usuario si debe crearlos siguiendo estos
patrones o si el proyecto usa otro enfoque.

## Patrones de interfaz y consistencia visual

Para que la aplicación se mantenga coherente de cara al usuario en futuras implementaciones, sigue siempre estos patrones establecidos:

- **Listados de datos (tablas):** Nunca crees tablas desde cero o `Table` básicas si vas a mostrar listados (usuarios, firewalls, inventario, etc.). Utiliza siempre el componente `EnhancedTable` (`components/shared/tables/enhanced-table.tsx`).
  - Los buscadores o filtros deben inyectarse directamente en la cabecera de la tabla mediante la propiedad `filters` de `EnhancedTable` en formato de inputs compactos, en lugar de crear formularios gigantes aislados.
  - Los filtros de selección (estado, tipo, categoría…) son siempre **multi-valor**: usa `useTableMultiFilter` (`hooks/use-table-multi-filter.ts`) + `TableMultiSelectFilter` dentro del `filterPanel`. Varios filtros se combinan con AND; los valores de un mismo filtro, con OR. `[]` significa "sin filtro" (no uses `'ALL'`).
  - Los filtros y las filas por página se persisten en `localStorage` con claves `cefa-tv.table.<tableId>.<campo>` y `cefa-tv.table.<tableId>.pageSize` (usa `useTablePagination(tableId, 10, pageSizeOptions)` y pasa las mismas `pageSizeOptions` a la tabla). Los valores guardados se validan al cargar; si no hay valor guardado o es incompatible, se aplican los valores por defecto.
- **Navegación y sidebar:** Al añadir nuevos módulos, agrúpalos siempre bajo su categoría lógica en `lib/nav-config.ts` (ej. 'Administración', 'Directorio', 'Red y VPN', 'Departamento'). No satures el menú raíz.
- **Componentes reutilizables:** Identifica patrones visuales repetitivos (ej. buscadores de barras superiores, modales de ajustes) y abstráelos en componentes genéricos en `components/shared/`. Todo sub-widget (como configuración de MCP) debe ser extraído si es propenso a usarse en distintos lugares.
- **Roles y badges:** Usa siempre las funciones estandarizadas (ej. `roleBadgeStyle` en `lib/roles.ts`) para renderizar etiquetas como Administrador, junto con el componente `<Chip>` en lugar de strings de texto crudo.
- **Breadcrumb del top header:** El breadcrumb de `components/layout/top-nav.tsx` se resuelve solo automáticamente para rutas registradas en `lib/nav-config.ts` (muestra un único nivel: el nombre de la página actual). En cualquier subpágina cuya jerarquía no se pueda derivar de `nav-config.ts` (rutas dinámicas como `/entidad/[id]/subseccion`, o cuando conviene mostrar un dato cargado en runtime, como el nombre real de la entidad) debes fijar el breadcrumb explícitamente con `useSetBreadcrumb` (`contexts/breadcrumb-context.tsx`), incluyendo un `href` en los niveles intermedios para que sean navegables. Si además la subpágina se aleja más de un nivel de su listado padre, añade también el botón de volver con `backHref`/`backLabel` en `PageShell`.

## Buscador global (Cmd+K)

- Cada vez que añadas una feature o sub-función visible (incluso opciones dentro de páginas de configuración, como "MCP"), debes actualizar `lib/search-index.ts`.
- **Sub-funciones:** Añade una entrada (`SearchItem`) apuntando a la ruta de la página que la contiene (ej. `/settings?tab=mcp`).
- Asegúrate de agregar *keywords* en español e inglés y usar iconos consistentes para que el usuario pueda encontrarlo rápido con `Ctrl+K`.

## Checklist de calidad (además de la de `general.md` y `nextjs.md`)

- [ ] Componentes y sub-funciones añadidos al índice del buscador global
- [ ] Uso de `EnhancedTable` para cualquier nuevo listado de datos
- [ ] Breadcrumb del top header actualizado (`useSetBreadcrumb`) en toda subpágina cuya jerarquía no resuelva sola desde `nav-config.ts`
