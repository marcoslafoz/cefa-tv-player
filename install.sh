#!/usr/bin/env bash
set -euo pipefail

INSTALL_DIR="/opt/cefa-tv-player"
CONTAINER="cefa-tv-player"
LOCAL_IMAGE="cefa-tv-player:local"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

KEY=""
SERVER=""
PLAYER_NAME=""
MODE=""
IMAGE=""
ASSUME_YES=0
UNINSTALL=0

usage() {
  cat <<USAGE
Instalador de CEFA TV Player

Uso: sudo ./install.sh [opciones]

  --key CLAVE        Clave de alta generada en CEFA TV Server (si falta, se pregunta)
  --server URL       URL del core (p. ej. http://192.168.1.10:8080); si se omite se descubre en la red
  --name NOMBRE      Nombre solicitado para la pantalla (por defecto, el hostname)
  --mode MODO        host (usa el servidor X del equipo), xorg (pantalla dedicada) o headless (pruebas)
  --image IMAGEN     Usa una imagen de un registro en lugar de construirla desde este repositorio
  --dir RUTA         Directorio de instalacion (por defecto $INSTALL_DIR)
  --yes              No pregunta confirmaciones (instala Docker si falta)
  --uninstall        Desinstala el reproductor
  --help             Muestra esta ayuda
USAGE
}

log() { printf '%s\n' "$*"; }
fail() { printf 'Error: %s\n' "$*" >&2; exit 1; }

confirm() {
  if [ "$ASSUME_YES" -eq 1 ]; then
    return 0
  fi
  local answer
  read -r -p "$1 [s/N] " answer </dev/tty || return 1
  [[ "$answer" =~ ^[sSyY]$ ]]
}

parse_args() {
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --key) KEY="${2:?Falta el valor de --key}"; shift 2 ;;
      --server) SERVER="${2:?Falta el valor de --server}"; shift 2 ;;
      --name) PLAYER_NAME="${2:?Falta el valor de --name}"; shift 2 ;;
      --mode) MODE="${2:?Falta el valor de --mode}"; shift 2 ;;
      --image) IMAGE="${2:?Falta el valor de --image}"; shift 2 ;;
      --dir) INSTALL_DIR="${2:?Falta el valor de --dir}"; shift 2 ;;
      --yes|-y) ASSUME_YES=1; shift ;;
      --uninstall) UNINSTALL=1; shift ;;
      --help|-h) usage; exit 0 ;;
      *) usage; fail "Opcion desconocida: $1" ;;
    esac
  done
}

require_root() {
  if [ "$(id -u)" -ne 0 ]; then
    fail "Ejecuta el instalador como root (sudo ./install.sh)"
  fi
}

install_docker() {
  . /etc/os-release
  case "${ID:-}" in
    debian|ubuntu|raspbian) ;;
    *) fail "Docker no esta instalado y la instalacion automatica solo cubre Debian y Ubuntu" ;;
  esac
  confirm "Docker no esta instalado. Instalarlo ahora con el script oficial de Docker?" \
    || fail "Docker es necesario"
  command -v curl >/dev/null 2>&1 || { apt-get update -qq && apt-get install -y -qq curl; }
  curl -fsSL https://get.docker.com | sh
  systemctl enable --now docker
}

install_compose_plugin() {
  confirm "Falta el plugin docker compose. Instalarlo con apt?" || fail "docker compose es necesario"
  apt-get update -qq
  apt-get install -y -qq docker-compose-plugin
}

ensure_docker() {
  command -v docker >/dev/null 2>&1 || install_docker
  docker info >/dev/null 2>&1 || systemctl start docker || fail "El demonio de Docker no responde"
  docker compose version >/dev/null 2>&1 || install_compose_plugin
}

read_existing_env() {
  local file="$INSTALL_DIR/.env"
  [ -f "$file" ] || return 0
  [ -n "$KEY" ] || KEY="$(sed -n 's/^CEFA_TV_ENROLLMENT_KEY=//p' "$file" | head -1)"
  [ -n "$SERVER" ] || SERVER="$(sed -n 's/^CEFA_TV_SERVER=//p' "$file" | head -1)"
  [ -n "$PLAYER_NAME" ] || PLAYER_NAME="$(sed -n 's/^CEFA_TV_PLAYER_NAME=//p' "$file" | head -1)"
  [ -n "$MODE" ] || MODE="$(sed -n 's/^DISPLAY_MODE=//p' "$file" | head -1)"
}

ask_key() {
  if [ -z "$KEY" ]; then
    read -r -p "Clave de alta (se genera en CEFA TV Server, seccion de claves): " KEY </dev/tty
  fi
  [[ "$KEY" =~ ^[A-Za-z0-9-]{6,64}$ ]] || fail "La clave de alta no tiene un formato valido"
}

detect_mode() {
  if [ -n "$MODE" ]; then
    return 0
  fi
  if compgen -G "/tmp/.X11-unix/X*" >/dev/null; then
    MODE="host"
  else
    MODE="xorg"
  fi
  log "Modo de pantalla detectado: $MODE"
}

validate_mode() {
  case "$MODE" in
    host|xorg|headless) ;;
    *) fail "Modo no valido: $MODE (host, xorg o headless)" ;;
  esac
}

x_display() {
  local socket
  socket="$(ls /tmp/.X11-unix/X* 2>/dev/null | head -1 || true)"
  printf ':%s' "${socket##*/X}"
}

