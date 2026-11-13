#!/usr/bin/env bash
# Push config.json (and an optional wallpaper.jpg) to Glass Car and restart it. Keys: car/README.md.
#
# Usage: CAR_HOST=192.168.43.20 car/theme.sh <folder with config.json>
#        CAR_HOST=192.168.43.20 car/theme.sh --reset
set -euo pipefail
ARG="${1:?usage: theme.sh <folder> | --reset}"
case "$ARG" in --reset|/*) ;; *) ARG="$PWD/$ARG" ;; esac
cd "$(dirname "$0")/.."
: "${CAR_HOST:?set CAR_HOST to the head unit IP address}"
export TV_HOST="$CAR_HOST" MSYS_NO_PATHCONV=1
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
PKG=com.gathin.carhome
DIR=/sdcard/Android/data/$PKG/files
car() { "$PY" -I tools/tv.py "$@"; }
if [ "$ARG" = --reset ]; then
  car "rm -f $DIR/config.json $DIR/wallpaper.jpg; am force-stop $PKG; am start -n $PKG/.HomeActivity >/dev/null; echo reset" | tail -n 1
  exit 0
fi
[ -f "$ARG/config.json" ] || { echo "no config.json in $ARG" >&2; exit 1; }
python3 -c 'import json,sys; json.load(open(sys.argv[1]))' "$ARG/config.json" || { echo "config.json is not valid JSON" >&2; exit 1; }
car "mkdir -p $DIR" >/dev/null
car --push "$ARG/config.json" "$DIR/config.json"
if [ -f "$ARG/wallpaper.jpg" ]; then "$PY" -I tools/b64push.py "$ARG/wallpaper.jpg" "$DIR/wallpaper.jpg"; else car "rm -f $DIR/wallpaper.jpg" >/dev/null; fi
car "am force-stop $PKG; am start -n $PKG/.HomeActivity >/dev/null; echo applied" | tail -n 1
