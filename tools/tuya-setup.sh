#!/bin/bash
# "Casa": reads the house from the Tuya cloud once (the hub's local key, rooms and the names of each
# key) and puts it on the TV. Run again only after adding, removing or re-pairing a device.
#
#   tools/tuya-setup.sh            (TV=ip:port if it is not 192.168.1.3:5555)
#   tools/tuya-setup.sh --fake     the fake hub of ./gradlew :tuya-core:fakeHub instead of the house
#
# It restarts the launcher and presses HOME on the TV: whatever is playing stops. Run it when nobody
# is watching.
#
# Needs tuya.accessId, tuya.accessSecret and tuya.region in local.properties (README, "Casa").
set -euo pipefail
cd "$(dirname "$0")/.."

TV="${TV:-192.168.1.3:5555}"
PKG="dev.jean.atvhome"
FILE="build/tuya.json"

# Gradle needs a JDK 17: the one in JAVA_HOME, the system's, or the one in ~/.local/jdk17
if [ -z "${JAVA_HOME:-}" ] && ! java -version >/dev/null 2>&1; then
  for home in "$(/usr/libexec/java_home -v 17 2>/dev/null || true)" "$HOME"/.local/jdk17/*/Contents/Home; do
    if [ -x "$home/bin/java" ]; then export JAVA_HOME="$home"; break; fi
  done
  [ -n "${JAVA_HOME:-}" ] || { echo "No JDK 17 found: install Temurin 17 (README, step 2) or set JAVA_HOME" >&2; exit 1; }
fi

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

# "Casa no filme" (the yellow key over any app) is an accessibility service: listed here, and kept
# listed by the launcher, which needs WRITE_SECURE_SETTINGS for that (the same grant as "Sem anúncios")
adb -s "$TV" shell pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS
SERVICE="$PKG/$PKG.casa.HouseKeyService"
LIST="$(adb -s "$TV" shell settings get secure enabled_accessibility_services | tr -d '\r')"
[ "$LIST" = "null" ] && LIST=""
# Out and back in: the service starts again and reads the new tuya.json
REST="$(printf '%s' "$LIST" | tr ':' '\n' | { grep -vx "$SERVICE" || true; } | paste -sd: -)"
if [ -n "$REST" ]; then adb -s "$TV" shell settings put secure enabled_accessibility_services "$REST"
else adb -s "$TV" shell settings delete secure enabled_accessibility_services >/dev/null; fi
adb -s "$TV" shell am force-stop "$PKG"
adb -s "$TV" shell settings put secure enabled_accessibility_services "${REST:+$REST:}$SERVICE"
adb -s "$TV" shell settings put secure accessibility_enabled 1
adb -s "$TV" shell input keyevent KEYCODE_HOME
echo "ok: the Casa row is under the apps, and the yellow key opens the house over any app"
