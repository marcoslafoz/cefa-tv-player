# Reglas globales

Reglas aplicables a cualquier stack o parte de este proyecto. Las reglas específicas de cada
stack (`nextjs.md`, `springboot.md`, si existen en este repositorio) están en esta
misma carpeta. Las reglas y el proceso de
commits están separados en [`commits.md`](./commits.md) y
[`workflows/commits.md`](../../workflows/commits.md).

## Workflow

- Prefiere editar archivos existentes sobre crear nuevos.
- No agregues manejo de errores, fallbacks o validación para casos que no pueden ocurrir. Solo
  valida en límites reales (entrada de usuario, APIs externas).
- No introduzcas dependencias nuevas sin justificación clara.
- Mantén cambios enfocados en lo solicitado: sin refactors o limpiezas sin relación.

## Código

- Escribe código limpio y autoexplicativo. Los nombres deben dejar claro el propósito.
- No escribas comentarios de ningún tipo, salvo por una decisión no obvia que lo justifique
  (una restricción oculta, un workaround puntual). Si el código necesita comentario, reescribe
  el código para que sea claro por sí solo.
- Prefiere funciones/métodos y componentes pequeños y enfocados.
- Evita abstracciones innecesarias: no agregues opciones de configuración para casos que no
  existen hoy.
- Sin código muerto, comentado o variables/imports sin usar.

## Emojis

- No uses emojis en ningún sitio: código, comentarios, mensajes de commit, documentación,
  textos de interfaz, logs, mensajes de error ni respuestas de la IA.
- Para señalar énfasis o advertencias usa texto (títulos, negrita, "Importante:"), no símbolos
  gráficos.
- Los iconos de interfaz se resuelven con la librería de iconos del proyecto (p. ej. lucide), nunca
  con emojis.

## Secretos

- Nunca escribas contraseñas, tokens, claves privadas ni cadenas de conexión con credenciales en
  el código, en la documentación, en `docs/llm/` ni en mensajes de commit.
- Los datos de acceso no secretos (IP, usuario, rutas, URLs) van en
  `docs/llm/context/reference-data.md`; de cada secreto se documenta solo dónde vive (variable de
  entorno, gestor de contraseñas, ajustes cifrados), nunca su valor.
- Los ficheros con secretos reales (`.env`) no se versionan. Si hace falta una contraseña para una
  tarea, pregúntala al usuario en el momento y no la guardes en ningún sitio.

## Documentación

- Los nombres de fichero dentro de `docs/` van en inglés y en kebab-case (p. ej.
  `reference-data.md`, `implementation-plan.md`), sin importar el idioma del contenido.

## Checklist de calidad (antes de comprometer)

- [ ] Código limpio, nombres autoexplicativos
- [ ] Sin comentarios (a menos que sea por una decisión no obvia)
- [ ] Sin código muerto
- [ ] Sin emojis
- [ ] Sin secretos en código ni documentación
- [ ] Cambios enfocados en lo solicitado
- [ ] Commit hecho siguiendo [`commits.md`](./commits.md)
