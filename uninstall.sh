#!/usr/bin/env bash
set -euo pipefail

PROJECT="cefa-tv-player"
INSTALL_DIR="/opt/cefa-tv-player"
ASSUME_YES=0
KEEP_DATA=0
REMOVE_DOCKER=0

usage() {
  cat <<USAGE
Desinstalador de CEFA TV Player

Uso: sudo ./uninstall.sh [opciones]

Para y elimina el contenedor, la imagen (y la copia de reversion de update.sh), el volumen de datos
(emparejamiento y contenido descargado), el servicio systemd y el directorio de instalacion.
No toca Docker salvo que se pida con --remove-docker.

  --keep-data        Conserva el volumen de datos: al reinstalar, el reproductor sigue emparejado
  --remove-docker    Ademas desinstala Docker del equipo (Debian y Ubuntu; afecta a TODOS los contenedores)
  --dir RUTA         Directorio de instalacion (por defecto $INSTALL_DIR)
  --project NOMBRE   Nombre del contenedor, volumen y servicio (por defecto $PROJECT)
  --yes              No pide confirmacion
  --help             Muestra esta ayuda
USAGE
}

log() { printf '%s\n' "$*"; }
fail() { printf 'Error: %s\n' "$*" >&2; exit 1; }

parse_args() {
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --keep-data) KEEP_DATA=1; shift ;;
      --remove-docker) REMOVE_DOCKER=1; shift ;;
      --dir) INSTALL_DIR="${2:?Falta el valor de --dir}"; shift 2 ;;
      --project) PROJECT="${2:?Falta el valor de --project}"; shift 2 ;;
      --yes|-y) ASSUME_YES=1; shift ;;
      --help|-h) usage; exit 0 ;;
      *) usage; fail "Opcion desconocida: $1" ;;
    esac
  done
  [[ "$PROJECT" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || fail "Nombre de proyecto no valido: $PROJECT"
}

require_root() {
  if [ "$(id -u)" -ne 0 ] && [ "${CEFA_TV_ALLOW_NON_ROOT:-0}" != "1" ]; then
    fail "Ejecuta el desinstalador como root (sudo ./uninstall.sh)"
  fi
}

has_systemd() {
  command -v systemctl >/dev/null 2>&1 && [ -d /run/systemd/system ]
}

ask_exact() {
  local expected="$1" prompt="$2" answer
  if [ "$ASSUME_YES" -eq 1 ]; then
    return 0
  fi
  read -r -p "$prompt " answer </dev/tty || return 1
  [ "$answer" = "$expected" ]
}

describe_plan() {
  log "Se va a desinstalar CEFA TV Player ($PROJECT):"
  log "  - contenedor $PROJECT"
  log "  - imagenes $PROJECT:local y $PROJECT:previous"
  if [ "$KEEP_DATA" -eq 1 ]; then
    log "  - el volumen $PROJECT-data SE CONSERVA (emparejamiento y contenido)"
  else
    log "  - volumen $PROJECT-data (se pierde el emparejamiento y el contenido descargado)"
  fi
  has_systemd && log "  - servicio systemd $PROJECT.service" || true
  log "  - directorio $INSTALL_DIR"
  [ "$REMOVE_DOCKER" -eq 0 ] || log "  - Docker completo (todos los contenedores, imagenes y volumenes del equipo)"
}

remove_containers() {
  if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
    log "Docker no responde: se omite la limpieza de contenedores, imagenes y volumenes"
    return 0
  fi
  if [ -f "$INSTALL_DIR/compose.yml" ]; then
    docker compose -f "$INSTALL_DIR/compose.yml" --env-file "$INSTALL_DIR/.env" down --remove-orphans >/dev/null 2>&1 || true
  fi
  docker rm -f "$PROJECT" >/dev/null 2>&1 || true
  if [ "$KEEP_DATA" -eq 0 ]; then
    docker volume rm "$PROJECT-data" >/dev/null 2>&1 || true
  fi
  docker image rm "$PROJECT:local" "$PROJECT:previous" >/dev/null 2>&1 || true
  local registry
  registry="$(sed -n 's/^CEFA_TV_REGISTRY_IMAGE=//p' "$INSTALL_DIR/.env" 2>/dev/null | head -1 || true)"
  if [ -n "$registry" ]; then
    docker image rm "$registry" >/dev/null 2>&1 || true
  fi
  if [ "$KEEP_DATA" -eq 1 ]; then
    log "Contenedor e imagenes eliminados; el volumen $PROJECT-data se conserva"
  else
    log "Contenedor, imagenes y volumen eliminados"
  fi
}

remove_service() {
  has_systemd || return 0
  local unit="/etc/systemd/system/$PROJECT.service"
  systemctl disable --now "$PROJECT.service" >/dev/null 2>&1 || true
  if [ -f "$unit" ]; then
    rm -f "$unit"
    systemctl daemon-reload
    log "Servicio $PROJECT.service eliminado"
  fi
}

remove_directory() {
  case "$INSTALL_DIR" in
    ""|"/"|"/opt"|"/usr"|"/etc"|"/var"|"$HOME") fail "Directorio de instalacion no seguro: $INSTALL_DIR" ;;
  esac
  rm -rf "$INSTALL_DIR"
  log "Directorio $INSTALL_DIR eliminado"
}

remove_docker() {
  . /etc/os-release
  case "${ID:-}" in
    debian|ubuntu|raspbian) ;;
    *) fail "La desinstalacion automatica de Docker solo cubre Debian y Ubuntu" ;;
  esac
  log "ADVERTENCIA: se va a desinstalar Docker del equipo. Se detendran todos los contenedores, no solo el reproductor."
  ask_exact "eliminar-docker" "Escribe 'eliminar-docker' para continuar:" || fail "Desinstalacion de Docker cancelada"
  systemctl disable --now docker docker.socket containerd >/dev/null 2>&1 || true
  apt-get purge -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin docker-ce-rootless-extras >/dev/null 2>&1 || true
  apt-get autoremove -y >/dev/null 2>&1 || true
  log "Docker desinstalado. Los datos de /var/lib/docker no se han borrado."
}

main() {
  parse_args "$@"
  require_root
  describe_plan
  ask_exact "desinstalar" "Escribe 'desinstalar' para confirmar:" || { log "Cancelado"; exit 0; }
  remove_service
  remove_containers
  remove_directory
  [ "$REMOVE_DOCKER" -eq 0 ] || remove_docker
  log "CEFA TV Player desinstalado"
}

main "$@"; exit $?
