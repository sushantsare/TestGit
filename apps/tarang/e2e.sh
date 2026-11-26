#!/bin/bash
# Real-device-style checks + automated audit evidence for Tarang.
# Usage: e2e.sh [api-tag]   (no tag = main audit run). Never calls ./gradlew (emulator jobs have no Gradle).
MAIN=0; [ -z "$1" ] && MAIN=1
TAG=${1:-$(adb shell getprop ro.build.version.sdk | tr -d '\r')}
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
# find a button by its test tag (the app exposes test tags as resource ids): prints "left top right bottom"
boundsof() { adb exec-out uiautomator dump /dev/tty 2>/dev/null | tr '>' '\n' | grep "resource-id=\"$1\"" | head -1 | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p'; }
hasid() { [ -n "$(boundsof "$1")" ]; }
tapid() { local b; b=$(boundsof "$1"); [ -z "$b" ] && return 1; set -- $b; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); }
perm() { if [ "$API" -ge 33 ]; then echo android.permission.READ_MEDIA_AUDIO; else echo android.permission.READ_EXTERNAL_STORAGE; fi; }
# playback state of Tarang's session: 3 = playing, 2 = paused (Android 13 prints "state=3", 14+ "state=PLAYING(3)")
state() { adb shell dumpsys media_session | tr -d '\r' | grep -A40 "$PKG" | grep -m1 -oE "state=(PlaybackState \{state=)?[A-Z_]*\(?[0-9]+" | grep -oE "[0-9]+$"; }

