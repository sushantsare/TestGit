#!/bin/bash
# Real-device-style checks + automated audit evidence (T1, T3–T11, T14, T15).
# Usage: e2e.sh [api-tag]   (no tag = main emulator run on API 33, which also runs the full audit)
TAG=${1:-33}
OUT=/tmp/out; mkdir -p $OUT/screens $OUT/audit
PKG=com.sushant.inknote
ACT=$PKG/.MainActivity
REL=$OUT/inknote.apk
DBG=app/build/outputs/apk/debug/app-debug.apk
LOG=$OUT/e2e-results-$TAG.txt
echo "== InkNote e2e on Android $(adb shell getprop ro.build.version.release) (API $TAG) ==" > $LOG
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1

# crashes of InkNote only (other system apps sometimes crash on emulators)
mycrash() { adb logcat -d | grep -A3 "FATAL EXCEPTION" | grep -c "Process: $PKG"; }
launch_ok() {
  adb shell am force-stop $PKG; adb logcat -c
  adb shell am start -W -n $ACT > /tmp/start.txt 2>&1; sleep 3
  adb shell pidof $PKG >/dev/null && ! adb logcat -d | grep -q "FATAL EXCEPTION"
}

# ---------- smoke on every API level ----------
adb uninstall $PKG >/dev/null 2>&1
adb install -r $REL >> $LOG 2>&1
if launch_ok; then echo "launch=PASSED" >> $LOG; else echo "launch=FAILED" >> $LOG; adb logcat -d | grep -A 20 "FATAL EXCEPTION" | head -40 >> $LOG; fi
adb exec-out screencap -p > $OUT/screens/e2e_${TAG}_launch.png
adb shell monkey -p $PKG -s 7 --throttle 60 --pct-syskeys 0 -v 1500 > $OUT/audit/monkey_$TAG.txt 2>&1
CR=$(( $(mycrash) + $(adb logcat -d | grep -c "ANR in $PKG") ))
echo "monkey_crashes_$TAG=$CR" >> $LOG
adb logcat -d | grep -B2 -A25 "FATAL EXCEPTION" | head -80 >> $LOG
[ "$TAG" != "33" ] && { grep -q "launch=PASSED" $LOG && [ "$CR" = "0" ] && echo PASSED > $OUT/E2E_$TAG || echo FAILED > $OUT/E2E_$TAG; exit 0; }

# ---------- AUDIT (main run only) ----------
A=$OUT/audit
# T1 lint
(./gradlew :app:lintDebug --offline -q > $A/lint.log 2>&1 || ./gradlew :app:lintDebug -q >> $A/lint.log 2>&1); cp app/build/reports/lint-results-debug.* $A/ 2>/dev/null
# T3/T4/T5 debug build: monkey crash crawl + StrictMode + LeakCanary
if [ -f "$DBG" ]; then
  adb uninstall $PKG >/dev/null 2>&1; adb install -r -g $DBG >> $LOG 2>&1; adb logcat -c
  adb shell am start -W -n $ACT >/dev/null 2>&1; sleep 4
  adb shell monkey -p $PKG -s 42 --throttle 80 --pct-syskeys 0 -v 5000 > $A/T3_monkey.txt 2>&1
  sleep 10
  adb logcat -d > $A/T3_logcat_debug.txt
  echo "T3 crashes=$(grep -A3 'FATAL EXCEPTION' $A/T3_logcat_debug.txt | grep -c "Process: $PKG") anrs=$(grep -c "ANR in $PKG" $A/T3_logcat_debug.txt)" >> $A/summary.txt
  echo "T4 strictmode=$(grep -c 'StrictMode policy violation' $A/T3_logcat_debug.txt)" >> $A/summary.txt
  grep -A3 "StrictMode policy violation" $A/T3_logcat_debug.txt | head -120 > $A/T4_strictmode.txt
  echo "T5 leaks=$(grep -cE 'HEAP ANALYSIS RESULT|APPLICATION LEAKS' $A/T3_logcat_debug.txt) leakcanary_lines=$(grep -c LeakCanary $A/T3_logcat_debug.txt)" >> $A/summary.txt
  grep -A30 "APPLICATION LEAKS" $A/T3_logcat_debug.txt | head -120 > $A/T5_leaks.txt
