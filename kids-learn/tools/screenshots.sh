#!/bin/sh
# Installs the APK on a running emulator or device, opens every kind of screen, and saves a screenshot of each.
# Fails if the app crashed. Used by CI; also handy by hand:  kids-learn/tools/screenshots.sh app.apk shots/
set -eu
apk=$1
out=$2
pkg=com.belajarceria.kids
mkdir -p "$out"
adb install -r "$apk"
adb logcat -c

shot() {
  # $1: file name, $2: screen to open ("" = home). Format of --es open: subject,activity (see MainActivity.open).
  if [ -z "$2" ]; then
    adb shell am start -S -W -n "$pkg/.MainActivity" >/dev/null
  else
    adb shell am start -S -W -n "$pkg/.MainActivity" --es open "$2" >/dev/null
  fi
  sleep 4
  adb shell screencap -p /sdcard/shot.png
  adb pull /sdcard/shot.png "$out/$1.png" >/dev/null
  echo "saved $out/$1.png"
}

shot 01-home ""
shot 02-abc "0"
shot 03-abc-learn "0,0"
shot 04-abc-draw "0,1"
shot 05-abc-drag "0,2"
shot 06-abc-match "0,3"
shot 07-123-drag "1,2"
shot 08-123-match "1,3"
shot 09-alifbata-learn "2,0"
shot 10-alifbata-draw "2,1"
shot 11-alifbata-drag "2,2"
shot 12-islamic-learn "3,0"
shot 13-islamic-match "3,3"
shot 14-arabic-drag "4,2"
shot 15-arabic-draw "4,1"

adb logcat -d > "$out/logcat.txt"
if adb logcat -d -b crash | grep -q "$pkg"; then
  adb logcat -d -b crash > "$out/crash.txt"
  echo "The app crashed:"
  cat "$out/crash.txt"
  exit 1
fi
echo "No crash."
