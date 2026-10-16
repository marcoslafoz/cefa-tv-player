# Reglas para Fotos y Assets

Estas reglas aplican al uso de imágenes y recursos estáticos en cualquier parte del proyecto.

## Formato y Optimización

- **Formato obligatorio**: Todas las imágenes, fotos e iconos complejos (que no sean SVG) deben ser obligatoriamente añadidos en formato **WebP**. No se permite subir o utilizar imágenes en formato PNG, JPEG o similares.
- **Optimización**: Antes de incluir cualquier imagen en el proyecto, debe ser comprimida u optimizada adecuadamente para asegurar su bajo peso. No se deben subir imágenes crudas o sin procesar.

## Aplicación

- En el frontend, las imágenes WebP optimizadas deben ubicarse en la carpeta `public/` (o su subdirectorio correspondiente, como `public/images/`).
- Cuando se editen o añadan imágenes desde tareas, la IA debe hacer uso de las herramientas del sistema para convertirlas y optimizarlas a WebP (por ejemplo, usando `cwebp -q 80 <origen> -o <destino>`). Esta compresión (calidad ~80) asegura un buen balance entre peso y calidad visual.
- **Limpieza**: Una vez que la imagen original (PNG, JPEG, etc.) ha sido convertida y optimizada a WebP, el fichero original debe ser borrado del repositorio para no ocupar espacio innecesario.
