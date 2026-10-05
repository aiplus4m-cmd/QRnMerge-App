#!/usr/bin/env bash
# Runs on the CI emulator: instrumented tests, then installs and launches the signed release APK.
set -euo pipefail

adb logcat -c || true
adb logcat > logcat.txt 2>&1 &
LOGCAT_PID=$!
trap 'kill $LOGCAT_PID 2>/dev/null || true' EXIT

./gradlew --no-daemon connectedDebugAndroidTest

echo "== Release APK smoke test =="
adb uninstall net.topvl.qrnmerge >/dev/null 2>&1 || true
adb install -r release-apk/app-release.apk
adb shell am start -W -n net.topvl.qrnmerge/.MainActivity
sleep 8
PID=$(adb shell pidof net.topvl.qrnmerge | tr -d '\r' || true)
if [ -z "$PID" ]; then
  echo "Release app is not running after launch (crash?)"
  adb logcat -d -b crash || true
  exit 1
fi
# Switch through the tabs via the UI hierarchy to make sure nothing crashes in the minified build.
adb shell uiautomator dump /sdcard/ui.xml >/dev/null
adb shell cat /sdcard/ui.xml | grep -q 'Document scanner' || { echo "Scan tab not rendered"; exit 1; }
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null
  local bounds
  bounds=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep "text=\"$1\"" | head -1 | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')
  [ -n "$bounds" ] || { echo "Text '$1' not found"; exit 1; }
  read -r x1 y1 x2 y2 <<< "$bounds"
  adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
  sleep 2
}
tap_text "Merge"
adb shell uiautomator dump /sdcard/ui.xml >/dev/null
adb shell cat /sdcard/ui.xml | grep -q 'Merge to PDF' || { echo "Merge tab not rendered"; exit 1; }
tap_text "About"
adb shell uiautomator dump /sdcard/ui.xml >/dev/null
adb shell cat /sdcard/ui.xml | grep -q 'topvl.net' || { echo "About tab not rendered"; exit 1; }
PID2=$(adb shell pidof net.topvl.qrnmerge | tr -d '\r' || true)
[ "$PID" = "$PID2" ] || { echo "App restarted/crashed while switching tabs"; adb logcat -d -b crash || true; exit 1; }
echo "Release APK smoke test passed"
