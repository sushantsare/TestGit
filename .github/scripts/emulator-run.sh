#!/bin/bash
# Runs inside one emulator job. Working directory: the app folder (apps/<app>).
# Usage: emulator-run.sh <ui|audit|e2e> <api>
#   ui    - installs the prebuilt debug + test APKs and runs the instrumented tests (walkthrough)
#   audit - runs the app's e2e.sh main run (audit evidence T3-T15) on its own emulator
#   e2e   - runs e2e.sh <api> (smoke / real-world checks on that Android version)
ROLE=$1
API=$2
OUT=/tmp/out
mkdir -p $OUT/screens $OUT/audit
adb shell settings put global hide_error_dialogs 1 || true
adb shell settings put secure anr_show_background 0 || true

run_ui() {
  local APKS=app/build/outputs/apk LOG=$OUT/ui-tests.log
  local APP TEST BT PKG INSTR
  APP=$(ls $APKS/debug/*x86_64*.apk 2>/dev/null | head -1)
  [ -z "$APP" ] && APP=$(ls $APKS/debug/*universal*.apk $APKS/debug/app-debug.apk 2>/dev/null | head -1)
  [ -z "$APP" ] && APP=$(ls $APKS/debug/*.apk 2>/dev/null | head -1)
  TEST=$(ls $APKS/androidTest/debug/*.apk 2>/dev/null | head -1)
  if [ -z "$APP" ] || [ -z "$TEST" ]; then
    echo "Debug or test APK missing: the debug build failed (see debug-build.log)" > $LOG
    echo FAILED > $OUT/UI_TESTS
    return
  fi
  BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
  PKG=$("$BT/aapt" dump badging "$APP" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")
  {
    echo "App APK:  $APP ($PKG)"
    echo "Test APK: $TEST"
    adb install -r -t "$APP"
    adb install -r -t "$TEST"
  } > $LOG 2>&1
  INSTR=$(adb shell pm list instrumentation | tr -d '\r' | grep "(target=$PKG)" | head -1 | sed 's/^instrumentation:\([^ ]*\) .*/\1/')
  echo "Instrumentation: $INSTR" >> $LOG
  adb logcat -c
  adb shell am instrument -w -r "$INSTR" >> $LOG 2>&1
  if grep -q "OK (" $LOG && ! grep -qE "FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|shortMsg=" $LOG; then
    echo PASSED > $OUT/UI_TESTS
  else
    echo FAILED > $OUT/UI_TESTS
  fi
  # one-line summary at the end, e.g. "OK (12 tests)" or "Tests run: 12,  Failures: 1"
  grep -E "^OK \(|^Tests run:" $LOG | tail -1 > $OUT/UI_TESTS_SUMMARY || true
  adb logcat -d | grep -E "Walk|TestRunner|AndroidJUnitRunner" > $OUT/walk-log.txt 2>&1 || true
  adb pull /sdcard/Pictures/GymShots/. $OUT/screens/ || true
  adb pull /sdcard/Pictures/AppShots/. $OUT/screens/ || true
  adb logcat -d -t 3000 > $OUT/logcat.txt || true
  if [ ! -f e2e.sh ]; then adb logcat -d -s GrabbitE2E:I > $OUT/e2e-results.txt || true; fi
}

case $ROLE in
  ui)    run_ui ;;
  audit) bash e2e.sh || true; adb logcat -d -t 3000 > $OUT/logcat-audit.txt || true ;;
  e2e)   bash e2e.sh "$API" || true ;;
  *)     echo "unknown role: $ROLE"; exit 1 ;;
esac
exit 0
