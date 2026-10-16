# Workflow — Hacer un commit

Pasos que debe seguir la IA para ejecutar un commit en este repositorio. El formato del mensaje
y la política de autorización están en [`../rules/common/commits.md`](../rules/common/commits.md);
este documento es sobre el *proceso*, no sobre el *formato*.

## 1. Confirmar autorización

No crear ningún commit salvo que el usuario lo haya pedido explícitamente en este turno de la
conversación. Ver la regla fundamental en `rules/common/commits.md`.

## 2. Inspeccionar el estado

En paralelo:

- `git status` — ficheros sin trackear y modificados.
- `git diff` y `git diff --staged` — cambios exactos que se van a comprometer.
- `git log --oneline -n 10` — estilo de mensajes recientes en este repositorio.

## 3. Redactar el mensaje

Sigue el formato de [`../rules/common/commits.md`](../rules/common/commits.md) (Conventional
Commits, en español). Si el área afectada tiene reglas de stack propias
(`../rules/nextjs/nextjs.md` / `../rules/springboot/springboot.md`, si existen), usa sus convenciones de alcance.

## 4. Preparar el staging

- Añade ficheros concretos por nombre; nunca `git add -A` ni `git add .`.
- Antes de comitear, revisa qué ha quedado en staging (`git status`); nunca subir ficheros que
  puedan contener secretos (`.env`, credenciales) ni binarios grandes sin confirmarlo con el
  usuario.

## 5. Crear el commit

- Crea siempre un commit nuevo; no uses `--amend` salvo petición explícita del usuario.
- No saltes hooks (`--no-verify`) ni firmas (`--no-gpg-sign`) salvo petición explícita.
- Si un hook de pre-commit falla: arregla la causa raíz, vuelve a añadir los ficheros y crea un
  commit nuevo — nunca reintentes saltando el hook.

## 6. Verificar

`git status` después del commit para confirmar que se aplicó correctamente.
