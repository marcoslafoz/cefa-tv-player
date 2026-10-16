# Reglas — Notion

Normas para trabajar con Notion en cualquier proyecto de CEFA. Notion es la fuente centralizada de
verdad para la **gestión de tareas** y la **documentación para personas** del proyecto; el contexto y
las reglas dirigidas a la IA siguen viviendo en `docs/llm/` del repositorio. Las reglas globales
están en [`general.md`](./general.md).

## Estructura

Cada proyecto tiene su propia carpeta en Notion, con la ruta fija:

```
CEFA Informática / Projects / <Proyecto>
├── Kanban          base de datos de tareas
└── Documentación   arquitectura, operación, despliegue e integraciones
```

- Los nombres `Kanban` y `Documentación` son siempre esos, para que cualquier proyecto se navegue
  igual.
- Los enlaces a la carpeta del proyecto, al Kanban y a la Documentación están en la sección "Notion"
  de [`../../context/reference-data.md`](../../context/reference-data.md), que es su única fuente. No
  los copies a otros ficheros.
- Si esa sección no tiene los enlaces (aparecen como "Pendiente de definir"), pregunta al usuario por
  ellos antes de buscar o crear nada en Notion.
- No crees la carpeta de un proyecto, ni su Kanban ni su Documentación, salvo petición explícita del
  usuario. Tampoco muevas ni borres páginas de otros proyectos.

## Tareas (Kanban)

Los estados de una tarea son `ToDo`, `In Progress` y `Done`.

- **Al empezar una sesión de trabajo**: consulta el Kanban, mira qué hay en `ToDo` y propón cuál
  empezar, o pregunta si se sigue con una que esté en `In Progress`. No empieces una tarea sin
  confirmarlo.
- **Durante el trabajo**: cuando una tarea empiece o termine, actualiza su estado en el Kanban a
  `In Progress` o `Done`.
- **Al completar**: marca la tarea como `Done`, añade un comentario con un resumen de lo realizado
  (commits, cambios clave) y pregunta si hay otra tarea por hacer.
- Los planes de implementación para la IA no van en Notion: se guardan en `docs/llm/tasks/` y la
  tarea del Kanban los referencia.

## Documentación

- **Sincronización activa**: si un cambio de código afecta a lo documentado en Notion (arquitectura,
  componentes, integraciones, operación, configuración), actualiza el documento correspondiente en el
  mismo trabajo, no después.
- **Nuevas funcionalidades**: cuando implementes una funcionalidad nueva, añade su información a la
  Documentación del proyecto si es relevante para administradores o futuros desarrolladores.
- **Cambios de configuración**: cualquier cambio en variables de entorno, URLs o procesos se refleja
  en la Documentación.
- Nunca escribas en Notion contraseñas, tokens ni claves: aplica lo indicado en
  [`general.md`](./general.md), sección "Secretos".

## Qué va en cada sitio

| Dónde | Qué |
|---|---|
| Notion, Kanban | Tareas, su estado y el resumen al completarlas. |
| Notion, Documentación | Documentación para administradores y desarrolladores. |
| `docs/llm/` del repositorio | Reglas, contexto y planes de tareas para la IA. |
| `docs/llm/context/reference-data.md` | Enlaces de Notion, URLs, servidores y dónde viven las credenciales. |

## Checklist de calidad (además de la de `general.md`)

- [ ] La tarea está en el estado correcto del Kanban y, si se completó, tiene su comentario resumen
- [ ] La Documentación de Notion refleja los cambios de arquitectura, integraciones o configuración
- [ ] No hay secretos en las páginas de Notion
