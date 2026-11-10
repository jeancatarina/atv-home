#!/bin/bash
# Measures what "Sem anúncios" costs, read-only. Budgets: docs/anuncios/SINAIS.md ("Desempenho").
#
#   tools/ad-guard-perf.sh home  <label>          Home frames over a scripted D-pad run
#   tools/ad-guard-perf.sh video <label> [secs]   frames of whatever plays now (default 60 s)
#
# Run each mode with the switch off (e.g. home-off) and on (home-on) and compare. Both also record
# memory and CPU of the launcher and of the guard process. Report: docs/anuncios/perf/<date>-<label>.md.
set -euo pipefail

TV="${TV:-192.168.1.3:5555}"
PKG="dev.jean.atvhome"
MODE="${1:?home|video}"
LABEL="${2:?label, e.g. home-off}"
SECS="${3:-60}"
OUT="docs/anuncios/perf/$(date +%F)-$LABEL.md"
sh() { adb -s "$TV" shell "$@"; }

if ! adb -s "$TV" get-state >/dev/null 2>&1; then
  adb connect "$TV" >/dev/null 2>&1 || true
  adb -s "$TV" get-state >/dev/null 2>&1 || { echo "TV $TV is not reachable over adb (is it on, with network debugging enabled?)" >&2; exit 1; }
fi

procs() {
  for p in "$PKG" "$PKG:guard"; do
    echo "### $p"
    sh dumpsys meminfo "$p" | grep -E "TOTAL" | head -1 || echo "not running"
  done
  echo "### top"
  sh top -b -n 3 -d 2 -o PID,%CPU,RES,NAME | grep -E "$PKG|surfaceflinger" || true
}

mkdir -p "$(dirname "$OUT")"
{
  echo "# $LABEL ($MODE, $(date '+%F %R'))"
  echo '```'
  case "$MODE" in
    home)
      sh input keyevent 3
      sleep 2
      sh dumpsys gfxinfo "$PKG" reset >/dev/null
      for _ in 1 2 3; do # down x3, right x4, left x2, up x3: rows and cards scroll
        for k in 20 20 20 22 22 22 22 21 21 19 19 19; do sh input keyevent "$k"; sleep 0.4; done
      done
      sh dumpsys gfxinfo "$PKG" | grep -E "Total frames|Janky|percentile|Missed Vsync|Slow UI"
      ;;
    video)
      sh dumpsys SurfaceFlinger --timestats -disable -clear -enable >/dev/null
      sleep "$SECS"
      sh dumpsys SurfaceFlinger --timestats -dump |
        grep -E "layerName|totalFrames|missedFrames|clientCompositionFrames|droppedFrames|averageFPS" || true
      sh dumpsys SurfaceFlinger --timestats -disable >/dev/null
      ;;
    *) echo "unknown mode: $MODE (home|video)" >&2; exit 1 ;;
  esac
  procs
  echo '```'
} | tee "$OUT"
