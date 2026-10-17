#!/usr/bin/env bash
set -euo pipefail

PROJECT="cefa-tv-player"
INSTALL_DIR="/opt/cefa-tv-player"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE=""
FORCE=0
HEALTH_TIMEOUT=300

usage() {
  cat <<USAGE
Actualizador de CEFA TV Player

Uso: sudo ./update.sh [opciones]

Descarga la ultima version (git pull --ff-only en este repositorio, o docker pull con --image),
reconstruye la imagen, recrea el contenedor sin perder el emparejamiento ni el contenido descargado y
espera a que quede sano. Si la version nueva no queda sana vuelve automaticamente a la anterior.
Es idempotente: si ya esta en la ultima version no hace nada.

  --image IMAGEN     Actualiza haciendo docker pull de esta imagen en lugar de construirla desde el repositorio
  --force            Recrea el contenedor aunque la imagen no haya cambiado
  --timeout SEG      Segundos de espera a que el contenedor quede sano (por defecto $HEALTH_TIMEOUT)
  --dir RUTA         Directorio de instalacion (por defecto $INSTALL_DIR)
  --project NOMBRE   Nombre del contenedor, volumen y servicio (por defecto $PROJECT)
  --yes              No pide confirmaciones (se acepta por compatibilidad con install.sh)
  --help             Muestra esta ayuda
USAGE
}

log() { printf '%s\n' "$*"; }
fail() { printf 'Error: %s\n' "$*" >&2; exit 1; }

