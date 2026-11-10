#!/bin/bash
# Captures what the app in front shows the system, for docs/anuncios/SINAIS.md.
#
#   tools/ad-signals.sh <label>        e.g. tools/ad-signals.sh netflix-ad
#
# Run it once during an ad and once during the film, per app. Output: docs/anuncios/raw/<label>/
# (git-ignored: screenshots). Do not run it while measuring smoothness: uiautomator and screencap are heavy.
set -euo pipefail

TV="${TV:-192.168.1.3:5555}"
LABEL="${1:?label, e.g. netflix-ad}"
OUT="docs/anuncios/raw/$LABEL"
sh() { adb -s "$TV" shell "$@"; }

if ! adb -s "$TV" get-state >/dev/null 2>&1; then
  adb connect "$TV" >/dev/null 2>&1 || true
  adb -s "$TV" get-state >/dev/null 2>&1 || { echo "TV $TV is not reachable over adb (is it on, with network debugging enabled?)" >&2; exit 1; }
fi

mkdir -p "$OUT"
sh dumpsys window windows | grep -E "mCurrentFocus|mFocusedApp" > "$OUT/focus.txt" || true
sh dumpsys media_session > "$OUT/media_session.txt"
if sh uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; then
  adb -s "$TV" pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null
else
  echo "uiautomator failed" > "$OUT/ui.xml"
fi
adb -s "$TV" exec-out screencap -p > "$OUT/screen.png"
sleep 2
adb -s "$TV" exec-out screencap -p > "$OUT/screen-2s.png" # second frame: does a number count down?
echo "saved in $OUT"
