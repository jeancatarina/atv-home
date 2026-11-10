#!/bin/bash
# Measures the launcher on the TV over adb, read-only. The plan's budgets are in docs/social/PLANO.md §6.2.
#
#   tools/social-perf.sh reset                 zero the frame statistics (run before a scenario)
#   tools/social-perf.sh snapshot <label>      memory, threads, CPU and frames since the last reset
#
# Typical use: `reset`, play the scenario (keys by `adb shell input keyevent`, phones or bots), then
# `snapshot social-room-8-bots`. The report goes to docs/social/perf/<date>-<label>.md.
# Set TV=ip:port if it is not 192.168.1.3:5555.
set -euo pipefail

TV="${TV:-192.168.1.3:5555}"
PKG="dev.jean.atvhome"
sh() { adb -s "$TV" shell "$@"; }

if ! adb -s "$TV" get-state >/dev/null 2>&1; then
  adb connect "$TV" >/dev/null 2>&1 || true
  adb -s "$TV" get-state >/dev/null 2>&1 || { echo "TV $TV is not reachable over adb (is it on, with network debugging enabled?)" >&2; exit 1; }
fi

case "${1:-}" in
  reset)
    sh dumpsys gfxinfo "$PKG" reset >/dev/null
    echo "frame statistics reset"
    ;;
  snapshot)
    label="${2:?usage: social-perf.sh snapshot <label>}"
    out="$(dirname "$0")/../docs/social/perf/$(date +%F)-$label.md"
    pid="$(sh pidof "$PKG" | tr -d '\r')"
    [ -n "$pid" ] || { echo "$PKG is not running" >&2; exit 1; }
    {
      echo "# $label — $(date '+%F %H:%M')"
      echo
      echo '```'
      echo "pid $pid · $(sh getprop ro.product.model | tr -d '\r') · Android $(sh getprop ro.build.version.release | tr -d '\r')"
      sh dumpsys meminfo "$PKG" | sed -n '/App Summary/,/TOTAL SWAP/p'
      sh "grep -E 'Threads|VmRSS' /proc/$pid/status" | tr -d '\r'
      echo
      sh dumpsys gfxinfo "$PKG" | grep -E 'Total frames|Janky|percentile|Missed Vsync|Slow UI|Slow bitmap|Frame deadline' | tr -d '\r'
      echo
      sh "top -b -n 3 -d 1 -o PID,%CPU,RES,NAME | grep -E 'PID|$PKG'" | tr -d '\r'
      echo
      sh dumpsys cpuinfo | grep -E 'Load|TOTAL' | head -2 | tr -d '\r'
      echo '```'
    } | tee "$out"
    echo "wrote $out" >&2
    ;;
  *)
    sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
    ;;
esac
