#!/bin/bash
# "Casa": reads the house from the Tuya cloud once (the hub's local key, rooms and the names of each
# key) and puts it on the TV. Run again only after adding, removing or re-pairing a device.
#
#   tools/tuya-setup.sh            (TV=ip:port if it is not 192.168.1.3:5555)
#   tools/tuya-setup.sh --fake     the fake hub of ./gradlew :tuya-core:fakeHub instead of the house
#
# Needs tuya.accessId, tuya.accessSecret and tuya.region in local.properties (README, "Casa").
set -euo pipefail
cd "$(dirname "$0")/.."

TV="${TV:-192.168.1.3:5555}"
PKG="dev.jean.atvhome"
FILE="build/tuya.json"

if [ "${1:-}" = "--fake" ]; then
  FILE="build/tuya-fake.json"
  [ -f "$FILE" ] || { echo "start ./gradlew :tuya-core:fakeHub first" >&2; exit 1; }
else
  ./gradlew -q :tuya-core:setup --args="--out $FILE"
fi

if ! adb -s "$TV" get-state >/dev/null 2>&1; then
  adb connect "$TV" >/dev/null 2>&1 || true
  adb -s "$TV" get-state >/dev/null 2>&1 || { echo "TV $TV is not reachable over adb (is it on, with network debugging enabled?)" >&2; exit 1; }
fi

# The app moves it to its private storage on the next start and deletes this copy
adb -s "$TV" shell mkdir -p "/sdcard/Android/data/$PKG/files"
adb -s "$TV" push "$FILE" "/sdcard/Android/data/$PKG/files/tuya.json" >/dev/null
adb -s "$TV" shell am force-stop "$PKG"
adb -s "$TV" shell input keyevent KEYCODE_HOME
echo "ok: the Casa row is under the apps on the TV"
