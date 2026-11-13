#!/usr/bin/env bash
# Put the head unit's own home screen back and remove Glass Car. The debloat list is undone separately,
# with tools/restore.sh car/debloat.txt.
#
# Usage: CAR_HOST=192.168.43.20 car/restore-home.sh <original home, e.g. com.android.launcher3/.Launcher>
set -euo pipefail
cd "$(dirname "$0")/.."
: "${CAR_HOST:?set CAR_HOST to the head unit IP address}"
ORIG="${1:?usage: restore-home.sh <original home component, printed by car/install.sh>}"
export TV_HOST="$CAR_HOST" MSYS_NO_PATHCONV=1
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
printf '%s\n' \
  "pm enable ${ORIG%%/*}" \
  "cmd package set-home-activity --user 0 $ORIG" \
  "pm uninstall com.gathin.carhome" \
  "input keyevent KEYCODE_HOME" \
  | "$PY" -I tools/tv.py --batch
echo "The unit's own home screen is back."
