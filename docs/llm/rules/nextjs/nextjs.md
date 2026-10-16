# Reglas — Next.js / Frontend

Reglas específicas para las partes de este proyecto construidas con Next.js + React. Las reglas
globales están en [`general.md`](../common/general.md). Los patrones de interfaz (tablas, navegación,
buscador global) están en [`ui-patterns.md`](./ui-patterns.md).

## Gestor de paquetes

- Este proyecto usa siempre **pnpm**, nunca `npm` ni `yarn` (instalar dependencias, ejecutar
  scripts, `npx` → `pnpm dlx`, etc.).

## Imports y componentes

- Usa el alias `@/` para imports: `@/components/widgets/types`, nunca rutas relativas (`./`,
  `../`).
- Prefiere componentes `shadcn/ui` sobre markup manual, en la carpeta que use el proyecto
  (`components/shadcn-ui/*` o `components/ui/*`).
  - Si falta un componente shadcn, agrégalo en lugar de hacer un equivalente personalizado.

## Código

- **No escribas comentarios** de ningún tipo (`//`, `/* */`, JSX comments) — ver la regla
  general en `general.md`.
- Saca los efectos secundarios obvios en el nombre y firma de la función.

## Texto visible al usuario

- Nunca hardcodees texto visible. Usa `useDictionary()` / `dict.*` e inclúyelo en el diccionario
  de i18n correspondiente (`i18n/dictionaries/{es,en,...}.json`).

## Alcance de commits

El formato general está en [`commits.md`](../common/commits.md). En este stack, el alcance
es el módulo o área afectada (ej: `sidebar`, `signature-generator`).

- Ejemplo: `fix(sidebar): reactivar sidebar y ruta principal`

## Checklist de calidad (además de la de `general.md`)

- [ ] Imports con `@/`, sin rutas relativas
- [ ] Textos de usuario en el diccionario de i18n
