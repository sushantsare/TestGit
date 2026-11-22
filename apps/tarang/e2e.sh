#!/bin/bash
# Real-device-style checks + automated audit evidence for Tarang.
# Usage: e2e.sh [api-tag]   (no tag = main audit run). Never calls ./gradlew (emulator jobs have no Gradle).
TAG=${1:-35}
MAIN=0; [ -z "$1" ] && MAIN=1
OUT=/tmp/out; mkdir -p $OUT/screens $OUT/audit
A=$OUT/audit
PKG=com.sushant.tarang
ACT=$PKG/.MainActivity
REL=$OUT/tarang.apk
[ -f "$REL" ] || REL=$(ls $OUT/apks/*.apk 2>/dev/null | head -1)
LOG=$OUT/e2e-results-$TAG.txt
API=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
echo "== Tarang e2e on Android $(adb shell getprop ro.build.version.release | tr -d '\r') (API $API) ==" > $LOG
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1
mycrash() { adb logcat -d | grep -A3 "FATAL EXCEPTION" | grep -c "Process: $PKG"; }
shot() { sleep 1; adb exec-out screencap -p > "$OUT/screens/e2e_${TAG}_$1.png"; }
perm() { if [ "$API" -ge 33 ]; then echo android.permission.READ_MEDIA_AUDIO; else echo android.permission.READ_EXTERNAL_STORAGE; fi; }
state() { adb shell dumpsys media_session | grep -A30 "$PKG" | grep -m1 -o "state=[0-9]*" | cut -d= -f2; }

# ---------- put real songs on the phone (same files the walkthrough uses) and let Android index them
MEDIA=app/src/androidTest/assets/media
for d in "$MEDIA"/*/; do
  n=$(basename "$d"); adb shell mkdir -p "/sdcard/Music/$n" >/dev/null 2>&1
  for f in "$d"*; do adb push "$f" "/sdcard/Music/$n/" >/dev/null 2>&1; done
done
adb shell content call --uri content://media/external/file --method scan_volume --arg external_primary >/dev/null 2>&1 \
  || adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music >/dev/null 2>&1
sleep 6
SONGS=$(adb shell content query --uri content://media/external/audio/media --projection _id:_display_name 2>/dev/null | grep -c "_display_name=")
echo "songs_on_phone=$SONGS" >> $LOG

# ---------- install the release APK (what he installs) and launch without permission
adb uninstall $PKG >/dev/null 2>&1
adb install -r $REL >> $LOG 2>&1
adb logcat -c
adb shell am start -W -n $ACT > /tmp/start.txt 2>&1; sleep 4
if adb shell pidof $PKG >/dev/null && [ "$(mycrash)" = "0" ]; then echo "launch=PASSED" >> $LOG; else echo "launch=FAILED" >> $LOG; adb logcat -d | grep -A 25 "FATAL EXCEPTION" | head -60 >> $LOG; fi
shot welcome
# permission prompt appears in context when the user taps Allow
adb shell input tap 540 2100 >/dev/null 2>&1; sleep 2; shot permission_prompt
adb shell pm grant $PKG $(perm); adb shell am force-stop $PKG
adb shell am start -W -n $ACT >/dev/null 2>&1; sleep 6; shot home_with_songs

# ---------- real playback: open a song the way a file manager would, then background it
ID=$(adb shell content query --uri content://media/external/audio/media --projection _id:_display_name 2>/dev/null | grep "Kesariya" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | head -1)
PLAY=FAILED
if [ -n "$ID" ]; then
  adb shell am start -a android.intent.action.VIEW -d "content://media/external/audio/media/$ID" -t audio/mpeg -n $ACT >/dev/null 2>&1; sleep 6
  shot player_from_file
  S1=$(state)
  adb shell input keyevent KEYCODE_HOME; sleep 10
  S2=$(state)
  adb shell cmd statusbar expand-notifications >/dev/null 2>&1; sleep 2; shot notification; adb shell cmd statusbar collapse >/dev/null 2>&1
  adb shell input keyevent KEYCODE_MEDIA_PAUSE; sleep 2; S3=$(state)
  adb shell input keyevent KEYCODE_MEDIA_PLAY; sleep 2; S4=$(state)
  adb shell input keyevent KEYCODE_MEDIA_NEXT; sleep 3; S5=$(state)
  echo "playback open=$S1 background=$S2 paused=$S3 resumed=$S4 after_next=$S5 (3=playing 2=paused)" >> $LOG
  [ "$S1" = "3" ] && [ "$S2" = "3" ] && [ "$S3" = "2" ] && [ "$S4" = "3" ] && PLAY=PASSED
  adb shell input keyevent KEYCODE_MEDIA_PAUSE; sleep 1
fi
echo "playback=$PLAY" >> $LOG
# fresh start after the app was closed: "Continue listening" card, no mini player
adb shell am force-stop $PKG; adb shell am start -W -n $ACT >/dev/null 2>&1; sleep 5; shot continue_listening

# ---------- monkey (random taps everywhere)
adb logcat -c
adb shell monkey -p $PKG -s 7 --throttle 80 --pct-syskeys 0 --pct-appswitch 0 -v 1500 > $A/monkey_$TAG.txt 2>&1
CR=$(( $(mycrash) + $(adb logcat -d | grep -c "ANR in $PKG") ))
echo "monkey_crashes_$TAG=$CR" >> $LOG
adb logcat -d | grep -B2 -A25 "FATAL EXCEPTION" | grep -A27 "$PKG" | head -80 >> $LOG
adb shell input keyevent KEYCODE_MEDIA_PAUSE

if [ $MAIN = 1 ]; then
  echo "" > $A/summary.txt
  echo "T3 crashes=$CR" >> $A/summary.txt
  # T5 permission denied: app keeps working with a clear message, no crash
  adb shell pm revoke $PKG $(perm); adb logcat -c; adb shell am start -W -n $ACT >/dev/null 2>&1; sleep 3; shot denied
  echo "T5 denied_crashes=$(mycrash)" >> $A/summary.txt
  adb shell pm grant $PKG $(perm)
  # T6 startup
  for i in $(seq 1 10); do adb shell am force-stop $PKG; sleep 1; adb shell am start -W -n $ACT | grep TotalTime | awk '{print $2}'; done > $A/T6_cold.txt
  for i in $(seq 1 5); do adb shell input keyevent KEYCODE_HOME; sleep 1; adb shell am start -W -n $ACT | grep TotalTime | awk '{print $2}'; done > $A/T6_warm.txt
  echo "T6 cold_median=$(sort -n $A/T6_cold.txt | sed -n 5p) warm_median=$(sort -n $A/T6_warm.txt | sed -n 3p)" >> $A/summary.txt
  # T7 scrolling jank on the library
  adb shell am start -n $ACT --es route library >/dev/null 2>&1; sleep 3
  adb shell dumpsys gfxinfo $PKG reset >/dev/null
  for i in 1 2 3 4 5; do adb shell input swipe 540 1800 540 700 300; sleep 0.4; adb shell input swipe 540 700 540 1800 300; sleep 0.4; done
  adb shell dumpsys gfxinfo $PKG > $A/T7_gfxinfo.txt
  echo "T7 frames=$(grep -m1 'Total frames rendered' $A/T7_gfxinfo.txt | awk '{print $4}') $(grep -m1 'Janky frames' $A/T7_gfxinfo.txt)" >> $A/summary.txt
  # T8 memory
  adb shell dumpsys meminfo $PKG | grep -E "TOTAL PSS|TOTAL:" | head -2 > $A/T8_mem1.txt
  for r in folders library stats home; do adb shell am start -n $ACT --es route $r >/dev/null; sleep 2; done
  adb shell dumpsys meminfo $PKG | grep -E "TOTAL PSS|TOTAL:" | head -2 > $A/T8_mem2.txt
  echo "T8 before=$(head -1 $A/T8_mem1.txt | tr -s ' ') after=$(head -1 $A/T8_mem2.txt | tr -s ' ')" >> $A/summary.txt
  # T10 offline: the app has no internet permission at all
  adb shell svc wifi disable; adb shell svc data disable; adb logcat -c
  adb shell am force-stop $PKG; adb shell am start -W -n $ACT >/dev/null; sleep 3
  adb shell monkey -p $PKG -s 11 --throttle 80 --pct-syskeys 0 --pct-appswitch 0 -v 600 >/dev/null 2>&1
  echo "T10 offline_crashes=$(mycrash)" >> $A/summary.txt
  adb shell svc wifi enable; adb shell svc data enable
  # T11 battery modes
  adb logcat -c; adb shell dumpsys deviceidle force-idle >/dev/null; adb shell am set-inactive $PKG true
  adb shell am start -W -n $ACT >/dev/null; sleep 3
  adb shell dumpsys deviceidle unforce >/dev/null; adb shell am set-inactive $PKG false
  echo "T11 crashes=$(mycrash)" >> $A/summary.txt
  # T14 big font, tablet, landscape
  adb shell am start -n $ACT --es route home >/dev/null 2>&1
  adb shell settings put system font_scale 2.0; sleep 3; shot T14_font200_home
  adb shell am start -n $ACT --es route settings >/dev/null 2>&1; sleep 2; shot T14_font200_settings
  adb shell settings put system font_scale 1.0
  adb shell wm size 1600x2560; adb shell wm density 320; sleep 3
  adb shell am start -n $ACT --es route library >/dev/null 2>&1; sleep 3; shot T14_tablet_library
  adb shell am start -n $ACT --es route home >/dev/null 2>&1; sleep 2; shot T14_tablet_home
  adb shell wm size 2560x1600; sleep 3; shot T14_tablet_landscape
  adb shell wm size reset; adb shell wm density reset; sleep 2
  adb shell cmd uimode night yes; sleep 3; shot T14_system_dark; adb shell cmd uimode night no
  echo "T14 crashes=$(mycrash)" >> $A/summary.txt
  # T15 apk checks
  BT=$(ls -d $ANDROID_HOME/build-tools/* 2>/dev/null | sort -V | tail -1)
  $BT/apksigner verify -v --print-certs $REL > $A/T15_apksigner.txt 2>&1
  echo "T15 apk_bytes=$(stat -c %s $REL) signature_v2=$(grep -c 'Verified using v2 scheme (APK Signature Scheme v2): true' $A/T15_apksigner.txt) native_libs=$(unzip -l $REL | grep -c '\.so$')" >> $A/summary.txt
  $BT/zipalign -c -P 16 -v 4 $REL > $A/T15_zipalign.txt 2>&1; echo "T15 zipalign16_exit=$?" >> $A/summary.txt
  $BT/aapt dump permissions $REL > $A/permissions.txt 2>&1
  echo "T15 internet_permission=$(grep -c 'android.permission.INTERNET' $A/permissions.txt)" >> $A/summary.txt
  $BT/aapt dump badging $REL | head -3 > $A/badging.txt 2>&1
  cat $A/summary.txt >> $LOG
fi
grep -q "launch=PASSED" $LOG && grep -q "playback=PASSED" $LOG && grep -q "monkey_crashes_$TAG=0" $LOG && echo PASSED > $OUT/E2E || echo FAILED > $OUT/E2E
exit 0
