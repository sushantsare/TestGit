#!/bin/bash
# Real-device-style checks + automated audit evidence (T3–T11, T14, T15) for Bahi Khata.
# Usage: e2e.sh [api-tag]   (no tag = main audit run). Never calls ./gradlew (emulator jobs have no Gradle).
TAG=${1:-33}
MAIN=0; [ -z "$1" ] && MAIN=1
OUT=/tmp/out; mkdir -p $OUT/screens $OUT/audit
PKG=com.sushant.bahikhata
ACT=$PKG/.MainActivity
REL=$OUT/bahi-khata.apk
DBG=$(ls app/build/outputs/apk/debug/*x86_64*.apk app/build/outputs/apk/debug/app-debug.apk 2>/dev/null | head -1)
LOG=$OUT/e2e-results-$TAG.txt
echo "== Bahi Khata e2e on Android $(adb shell getprop ro.build.version.release) (API $TAG) ==" > $LOG
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1
mycrash() { adb logcat -d | grep -A3 "FATAL EXCEPTION" | grep -c "Process: $PKG"; }
launch_ok() {
  adb shell am force-stop $PKG; adb logcat -c
  adb shell am start -W -n $ACT > /tmp/start.txt 2>&1; sleep 4
  adb shell pidof $PKG >/dev/null && [ "$(mycrash)" = "0" ]
}
prefs_ready() {   # debug build only: skip setup and switch SMS reading on, like a user who finished setup
  adb shell am force-stop $PKG
  adb shell "run-as $PKG mkdir -p shared_prefs"
  adb shell "run-as $PKG sh -c 'cat > shared_prefs/settings.xml'" <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="setupDone" value="true" />
    <boolean name="smsOn" value="true" />
    <boolean name="secureScreens" value="false" />
</map>
XML
}

# ---------- smoke on every API level (release APK, what he installs)
adb uninstall $PKG >/dev/null 2>&1
adb install -r $REL >> $LOG 2>&1
if launch_ok; then echo "launch=PASSED" >> $LOG; else echo "launch=FAILED" >> $LOG; adb logcat -d | grep -A 25 "FATAL EXCEPTION" | head -60 >> $LOG; fi
adb exec-out screencap -p > $OUT/screens/e2e_${TAG}_launch.png
adb shell monkey -p $PKG -s 7 --throttle 70 --pct-syskeys 0 -v 1500 > $OUT/audit/monkey_$TAG.txt 2>&1
CR=$(( $(mycrash) + $(adb logcat -d | grep -c "ANR in $PKG") ))
echo "monkey_crashes_$TAG=$CR" >> $LOG
adb logcat -d | grep -B2 -A25 "FATAL EXCEPTION" | grep -A27 "$PKG" | head -80 >> $LOG

# ---------- real SMS through the emulator's modem (debug build so prefs can be set like a finished setup)
SMSOK=SKIPPED
if [ -f "$DBG" ]; then
  adb uninstall $PKG >/dev/null 2>&1; adb install -r -t $DBG >> $LOG 2>&1
  adb shell pm grant $PKG android.permission.READ_SMS; adb shell pm grant $PKG android.permission.RECEIVE_SMS
  adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
  prefs_ready
  adb shell am start -W -n $ACT >/dev/null 2>&1; sleep 4
  adb shell input keyevent KEYCODE_HOME; sleep 1
  adb logcat -c
  P() { adb logcat -d -s BahiSms:I | grep -c "processed via=.* status=$1"; }
  # 1) app in background (receiver or live observer)
  adb emu sms send 5554 "Rs.412.00 spent on HDFC Bank Card 8812 at ZOMATO on 21-11-26. Not you? Call 18002586161"; sleep 12
  R1=$(P PENDING)
  # 2) OTP must be ignored (never a suggestion)
  adb emu sms send 5554 "Your OTP for transaction of Rs 1,299 at AMAZON is 482910. Do not share it with anyone."; sleep 10
  R2=$(P ignored)
  # 3) app killed: only the manifest receiver can catch it
  adb shell am force-stop $PKG; sleep 1
  adb emu sms send 5554 "Dear UPI user A/C X4417 debited by 230.0 on date 21Nov26 trf to RAVI KIRANA Refno 532511234567. -SBI"; sleep 15
  R3=$(adb logcat -d -s BahiSms:I | grep -c "processed via=receiver status=PENDING")
  # 4) unknown format -> needs a look
  adb emu sms send 5554 "Your acct 99887766 has a txn Rs 450.00 on 21/11. Bal Rs 1,000."; sleep 12
  R4=$(P LOOK)
  TOTAL=$(adb logcat -d -s BahiSms:I | grep -c "processed via=")
  PEND=$(P PENDING)
  NOTIF=$(adb shell dumpsys notification --noredact | grep -c "pkg=$PKG")
  adb shell cmd statusbar expand-notifications 2>/dev/null; sleep 2; adb exec-out screencap -p > $OUT/screens/e2e_${TAG}_sms_notification.png; adb shell cmd statusbar collapse 2>/dev/null
  # 5) opening the app re-scans the inbox: nothing may be processed twice
  adb logcat -c; adb shell am start -W -n $ACT >/dev/null 2>&1; sleep 6
  adb shell am start -n $ACT --es route sms >/dev/null 2>&1; sleep 3; adb exec-out screencap -p > $OUT/screens/e2e_${TAG}_sms_inbox.png
  DUP=$(adb logcat -d -s BahiSms:I | grep -c "processed via=")
  adb logcat -d -s BahiSms:I > $OUT/audit/sms_e2e_$TAG.txt
  echo "sms_pending=$PEND first_sms=$R1 sms_otp_ignored=$R2 sms_by_receiver_after_kill=$R3 sms_needs_look=$R4 processed_total=$TOTAL notifications=$NOTIF reprocessed_on_open=$DUP" >> $LOG
  if [ "$PEND" -ge 2 ] && [ "$R1" -ge 1 ] && [ "$R2" -ge 1 ] && [ "$R3" -ge 1 ] && [ "$R4" -ge 1 ] && [ "$TOTAL" = "4" ] && [ "$DUP" = "0" ]; then SMSOK=PASSED; else SMSOK=FAILED; fi
  echo "sms_e2e=$SMSOK" >> $LOG
  echo $SMSOK > $OUT/SMS_E2E_$TAG
fi
if [ $MAIN = 0 ]; then
  grep -q "launch=PASSED" $LOG && [ "$CR" = "0" ] && [ "$SMSOK" != "FAILED" ] && echo PASSED > $OUT/E2E_$TAG || echo FAILED > $OUT/E2E_$TAG
  exit 0
fi

# ---------- AUDIT (main run only)
A=$OUT/audit
# T3/T4/T5 debug build: monkey crash crawl + StrictMode + LeakCanary
if [ -f "$DBG" ]; then
  adb shell am force-stop $PKG; adb logcat -c
  adb shell am start -W -n $ACT >/dev/null 2>&1; sleep 4
  adb shell monkey -p $PKG -s 42 --throttle 80 --pct-syskeys 0 -v 5000 > $A/T3_monkey.txt 2>&1
  sleep 10
  adb logcat -d > $A/T3_logcat_debug.txt
  echo "T3 crashes=$(grep -A3 'FATAL EXCEPTION' $A/T3_logcat_debug.txt | grep -c "Process: $PKG") anrs=$(grep -c "ANR in $PKG" $A/T3_logcat_debug.txt)" >> $A/summary.txt
  echo "T4 strictmode=$(grep -c 'StrictMode policy violation' $A/T3_logcat_debug.txt)" >> $A/summary.txt
  grep -A14 "StrictMode policy violation" $A/T3_logcat_debug.txt | head -300 > $A/T4_strictmode.txt
  echo "T5 app_leaks=$(grep -oE '[0-9]+ APPLICATION LEAKS' $A/T3_logcat_debug.txt | awk '{s+=$1} END {print s+0}')" >> $A/summary.txt
  grep -A30 "APPLICATION LEAKS" $A/T3_logcat_debug.txt | head -120 > $A/T5_leaks.txt
fi
# release build for the rest
adb uninstall $PKG >/dev/null 2>&1; adb install -r $REL >> $LOG 2>&1
# T6 startup
for i in $(seq 1 10); do adb shell am force-stop $PKG; sleep 1; adb shell am start -W -n $ACT | grep TotalTime | awk '{print $2}'; done > $A/T6_cold.txt
for i in $(seq 1 5); do adb shell input keyevent KEYCODE_HOME; sleep 1; adb shell am start -W -n $ACT | grep TotalTime | awk '{print $2}'; done > $A/T6_warm.txt
echo "T6 cold_median=$(sort -n $A/T6_cold.txt | sed -n 5p) warm_median=$(sort -n $A/T6_warm.txt | sed -n 3p)" >> $A/summary.txt
# T7 jank
adb shell dumpsys gfxinfo $PKG reset >/dev/null
for i in 1 2 3 4 5; do adb shell input swipe 540 1700 540 600 250; adb shell input swipe 540 600 540 1700 250; done
adb shell dumpsys gfxinfo $PKG > $A/T7_gfxinfo.txt
echo "T7 $(grep -m1 'Janky frames' $A/T7_gfxinfo.txt)" >> $A/summary.txt
# T8 memory
adb shell dumpsys meminfo $PKG | grep -E "TOTAL PSS|TOTAL:" | head -2 > $A/T8_mem1.txt
for i in 1 2 3; do adb shell input keyevent KEYCODE_BACK; adb shell am start -n $ACT >/dev/null; sleep 2; done
adb shell dumpsys meminfo $PKG | grep -E "TOTAL PSS|TOTAL:" | head -2 > $A/T8_mem2.txt
echo "T8 before=$(head -1 $A/T8_mem1.txt | tr -s ' ') after=$(head -1 $A/T8_mem2.txt | tr -s ' ')" >> $A/summary.txt
# T9 process death
adb logcat -c; adb shell am start -n $ACT >/dev/null; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 1; adb shell am kill $PKG; sleep 1
adb shell am start -n $ACT >/dev/null; sleep 3
adb exec-out screencap -p > $OUT/screens/audit_T9_after_kill.png
echo "T9 crash_after_kill=$(mycrash)" >> $A/summary.txt
# T10 offline (Drive backup is the only network feature)
adb shell svc wifi disable; adb shell svc data disable; sleep 1; adb logcat -c
adb shell am force-stop $PKG; adb shell am start -W -n $ACT >/dev/null; sleep 3
adb shell monkey -p $PKG -s 11 --throttle 80 --pct-syskeys 0 -v 800 >/dev/null 2>&1
echo "T10 offline_crashes=$(mycrash)" >> $A/summary.txt; adb logcat -d | grep -B2 -A25 "FATAL EXCEPTION" | head -60 > $A/T10_crash.txt
adb shell svc wifi enable; adb shell svc data enable
# T11 battery modes
adb logcat -c; adb shell dumpsys deviceidle force-idle >/dev/null; adb shell am set-inactive $PKG true
adb shell am start -W -n $ACT >/dev/null; sleep 3
adb shell dumpsys deviceidle unforce >/dev/null; adb shell am set-inactive $PKG false
echo "T11 crashes=$(mycrash)" >> $A/summary.txt
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
echo "T15 apk_bytes=$(stat -c %s $REL) signature_v2=$(grep -c 'Verified using v2 scheme (APK Signature Scheme v2): true' $A/T15_apksigner.txt) native_libs=$(unzip -l $REL | grep -c '\.so$')" >> $A/summary.txt
unzip -l $REL | grep '\.so$' | awk '{print $4}' > $A/T15_native_libs.txt
$BT/zipalign -c -P 16 -v 4 $REL > $A/T15_zipalign.txt 2>&1; echo "T15 zipalign16_exit=$?" >> $A/summary.txt
$BT/aapt dump permissions $REL > $A/permissions.txt 2>&1
$BT/aapt dump badging $REL | head -3 > $A/badging.txt 2>&1
cat $A/summary.txt >> $LOG
grep -q "launch=PASSED" $LOG && grep -q "T3 crashes=0" $A/summary.txt && [ "$SMSOK" != "FAILED" ] && echo PASSED > $OUT/E2E || echo FAILED > $OUT/E2E
exit 0
