#!/usr/bin/env bash
# Install Glass Car on an Android car head unit and make it the Home screen, with a safeguard:
# it checks Glass Car really runs before it changes Home, and puts the unit's own home screen back if not.
# The unit's own launcher is never disabled: on head units it often also runs car features.
#
# Usage: CAR_HOST=192.168.43.20 car/install.sh
set -euo pipefail
cd "$(dirname "$0")/.."
: "${CAR_HOST:?set CAR_HOST to the head unit IP address, e.g. CAR_HOST=192.168.43.20 car/install.sh}"
export TV_HOST="$CAR_HOST" MSYS_NO_PATHCONV=1
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
PKG=com.gathin.carhome
# A fresh build if there is one, else the prebuilt app in release/ (no Android tools needed), else build.
APK=${APK:-launcher/car/build/outputs/apk/release/car-release.apk}
[ -f "$APK" ] || [ ! -f release/glass-car.apk ] || APK=release/glass-car.apk
mkdir -p backup
log() { echo "$(date '+%F %T') $*" >> backup/log.txt; }
car() { "$PY" -I tools/tv.py "$@"; }

if [ ! -f "$APK" ]; then
  echo "Building Glass Car..."
  (cd launcher && ./gradlew -q :car:testReleaseUnitTest :car:assembleRelease)
fi

SDK=$(car 'getprop ro.build.version.sdk' | tail -n 1 | tr -d '\r ')
REL=$(car 'getprop ro.build.version.release' | tail -n 1 | tr -d '\r ')
echo "Head unit runs Android $REL (SDK $SDK)."
if [ "${SDK:-0}" -lt 26 ] 2>/dev/null; then
  echo "ERROR: Glass Car needs Android 8 or newer; this unit is SDK $SDK. Nothing was changed." >&2
  exit 1
fi
ORIG_HOME=$(car 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | tail -n 1 | tr -d '\r')
echo "Current home screen: $ORIG_HOME"
log "car/install.sh: original home $ORIG_HOME"

ARMED=0
rollback() {
  [ "$ARMED" = 1 ] || return 0
  echo "Putting the unit's own home screen back..." >&2
  local cmds="pm uninstall $PKG"
  case "$ORIG_HOME" in
    $PKG/*|android|"") ;;
    */*) cmds="$cmds
cmd package set-home-activity --user 0 $ORIG_HOME" ;;
  esac
  cmds="$cmds
input keyevent KEYCODE_HOME"
  printf '%s\n' "$cmds" | car --batch >/dev/null 2>&1 && log "rolled back: uninstalled $PKG, home $ORIG_HOME" \
    || echo "Could not reach the unit. Later run: CAR_HOST=$CAR_HOST car/restore-home.sh $ORIG_HOME" >&2
}
trap 'st=$?; [ $st -ne 0 ] && rollback; exit $st' EXIT

"$PY" -I tools/b64push.py "$APK" /data/local/tmp/carhome.apk
OUT=$(car 'pm install -r /data/local/tmp/carhome.apk; rm -f /data/local/tmp/carhome.apk')
echo "$OUT"
case "$OUT" in
  *Success*) ;;
  *UPDATE_INCOMPATIBLE*) echo "ERROR: a Glass Car signed by another computer is already installed. Remove it first:" >&2
    echo "  CAR_HOST=$CAR_HOST tools/venv/bin/python -I tools/tv.py 'pm uninstall $PKG'   then run this again." >&2; exit 1 ;;
  *) echo "ERROR: pm install failed; nothing else was changed." >&2; exit 1 ;;
esac
ARMED=1
log "installed $PKG"
printf '%s\n' \
  "appops set $PKG MANAGE_EXTERNAL_STORAGE allow; echo cache-permission" \
  "pm grant $PKG android.permission.ACCESS_FINE_LOCATION; echo gps-permission" \
  "cmd notification allow_listener $PKG/$PKG.MediaListener; echo music-widget-permission" \
  "cmd package compile -m speed -f $PKG" \
  "am start -W -n $PKG/.HomeActivity | grep -E '^(Status|Error)'" \
  | car --batch
log "appops set $PKG MANAGE_EXTERNAL_STORAGE allow"

s=$(car "sleep 4; a=\$(pidof $PKG); sleep 3; b=\$(pidof $PKG); f=\$(dumpsys window 2>/dev/null | grep -m1 mCurrentFocus); echo \"\$a|\$b|\$f\"" | tail -n 1 | tr -d '\r')
p1=${s%%|*}; r=${s#*|}; p2=${r%%|*}; focus=${r#*|}
if [ -z "$p1" ] || [ "$p1" != "$p2" ]; then
  echo "ERROR: Glass Car did not keep running on this unit." >&2; exit 1
fi
case "$focus" in *$PKG*) ;; *) echo "ERROR: Glass Car did not come to the front ($focus)." >&2; exit 1 ;; esac

printf '%s\n' \
  "cmd package set-home-activity --user 0 $PKG/.HomeActivity" \
  "input keyevent KEYCODE_HOME" \
  | car --batch >/dev/null
log "cmd package set-home-activity --user 0 $PKG/.HomeActivity"
HOME_NOW=$(car 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | tail -n 1 | tr -d '\r')
echo "Home now resolves to $HOME_NOW"
case "$HOME_NOW" in
  $PKG/*) ;;
  *) echo "NOTE: the unit still prefers $HOME_NOW. Press Home once and pick Glass Car, \"Always\"." ;;
esac
ARMED=0
echo "Installed. Press Home on the screen to see it. Undo: CAR_HOST=$CAR_HOST car/restore-home.sh $ORIG_HOME"
