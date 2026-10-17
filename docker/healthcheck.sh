#!/usr/bin/env bash
set -uo pipefail

curl -fsS -m 4 http://127.0.0.1:8081/actuator/health >/dev/null || exit 1

DISPLAY="$(cat /run/cefa-tv-display 2>/dev/null || true)"
export DISPLAY
if [ -n "$DISPLAY" ]; then
  xdpyinfo -display "$DISPLAY" >/dev/null 2>&1 || exit 1
fi

if [ "${CEFA_TV_CHROMIUM_ENABLED:-true}" = "true" ]; then
  pgrep -f 'remote-debugging-port' >/dev/null || exit 1
fi
exit 0
