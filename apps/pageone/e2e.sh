#!/bin/bash
# Real-world checks with the release APK (what Sushant installs) + audit evidence.
# Usage: e2e.sh [api-tag]   (no tag = main audit run). Never calls ./gradlew (emulator jobs have no Gradle).
MAIN=0; [ -z "$1" ] && MAIN=1
TAG=${1:-$(adb shell getprop ro.build.version.sdk | tr -d '\r')}
OUT=/tmp/out; mkdir -p $OUT/screens $OUT/audit
A=$OUT/audit
PKG=com.sushant.pageone
ACT=$PKG/.MainActivity
REL=$OUT/pageone.apk
[ -f "$REL" ] || REL=$(ls $OUT/apks/*.apk 2>/dev/null | head -1)
LOG=$OUT/e2e-results-$TAG.txt
API=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
echo "== PageOne e2e on Android $(adb shell getprop ro.build.version.release | tr -d '\r') (API $API) ==" > $LOG
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1
mycrash() { adb logcat -d | grep -A3 "FATAL EXCEPTION" | grep -c "Process: $PKG"; }
shot() { sleep 1; adb exec-out screencap -p > "$OUT/screens/e2e_${TAG}_$1.png"; }
boundsof() { adb exec-out uiautomator dump /dev/tty 2>/dev/null | tr '>' '\n' | grep "resource-id=\"$1\"" | head -1 | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p'; }
hasid() { [ -n "$(boundsof "$1")" ]; }
tapid() { local b; b=$(boundsof "$1"); [ -z "$b" ] && return 1; set -- $b; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); }

# ---------- PDFs on the phone, like downloads/WhatsApp/Telegram
P=app/src/androidTest/assets/pdfs
adb shell "mkdir -p '/sdcard/Download/Lucent Books' '/sdcard/Download/Telegram' '/sdcard/Download/WhatsApp Documents'" >/dev/null 2>&1
adb push "$P/Lucent General English.pdf" "/sdcard/Download/Lucent Books/" >/dev/null 2>&1
adb push "$P/STET 2025 Paper 2 English.pdf" "/sdcard/Download/Telegram/" >/dev/null 2>&1
adb push "$P/Admit Card - Sushant Kumar.pdf" "/sdcard/Download/WhatsApp Documents/" >/dev/null 2>&1
adb push "$P/SBI Statement Oct 2026.pdf" "/sdcard/Download/" >/dev/null 2>&1
adb shell content call --uri content://media/external/file --method scan_volume --arg external_primary >/dev/null 2>&1 \
  || adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download >/dev/null 2>&1
sleep 5

# ---------- install the release APK fresh; first launch must not ask for anything
adb uninstall $PKG >/dev/null 2>&1
adb install -r $REL >> $LOG 2>&1
adb logcat -c
adb shell am start -W -n $ACT > /tmp/start.txt 2>&1; sleep 4
COLD=$(grep -E "TotalTime" /tmp/start.txt | grep -oE "[0-9]+")
echo "cold_start_ms=$COLD" >> $LOG
if adb shell pidof $PKG >/dev/null && [ "$(mycrash)" = "0" ]; then echo "launch=PASSED" >> $LOG; else echo "launch=FAILED" >> $LOG; adb logcat -d | grep -A 25 "FATAL EXCEPTION" | head -60 >> $LOG; fi
shot first_launch
DIALOG=$(adb exec-out uiautomator dump /dev/tty 2>/dev/null | grep -c "com.android.permissioncontroller")
echo "permission_prompt_on_launch=$([ "$DIALOG" = "0" ] && echo NONE || echo SHOWN)" >> $LOG

# setup guide: Welcome → Files access → Look → Done
SETUP=FAILED
if hasid setup; then
  for i in 1 2 3 4; do tapid setup_next; sleep 2; shot setup_$i; done
  hasid setup || SETUP=PASSED
fi
echo "setup_guide=$SETUP" >> $LOG

# grant All files access the way Settings would, then come back: Home must list the folders
adb shell appops set $PKG MANAGE_EXTERNAL_STORAGE allow
adb shell am force-stop $PKG
adb shell am start -W -n $ACT >/dev/null 2>&1; sleep 8; shot home
FOLDERS=$(adb exec-out uiautomator dump /dev/tty 2>/dev/null | tr '>' '\n' | grep -c 'resource-id="folder_row"')
echo "home_folders=$FOLDERS" >> $LOG
[ "$FOLDERS" -ge 3 ] && echo "library_scan=PASSED" >> $LOG || echo "library_scan=FAILED" >> $LOG

# ---------- open a PDF exactly like WhatsApp / Files does (VIEW intent with a content:// Uri), timed
idof() { adb shell content query --uri content://media/external/file --projection _id:_display_name 2>/dev/null | tr -d '\r' | grep "_display_name=$1" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1; }
ID=$(idof "STET 2025 Paper 2 English.pdf")
OPEN=FAILED
if [ -n "$ID" ]; then
  adb shell am force-stop $PKG
  adb shell am start -W -a android.intent.action.VIEW -d "content://media/external/file/$ID" -t application/pdf -n $ACT > /tmp/view.txt 2>&1
  T=$(grep -E "TotalTime" /tmp/view.txt | grep -oE "[0-9]+")
  sleep 3; shot opened_from_whatsapp_style
  hasid page_0 && OPEN=PASSED
  echo "open_from_other_app_ms=$T" >> $LOG
fi
echo "open_from_other_app=$OPEN (id=$ID)" >> $LOG

# volume keys off by default must not turn pages or crash; back to Home
adb shell input keyevent KEYCODE_VOLUME_DOWN; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 2

# ---------- password PDF opens only with the right password
PID=$(idof "SBI Statement Oct 2026.pdf")
PW=FAILED
if [ -n "$PID" ]; then
  adb shell am start -a android.intent.action.VIEW -d "content://media/external/file/$PID" -t application/pdf -n $ACT >/dev/null 2>&1; sleep 4
  if hasid password_field; then
    tapid password_field; adb shell input text 1234; sleep 1; tapid password_open; sleep 4; shot password_opened
    hasid page_0 && PW=PASSED
  fi
fi
echo "password_pdf=$PW (id=$PID)" >> $LOG

# ---------- crashes, permissions, network
CR=$(mycrash); echo "crashes=$CR" >> $LOG
adb logcat -d | grep -A 30 "FATAL EXCEPTION" | head -80 >> $LOG
adb shell dumpsys package $PKG | grep -E "android.permission\.[A-Z_]+" -o | sort -u > $A/permissions-$TAG.txt
grep -q "android.permission.INTERNET" $A/permissions-$TAG.txt && echo "no_internet=FAILED" >> $LOG || echo "no_internet=PASSED" >> $LOG

if [ "$MAIN" = "1" ]; then
  cp $LOG $OUT/e2e-results.txt
  # memory after reading (audit evidence)
  adb shell dumpsys meminfo $PKG | head -40 > $A/meminfo.txt 2>/dev/null
  adb shell dumpsys gfxinfo $PKG > $A/gfxinfo.txt 2>/dev/null
fi
PASS=1
for k in launch setup_guide library_scan open_from_other_app password_pdf no_internet; do grep -q "^$k=PASSED" $LOG || PASS=0; done
[ "$CR" = "0" ] || PASS=0
[ "$PASS" = "1" ] && echo PASSED > $OUT/E2E_$TAG || echo FAILED > $OUT/E2E_$TAG
[ "$MAIN" = "1" ] && cp $OUT/E2E_$TAG $OUT/E2E
exit 0
