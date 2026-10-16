# Reglas — Commits

Reglas sobre **cuándo y cómo** comitear en este repositorio, para cualquier stack. El *proceso*
paso a paso que debe seguir la IA para ejecutar un commit está en
[`workflows/commits.md`](../../workflows/commits.md); este documento es sobre el formato y la
política, no sobre los pasos a ejecutar.

## Regla fundamental

**No se hacen commits sin autorización explícita del usuario.** Siempre solicitar confirmación
antes de crear, amend o hacer push de cualquier commit.

## Formato del mensaje

Conventional Commits, descripción **en español**: `tipo(alcance): descripción`

- Tipos: `feat`, `fix`, `refactor`, `chore`, `docs`, `style`, `test`
- Alcance: módulo o área afectada. Si el stack tiene convenciones propias de alcance, están en
  `nextjs.md` y `springboot.md` (si existen en este repositorio).
- Ejemplo: `fix(sidebar): reactivar sidebar y ruta principal`

## Reglas adicionales

- Nunca usar `--no-verify`, `--no-gpg-sign` ni saltar hooks salvo petición explícita del usuario.
- Crear siempre un commit nuevo; usar `--amend` solo si el usuario lo pide explícitamente.
- Nunca hacer `git push --force` ni tocar ramas protegidas sin confirmación explícita.
- Nunca comitear ficheros que probablemente contengan secretos (`.env`, credenciales) sin
  avisar al usuario.
