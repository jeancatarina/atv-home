#!/bin/bash
# Grants what "Sem anúncios" needs. Run once after each fresh install.
#
#   tools/ad-guard-setup.sh            (TV=ip:port if it is not 192.168.1.3:5555)
#
# Three grants: WRITE_SECURE_SETTINGS (Private DNS, and the guard switching itself on and off), the
# notification listener (Android only lets an app read other apps' media sessions through one; since
# Android 9 only `cmd notification allow_listener` sets it) and PROJECT_MEDIA (screen capture without
# the "start casting?" dialog).
set -euo pipefail

TV="${TV:-192.168.1.3:5555}"
PKG="dev.jean.atvhome"
sh() { adb -s "$TV" shell "$@"; }

if ! adb -s "$TV" get-state >/dev/null 2>&1; then
  adb connect "$TV" >/dev/null 2>&1 || true
  adb -s "$TV" get-state >/dev/null 2>&1 || { echo "TV $TV is not reachable over adb (is it on, with network debugging enabled?)" >&2; exit 1; }
fi

sh pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS
sh cmd notification allow_listener "$PKG/$PKG.guard.SessionListener"
sh appops set "$PKG" PROJECT_MEDIA allow
echo "ok: turn on \"Sem anúncios\" in the Central (the Central button at the top of Home, or MENU)"