fi
# Release build for the rest
adb uninstall $PKG >/dev/null 2>&1; adb install -r $REL >> $LOG 2>&1
# T6 startup
for i in $(seq 1 10); do adb shell am force-stop $PKG; sleep 1; adb shell am start -W -n $ACT | grep TotalTime | awk '{print $2}'; done > $A/T6_cold.txt
for i in $(seq 1 5); do adb shell input keyevent KEYCODE_HOME; sleep 1; adb shell am start -W -n $ACT | grep TotalTime | awk '{print $2}'; done > $A/T6_warm.txt
echo "T6 cold_median=$(sort -n $A/T6_cold.txt | sed -n 5p) warm_median=$(sort -n $A/T6_warm.txt | sed -n 3p)" >> $A/summary.txt
# T7 jank (scroll home)
adb shell dumpsys gfxinfo $PKG reset >/dev/null
for i in 1 2 3 4 5; do adb shell input swipe 540 1700 540 600 250; adb shell input swipe 540 600 540 1700 250; done
adb shell dumpsys gfxinfo $PKG > $A/T7_gfxinfo.txt
echo "T7 $(grep -m1 'Janky frames' $A/T7_gfxinfo.txt)" >> $A/summary.txt
# T8 memory
adb shell dumpsys meminfo $PKG | grep -E "TOTAL PSS|TOTAL:" | head -2 > $A/T8_mem1.txt
for i in 1 2 3; do adb shell input keyevent KEYCODE_BACK; adb shell am start -n $ACT >/dev/null; sleep 2; done
adb shell dumpsys meminfo $PKG | grep -E "TOTAL PSS|TOTAL:" | head -2 > $A/T8_mem2.txt
echo "T8 before=$(head -1 $A/T8_mem1.txt | tr -s ' ') after=$(head -1 $A/T8_mem2.txt | tr -s ' ')" >> $A/summary.txt
# T9 process death: open new-note sheet, kill in background, come back
adb logcat -c
adb shell am start -n $ACT >/dev/null; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 1; adb shell am kill $PKG; sleep 1
adb shell am start -n $ACT >/dev/null; sleep 3
adb exec-out screencap -p > $OUT/screens/audit_T9_after_kill.png
echo "T9 crash_after_kill=$(mycrash)" >> $A/summary.txt
# T10 offline
adb shell svc wifi disable; adb shell svc data disable; sleep 1; adb logcat -c
adb shell am force-stop $PKG; adb shell am start -W -n $ACT >/dev/null; sleep 3
adb shell monkey -p $PKG -s 11 --throttle 80 --pct-syskeys 0 -v 800 >/dev/null 2>&1
echo "T10 offline_crashes=$(mycrash)" >> $A/summary.txt; adb logcat -d | grep -B2 -A25 "FATAL EXCEPTION" | head -60 > $A/T10_crash.txt
adb shell svc wifi enable; adb shell svc data enable
# T11 battery modes
adb logcat -c; adb shell dumpsys deviceidle force-idle >/dev/null; adb shell am set-inactive $PKG true
adb shell am start -W -n $ACT >/dev/null; sleep 3
adb shell dumpsys deviceidle unforce >/dev/null; adb shell am set-inactive $PKG false
echo "T11 crashes=$(mycrash)" >> $A/summary.txt; adb logcat -d | grep -B2 -A25 "FATAL EXCEPTION" | head -60 > $A/T11_crash.txt
# T14 config stress
adb logcat -c
adb shell settings put system accelerometer_rotation 0
for r in 1 0 1 0 1 0; do adb shell settings put system user_rotation $r; sleep 1.2; done
adb shell cmd uimode night yes; sleep 2; adb exec-out screencap -p > $OUT/screens/audit_T14_night.png; adb shell cmd uimode night no; sleep 1
adb shell settings put system font_scale 2.0; sleep 2; adb exec-out screencap -p > $OUT/screens/audit_T14_font200.png
adb shell settings put system font_scale 1.0
adb shell wm size 1600x2560; adb shell wm density 320; sleep 3; adb exec-out screencap -p > $OUT/screens/audit_T14_tablet.png
adb shell wm size reset; adb shell wm density reset; sleep 2
echo "T14 crashes=$(mycrash)" >> $A/summary.txt
adb logcat -d | grep -B2 -A25 "FATAL EXCEPTION" | head -60 > $A/T14_crash.txt
# T15 apk checks
BT=$(ls -d $ANDROID_HOME/build-tools/* 2>/dev/null | sort -V | tail -1)
$BT/apksigner verify -v --print-certs $REL > $A/T15_apksigner.txt 2>&1
echo "T15 signature=$(grep -c 'Verified using v2 scheme (APK Signature Scheme v2): true' $A/T15_apksigner.txt) native_libs=$(unzip -l $REL | grep -c '\.so$')" >> $A/summary.txt
unzip -l $REL | grep '\.so$' | awk '{print $4}' > $A/T15_native_libs.txt
$BT/zipalign -c -P 16 -v 4 $REL > $A/T15_zipalign.txt 2>&1; echo "T15 zipalign16_exit=$?" >> $A/summary.txt
$BT/aapt dump permissions $REL > $A/permissions.txt 2>&1
$BT/aapt dump badging $REL | head -3 > $A/badging.txt 2>&1
cat $A/summary.txt >> $LOG
grep -q "launch=PASSED" $LOG && grep -q "T3 crashes=0" $A/summary.txt && echo PASSED > $OUT/E2E || echo FAILED > $OUT/E2E
exit 0
