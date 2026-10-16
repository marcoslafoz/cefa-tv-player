#!/usr/bin/env bash
set -euo pipefail

MODE="${DISPLAY_MODE:-headless}"
RESOLUTION="${CEFA_TV_RESOLUTION:-1920x1080x24}"

wait_for_display() {
  for _ in $(seq 1 50); do
    if xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.2
  done
  echo "No se pudo abrir la pantalla $DISPLAY" >&2
  return 1
}

case "$MODE" in
  host)
    export DISPLAY="${DISPLAY:-:0}"
    if [ ! -d /tmp/.X11-unix ]; then
      echo "DISPLAY_MODE=host requiere montar /tmp/.X11-unix del anfitrion" >&2
      exit 1
    fi
    wait_for_display
    ;;
  xorg)
    export DISPLAY=:0
    rm -f /tmp/.X0-lock /tmp/.X11-unix/X0
    Xorg :0 vt1 -nolisten tcp -noreset -novtswitch -keeptty &
    wait_for_display
    openbox >/dev/null 2>&1 &
    ;;
  headless)
    export DISPLAY=:99
    rm -f /tmp/.X99-lock /tmp/.X11-unix/X99
    Xvfb :99 -screen 0 "$RESOLUTION" -nolisten tcp &
    wait_for_display
    openbox >/dev/null 2>&1 &
    ;;
  *)
    echo "DISPLAY_MODE desconocido: $MODE (host, xorg, headless)" >&2
    exit 1
    ;;
esac

xset s off -dpms s noblank >/dev/null 2>&1 || true
unclutter -idle 0.5 -root >/dev/null 2>&1 &

if [ "$MODE" != "headless" ]; then
  DETECTED="$(xdpyinfo -display "$DISPLAY" | awk '/dimensions:/ {print $2}')"
  if [ -n "$DETECTED" ]; then
    export CEFA_TV_RESOLUTION="$DETECTED"
  fi
fi

exec java $JAVA_OPTS -jar /app/app.jar
