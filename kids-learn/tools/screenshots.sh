#!/bin/sh
# Installs the APK on a running emulator or device, opens every kind of screen, and saves a screenshot of each.
# Fails if the app crashed. Used by CI; also handy by hand:  kids-learn/tools/screenshots.sh app.apk shots/
set -eu
apk=$1
out=$2
pkg=com.belajarceria.kids
mkdir -p "$out"
adb install -r "$apk"
# A busy CI emulator's own launcher tends to hang and cover the shots with "isn't responding": switch it off
# (this is only ever run on a test device), and close any system dialog before each shot.
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher >/dev/null 2>&1 || true
# Skip Android's one-time "Viewing full screen" notice, which would cover the screenshots.
adb shell settings put secure immersive_mode_confirmations confirmed
adb logcat -c

shot() {
  # $1: file name, $2: screen to open ("" = home). Format of --es open: subject,activity (see MainActivity.open).
  # $3 (optional): a theme to switch to first (classic, hero, ice); it is remembered for later shots.
  set -- "$1" "$2" "${3:-}"
  extra=""
  [ -n "$2" ] && extra="--es open $2"
  [ -n "$3" ] && extra="$extra --es theme $3"
  # shellcheck disable=SC2086
  adb shell am start -S -W -n "$pkg/.MainActivity" $extra >/dev/null
  sleep 6
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
  sleep 1
  adb shell screencap -p /sdcard/shot.png
  adb pull /sdcard/shot.png "$out/$1.png" >/dev/null
  echo "saved $out/$1.png"
}

shot 01-home ""
shot 02-abc "0"
shot 03-abc-learn "0,0"
shot 03x-abc-learn-x "0,0,23"
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
shot 16-123-learn "1,0"
shot 17-islamic-drag "3,2"

# The test: its start, and a question from each part (random each time).
shot 40-exam-intro "exam"
shot 41-exam-bm "exam,0"
shot 42-exam-en "exam,1"
shot 43-exam-mt "exam,2"
shot 44-exam-jawi "exam,3"

# The two themed looks, on the home screen and in a few games.
shot 20-hero-home "" hero
shot 21-hero-abc-drag "0,2"
shot 22-hero-123-match "1,3"
shot 23-hero-alifbata-draw "2,1"
shot 30-ice-home "" ice
shot 31-ice-abc-learn "0,0"
shot 32-ice-islamic-match "3,3"
shot 33-ice-arabic-drag "4,2"
shot 34-ice-subject "1"
# Back to the default look for whatever runs next.
adb shell am start -S -W -n "$pkg/.MainActivity" --es theme classic >/dev/null

adb logcat -d > "$out/logcat.txt"
if adb logcat -d -b crash | grep -q "$pkg"; then
  adb logcat -d -b crash > "$out/crash.txt"
  echo "The app crashed:"
  cat "$out/crash.txt"
  exit 1
fi
echo "No crash."