parse_args() {
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --image) IMAGE="${2:?Falta el valor de --image}"; shift 2 ;;
      --force) FORCE=1; shift ;;
      --timeout) HEALTH_TIMEOUT="${2:?Falta el valor de --timeout}"; shift 2 ;;
      --dir) INSTALL_DIR="${2:?Falta el valor de --dir}"; shift 2 ;;
      --project) PROJECT="${2:?Falta el valor de --project}"; shift 2 ;;
      --yes|-y) shift ;;
      --help|-h) usage; exit 0 ;;
      *) usage; fail "Opcion desconocida: $1" ;;
    esac
  done
  [[ "$PROJECT" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || fail "Nombre de proyecto no valido: $PROJECT"
  [[ "$HEALTH_TIMEOUT" =~ ^[0-9]+$ ]] || fail "--timeout debe ser un numero de segundos"
  LOCAL_IMAGE="$PROJECT:local"
  PREVIOUS_IMAGE="$PROJECT:previous"
}

require_root() {
  if [ "$(id -u)" -ne 0 ] && [ "${CEFA_TV_ALLOW_NON_ROOT:-0}" != "1" ]; then
    fail "Ejecuta el actualizador como root (sudo ./update.sh)"
  fi
}

compose() {
  docker compose -f "$INSTALL_DIR/compose.yml" --env-file "$INSTALL_DIR/.env" "$@"
}

env_value() {
  sed -n "s/^$1=//p" "$INSTALL_DIR/.env" | head -1
}

set_env_value() {
  local key="$1" value="$2" file="$INSTALL_DIR/.env" tmp
  tmp="$(mktemp)"
  if grep -q "^$key=" "$file"; then
    sed "s|^$key=.*|$key=$value|" "$file" >"$tmp"
  else
    cat "$file" >"$tmp"
    printf '%s=%s\n' "$key" "$value" >>"$tmp"
  fi
  cat "$tmp" >"$file"
  rm -f "$tmp"
}

running_version() {
  local status version build
  status="$(docker exec "$PROJECT" curl -fsS -m 4 http://127.0.0.1:8081/api/status 2>/dev/null || true)"
  version="$(printf '%s' "$status" | sed -n 's/.*"version":"\([^"]*\)".*/\1/p')"
  build="$(printf '%s' "$status" | sed -n 's/.*"build":"\([^"]*\)".*/\1/p')"
  [ -n "$version" ] || return 0
  printf '%s (build %s)' "$version" "${build:-unknown}"
}

health_status() {
  docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$PROJECT" 2>/dev/null || echo missing
}

wait_healthy() {
  local waited=0 health
  while [ "$waited" -lt "$HEALTH_TIMEOUT" ]; do
    health="$(health_status)"
    case "$health" in
      healthy) return 0 ;;
      unhealthy|missing) return 1 ;;
    esac
    sleep 3
    waited=$((waited + 3))
  done
  return 1
}

image_id() {
  docker image inspect --format '{{.Id}}' "$1" 2>/dev/null || true
}

image_fingerprint() {
  docker image inspect --format '{{.RootFS.Layers}}' "$1" 2>/dev/null | cksum | cut -d" " -f1
}

git_build() {
  git -C "$SCRIPT_DIR" rev-parse --short HEAD 2>/dev/null || echo unknown
}

fetch_new_version() {
  if [ -n "$IMAGE" ]; then
    log "Descargando la imagen $IMAGE"
    docker pull "$IMAGE"
    return 0
  fi
  git -C "$SCRIPT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
    || fail "Este directorio no es un repositorio git; clona el repositorio o usa --image"
  log "Actualizando el repositorio (git pull --ff-only)"
  git -C "$SCRIPT_DIR" pull --ff-only
  log "Construyendo la imagen local (puede tardar unos minutos)"
  docker build -q --build-arg PLAYER_BUILD="$(git_build)" -t "$LOCAL_IMAGE" "$SCRIPT_DIR" >/dev/null
}

rollback() {
  local image_name="$1"
  log "La version nueva no ha quedado sana: se vuelve a la anterior"
  if [ -z "$(image_id "$PREVIOUS_IMAGE")" ]; then
    fail "No hay imagen anterior a la que volver; revisa docker logs $PROJECT"
  fi
  docker tag "$PREVIOUS_IMAGE" "$image_name"
  compose up -d --force-recreate
  if wait_healthy; then
    log "Reversion correcta: el reproductor vuelve a funcionar con la version anterior"
  else
    log "Aviso: tras la reversion el contenedor tampoco queda sano; revisa docker logs $PROJECT"
  fi
  exit 1
}

main() {
  parse_args "$@"
  require_root
  command -v docker >/dev/null 2>&1 || fail "Docker no esta instalado"
  docker info >/dev/null 2>&1 || fail "El demonio de Docker no responde"
  [ -f "$INSTALL_DIR/compose.yml" ] && [ -f "$INSTALL_DIR/.env" ] \
    || fail "No hay ninguna instalacion en $INSTALL_DIR; ejecuta primero ./install.sh"

  local image_name previous_version previous_id previous_fingerprint new_id new_version
  if [ -n "$IMAGE" ]; then
    image_name="$IMAGE"
    set_env_value CEFA_TV_IMAGE "$IMAGE"
    set_env_value CEFA_TV_REGISTRY_IMAGE "$IMAGE"
  else
    image_name="$(env_value CEFA_TV_IMAGE)"
    [ -n "$image_name" ] || image_name="$LOCAL_IMAGE"
  fi

  previous_version="$(running_version)"
  previous_version="${previous_version:-desconocida}"
  previous_id="$(image_id "$image_name")"
  previous_fingerprint="$(image_fingerprint "$image_name")"
  if [ -n "$previous_id" ]; then
    docker tag "$previous_id" "$PREVIOUS_IMAGE"
  fi

  fetch_new_version
  new_id="$(image_id "$image_name")"
  [ -n "$new_id" ] || fail "No se ha podido obtener la imagen $image_name"

  if [ "$FORCE" -eq 0 ] && [ "$(image_fingerprint "$image_name")" = "$previous_fingerprint" ] && [ "$(health_status)" = "healthy" ]; then
    log "Ya esta en la ultima version ($previous_version): no hay nada que actualizar"
    exit 0
  fi

  log "Recreando el contenedor conservando el volumen $PROJECT-data"
  compose up -d --force-recreate
  wait_healthy || rollback "$image_name"

  new_version="$(running_version)"
  log "Actualizacion completada"
  log "  Version anterior: $previous_version"
  log "  Version nueva:    ${new_version:-desconocida}"
}

main "$@"; exit $?