find_xauthority() {
  local candidate
  for candidate in \
    "${XAUTHORITY:-}" \
    "/home/${SUDO_USER:-root}/.Xauthority" \
    "/run/user/$(id -u "${SUDO_USER:-root}")/gdm/Xauthority" \
    "/root/.Xauthority"; do
    if [ -n "$candidate" ] && [ -f "$candidate" ]; then
      printf '%s' "$candidate"
      return 0
    fi
  done
}

allow_x_access() {
  if command -v xhost >/dev/null 2>&1; then
    local user="${SUDO_USER:-root}"
    sudo -u "$user" env DISPLAY="$(x_display)" xhost +local: >/dev/null 2>&1 \
      || log "Aviso: no se pudo ejecutar 'xhost +local:'; si la pantalla no se ve, ejecutalo desde la sesion grafica"
  fi
}

write_env() {
  install -d -m 755 "$INSTALL_DIR"
  umask 077
  cat >"$INSTALL_DIR/.env" <<ENV
CEFA_TV_ENROLLMENT_KEY=$KEY
CEFA_TV_SERVER=$SERVER
CEFA_TV_PLAYER_NAME=$PLAYER_NAME
DISPLAY_MODE=$MODE
CEFA_TV_IMAGE=${IMAGE:-$LOCAL_IMAGE}
ENV
  chmod 600 "$INSTALL_DIR/.env"
}

write_compose() {
  local file="$INSTALL_DIR/compose.yml"
  {
    cat <<YAML
services:
  player:
    image: \${CEFA_TV_IMAGE}
    container_name: $CONTAINER
    restart: unless-stopped
    network_mode: host
    shm_size: 1gb
    env_file:
      - .env
    environment:
      - CEFA_TV_DATA_DIR=/data
      - DISPLAY_MODE=\${DISPLAY_MODE}
YAML
    case "$MODE" in
      host)
        echo "      - DISPLAY=$(x_display)"
        echo "      - XAUTHORITY=/root/.Xauthority"
        echo "    volumes:"
        echo "      - cefa-tv-player-data:/data"
        echo "      - /tmp/.X11-unix:/tmp/.X11-unix"
        local xauth
        xauth="$(find_xauthority || true)"
        if [ -n "$xauth" ]; then
          echo "      - $xauth:/root/.Xauthority:ro"
        fi
        if [ -d /dev/dri ]; then
          echo "    devices:"
          echo "      - /dev/dri:/dev/dri"
        fi
        ;;
      xorg)
        echo "    privileged: true"
        echo "    tty: true"
        echo "    volumes:"
        echo "      - cefa-tv-player-data:/data"
        echo "      - /run/udev:/run/udev:ro"
        ;;
      headless)
        echo "    volumes:"
        echo "      - cefa-tv-player-data:/data"
        ;;
    esac
    cat <<YAML

volumes:
  cefa-tv-player-data:
YAML
  } >"$file"
}

prepare_image() {
  if [ -n "$IMAGE" ]; then
    docker pull "$IMAGE"
    return 0
  fi
  [ -f "$SCRIPT_DIR/Dockerfile" ] || fail "No se encuentra el Dockerfile; ejecuta el instalador desde el repositorio o usa --image"
  log "Construyendo la imagen local (puede tardar unos minutos)"
  docker build -t "$LOCAL_IMAGE" "$SCRIPT_DIR"
}

wait_for_enrollment() {
  log "Esperando el alta en el servidor"
  local status phase
  for _ in $(seq 1 60); do
    status="$(docker exec "$CONTAINER" curl -fsS http://127.0.0.1:8081/status.json 2>/dev/null || true)"
    phase="$(printf '%s' "$status" | sed -n 's/.*"phase":"\([A-Z_]*\)".*/\1/p')"
    case "$phase" in
      RUNNING)
        log "Reproductor dado de alta:"
        printf '%s\n' "$status"
        return 0
        ;;
      INVALID_KEY)
        printf '%s\n' "$status" >&2
        fail "El servidor ha rechazado la clave de alta. Revisa la clave y ejecuta de nuevo el instalador"
        ;;
    esac
    sleep 3
  done
  log "El reproductor todavia no se ha dado de alta. Estado actual:"
  printf '%s\n' "${status:-sin respuesta}"
  log "Revisa los registros con: docker logs $CONTAINER"
  return 1
}

uninstall() {
  require_root
  confirm "Desinstalar CEFA TV Player y borrar su alta y su cache de contenido?" || exit 0
  if [ -f "$INSTALL_DIR/compose.yml" ]; then
    docker compose -f "$INSTALL_DIR/compose.yml" --env-file "$INSTALL_DIR/.env" down -v || true
  fi
  rm -rf "$INSTALL_DIR"
  docker image rm "$LOCAL_IMAGE" >/dev/null 2>&1 || true
  log "CEFA TV Player desinstalado"
}

main() {
  parse_args "$@"
  if [ "$UNINSTALL" -eq 1 ]; then
    uninstall
    exit 0
  fi
  require_root
  ensure_docker
  read_existing_env
  ask_key
  detect_mode
  validate_mode
  [ "$MODE" != "host" ] || allow_x_access
  prepare_image
  write_env
  write_compose
  docker compose -f "$INSTALL_DIR/compose.yml" --env-file "$INSTALL_DIR/.env" up -d --force-recreate
  wait_for_enrollment
  log "Instalacion completada en $INSTALL_DIR"
}

main "$@"
