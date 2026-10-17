#!/usr/bin/env bash
set -uo pipefail

MODE="${DISPLAY_MODE:-headless}"
RESOLUTION="${CEFA_TV_RESOLUTION:-1920x1080x24}"
WATCHDOG_INTERVAL="${CEFA_TV_WATCHDOG_INTERVAL:-30}"
WATCHDOG_FAILURES="${CEFA_TV_WATCHDOG_FAILURES:-6}"
DISPLAY_FILE=/run/cefa-tv-display

log() { printf '[entrypoint] %s\n' "$*" >&2; }

case "$MODE" in
  host) export DISPLAY="${DISPLAY:-:0}" ;;
  xorg) export DISPLAY=:0 ;;
  headless) export DISPLAY=:99 ;;
  *)
    log "DISPLAY_MODE desconocido: $MODE (host, xorg, headless)"
    exit 1
    ;;
esac
printf '%s' "$DISPLAY" >"$DISPLAY_FILE"

wait_for_display() {
  local attempts="${1:-50}"
  for _ in $(seq 1 "$attempts"); do
    if xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.2
  done
  return 1
}

prepare_display() {
  xset s off -dpms s noblank >/dev/null 2>&1 || true
}

stop_all() {
  local pid
  for pid in "$@"; do
    [ -n "$pid" ] && kill "$pid" >/dev/null 2>&1 || true
  done
  sleep 1
  for pid in "$@"; do
    [ -n "$pid" ] && kill -9 "$pid" >/dev/null 2>&1 || true
  done
}

run_local_stack() {
  local server_pid="" wm_pid="" hider_pid=""
  case "$MODE" in
    xorg)
      rm -f /tmp/.X0-lock /tmp/.X11-unix/X0
      Xorg :0 vt1 -nolisten tcp -noreset -novtswitch -keeptty &
      server_pid=$!
      ;;
    headless)
      rm -f /tmp/.X99-lock /tmp/.X11-unix/X99
      Xvfb :99 -screen 0 "$RESOLUTION" -nolisten tcp +extension RANDR &
      server_pid=$!
      ;;
  esac
  if ! wait_for_display 100; then
    log "La pantalla $DISPLAY no ha arrancado"
    stop_all "$server_pid"
    return 1
  fi
  [ "$MODE" != "xorg" ] || xrandr --auto >/dev/null 2>&1 || true
  prepare_display
  openbox >/dev/null 2>&1 &
  wm_pid=$!
  unclutter -idle 0.5 -root >/dev/null 2>&1 &
  hider_pid=$!
  wait -n "$server_pid" "$wm_pid"
  log "Se ha caído el servidor gráfico o el gestor de ventanas"
  stop_all "$hider_pid" "$wm_pid" "$server_pid"
}

supervise_display() {
  while true; do
    if [ "$MODE" = "host" ]; then
      if wait_for_display 25; then
        prepare_display
        unclutter -idle 0.5 -root >/dev/null 2>&1 &
        local hider_pid=$!
        while xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; do sleep 5; done
        stop_all "$hider_pid"
        log "El servidor gráfico del equipo ya no responde, se espera a que vuelva"
      fi
    else
      run_local_stack || true
    fi
    sleep 2
  done
}

watchdog() {
  local failures=0
  sleep 90
  while true; do
    if /usr/local/bin/healthcheck.sh >/dev/null 2>&1; then
      failures=0
    else
      failures=$((failures + 1))
      log "Comprobación de salud fallida ($failures de $WATCHDOG_FAILURES)"
      if [ "$failures" -ge "$WATCHDOG_FAILURES" ]; then
        log "El reproductor no se recupera, se reinicia el contenedor"
        kill -TERM "$$"
        return 0
      fi
    fi
    sleep "$WATCHDOG_INTERVAL"
  done
}

supervise_display &
SUPERVISOR_PID=$!

wait_for_display 300 || log "La pantalla $DISPLAY aún no responde; Chromium reintentará solo"

if [ "$MODE" != "headless" ]; then
  DETECTED="$(xdpyinfo -display "$DISPLAY" 2>/dev/null | awk '/dimensions:/ {print $2}')"
  if [ -n "$DETECTED" ]; then
    export CEFA_TV_RESOLUTION="$DETECTED"
  fi
fi

watchdog &
WATCHDOG_PID=$!

java $JAVA_OPTS -jar /app/app.jar &
JAVA_PID=$!

shutdown() {
  kill "$JAVA_PID" "$WATCHDOG_PID" "$SUPERVISOR_PID" >/dev/null 2>&1 || true
  pkill -P "$SUPERVISOR_PID" >/dev/null 2>&1 || true
  pkill -x Xorg >/dev/null 2>&1 || true
  pkill -x Xvfb >/dev/null 2>&1 || true
  pkill -x openbox >/dev/null 2>&1 || true
}
trap 'shutdown; wait "$JAVA_PID" 2>/dev/null; exit 143' TERM INT

wait "$JAVA_PID"
CODE=$?
log "El reproductor ha terminado con código $CODE"
shutdown
exit "$CODE"