# ---------- put real songs on the phone (same files the walkthrough uses) and let Android index them
MEDIA=app/src/androidTest/assets/media
for d in "$MEDIA"/*/; do
  n=$(basename "$d"); adb shell "mkdir -p '/sdcard/Music/$n'" >/dev/null 2>&1
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
# first-launch setup guide: Welcome → Music access (asked in context) → … → Done
SETUP=FAILED
if hasid setup; then
  tapid setup_next; sleep 2; shot setup_music
  tapid allow_music; sleep 2; shot permission_prompt
  adb shell pm grant $PKG $(perm); adb shell input keyevent KEYCODE_BACK; sleep 2
  for i in 1 2 3 4 5 6; do hasid setup || break; tapid setup_next; sleep 2; shot setup_$i; done
  hasid setup || SETUP=PASSED
fi
echo "setup_guide=$SETUP" >> $LOG
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
fi
echo "playback=$PLAY" >> $LOG

# ---------- real finger gestures on the player (Android's own input, not the test framework) + frame timing
# play a whole folder (several songs) so a cover swipe has a next song, then open the full player
adb shell am start -n $ACT >/dev/null 2>&1; sleep 3
hasid full_player && { adb shell input keyevent KEYCODE_BACK; sleep 2; }
tapid tab_folders || echo "e2e: Folders tab not found" >> $LOG; sleep 2
tapid folder_Bollywood || echo "e2e: Bollywood folder not found" >> $LOG; sleep 2
tapid play_all || echo "e2e: Play all not found" >> $LOG; sleep 3
tapid mini_player; sleep 2
GEST=PASSED
if hasid full_player; then
  shot gesture_full
  B=$(boundsof player_cover); set -- $B; CX=$(( ($1 + $3) / 2 )); CY=$(( ($2 + $4) / 2 ))
  adb shell dumpsys gfxinfo $PKG reset >/dev/null
  # cover swipe left: the song changes
  T0=$(adb shell dumpsys media_session | tr -d '\r' | grep -A40 "$PKG" | grep -m1 -o "description=[^,]*")
  adb shell input swipe $((CX + 300)) $CY $((CX - 300)) $CY 180; sleep 2
  T1=$(adb shell dumpsys media_session | tr -d '\r' | grep -A40 "$PKG" | grep -m1 -o "description=[^,]*")
  [ "$T0" != "$T1" ] && echo "cover_swipe=PASSED" >> $LOG || { echo "cover_swipe=FAILED ($T0)" >> $LOG; GEST=FAILED; }
  shot gesture_after_cover_swipe
  # slow pull down in steps with pictures at 25/50/75 % (Android 11+ can hold the finger down between steps)
  if [ "$API" -ge 30 ]; then
    H=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | cut -dx -f2)
    STEP=$(( H * 19 / 100 )); Y=$CY
    adb shell input motionevent DOWN $CX $Y
    for p in 25 50 75; do
      for k in 1 2 3 4 5 6 7 8; do Y=$(( Y + STEP / 8 )); adb shell input motionevent MOVE $CX $Y; done
      sleep 0.4; shot gesture_pull_down_$p
    done
    adb shell input motionevent UP $CX $Y; sleep 2
  else
    adb shell input swipe $CX $CY $CX $(( CY + 900 )) 600; sleep 2
  fi
  hasid mini_player && ! hasid full_player && echo "pull_down=PASSED" >> $LOG || { echo "pull_down=FAILED" >> $LOG; GEST=FAILED; }
  shot gesture_after_pull_down
  # fast flick up on the mini player opens it, fast flick down closes it
  B=$(boundsof mini_player); set -- $B; MX=$(( ($1 + $3) / 2 )); MY=$(( ($2 + $4) / 2 ))
  adb shell input swipe $MX $MY $MX $(( MY - 700 )) 90; sleep 2
  hasid full_player && echo "flick_up=PASSED" >> $LOG || { echo "flick_up=FAILED" >> $LOG; GEST=FAILED; }
  B=$(boundsof player_title); set -- $B; TX=$(( ($1 + $3) / 2 )); TY=$(( ($2 + $4) / 2 ))
  adb shell input swipe $TX $TY $TX $(( TY + 900 )) 90; sleep 2
  hasid mini_player && ! hasid full_player && echo "flick_down=PASSED" >> $LOG || { echo "flick_down=FAILED" >> $LOG; GEST=FAILED; }
  adb shell dumpsys gfxinfo $PKG > $A/gestures_gfxinfo_$TAG.txt
  echo "gesture_frames total=$(grep -m1 'Total frames rendered' $A/gestures_gfxinfo_$TAG.txt | awk '{print $4}') $(grep -m1 'Janky frames' $A/gestures_gfxinfo_$TAG.txt) p50=$(grep -m1 '50th percentile' $A/gestures_gfxinfo_$TAG.txt | awk '{print $3}') p90=$(grep -m1 '90th percentile' $A/gestures_gfxinfo_$TAG.txt | awk '{print $3}')" >> $LOG
else
  GEST=FAILED; echo "gestures: full player did not open" >> $LOG
fi
echo "gestures=$GEST" >> $LOG
adb shell input keyevent KEYCODE_MEDIA_PAUSE; sleep 2
# ---------- quit and come back: the mini player shows the same song, paused, at the same spot (no banner)
desc() { adb shell dumpsys media_session | tr -d '\r' | grep -A40 "$PKG" | grep -m1 -o "description=[^,]*"; }
pos() { adb shell dumpsys media_session | tr -d '\r' | grep -A40 "$PKG" | grep -m1 -oE "position=[0-9]+" | grep -oE "[0-9]+"; }
D0=$(desc); P0=$(pos)
adb shell am force-stop $PKG; sleep 1; adb shell am start -W -n $ACT >/dev/null 2>&1; sleep 6; shot resume_after_quit
D1=$(desc); P1=$(pos); S1=$(state)
RES=FAILED
if hasid mini_player && ! hasid continue_card && [ -n "$D0" ] && [ "$D0" = "$D1" ] && [ "$S1" != "3" ]; then
  # position within 6 s of where it stopped (it is saved on pause)
  if [ -n "$P0" ] && [ -n "$P1" ] && [ $(( P1 > P0 ? P1 - P0 : P0 - P1 )) -le 6000 ]; then RES=PASSED; fi
fi
echo "resume song0='$D0' song1='$D1' pos0=$P0 pos1=$P1 state=$S1 mini=$(hasid mini_player && echo yes || echo no)" >> $LOG
echo "resume=$RES" >> $LOG
# ▶ continues from there
tapid btn_play; sleep 3; S2=$(state); echo "resume_play state=$S2" >> $LOG; shot resume_playing
adb shell input keyevent KEYCODE_MEDIA_PAUSE; sleep 1

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
  for r in folders library settings stats home; do adb shell am start -n $ACT --es route $r >/dev/null; sleep 2; done
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
grep -q "launch=PASSED" $LOG && grep -q "playback=PASSED" $LOG && grep -q "gestures=PASSED" $LOG && grep -q "resume=PASSED" $LOG && grep -q "setup_guide=PASSED" $LOG && grep -q "monkey_crashes_$TAG=0" $LOG && echo PASSED > $OUT/E2E || echo FAILED > $OUT/E2E
exit 0
