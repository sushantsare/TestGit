#!/bin/bash
# Real-world tile tests without instrumentation (Android's normal background rules apply),
# with the emulator's real GPS receiver fed by "adb emu geo fix". Usage: e2e.sh [api-tag]
TAG=${1:-33}
OUT=/tmp/out; mkdir -p $OUT/screens
PKG=com.sushant.speedtile
TILE=$PKG/.SpeedTileService
LOG=$OUT/e2e-results-$TAG.txt
RES=$OUT/E2E_$TAG
echo "== Speed Tile real tile tests on Android $(adb shell getprop ro.build.version.release) (API $TAG) ==" > $LOG
LON=85.1000
feed() { for i in $(seq 1 $1); do LON=$(python3 -c "print(f'{$LON + 0.00015:.5f}')"); r=$(adb emu geo fix $LON 25.6000 50 9 29 2>&1); echo "$r" | grep -q KO && adb emu geo fix $LON 25.6000 50 9 >/dev/null 2>&1; sleep 1; done; }
count() { adb logcat -d -s SpeedTile:I | grep -c "$1"; }
reset_app() { adb shell am force-stop $PKG; adb shell input keyevent KEYCODE_HOME; sleep 2; adb logcat -c; }

adb install -r $OUT/speed-tile.apk >> $LOG 2>&1
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION
adb shell settings put secure location_mode 3
adb shell settings put global hide_error_dialogs 1
adb root > /dev/null 2>&1; sleep 3; adb wait-for-device
for attempt in 1 2 3 4; do
  adb shell settings put secure sysui_qs_tiles "custom\($TILE\),wifi,bt,flashlight,dnd,airplane,rotation,location"
  adb shell 'kill $(pidof com.android.systemui)' >> $LOG 2>&1; sleep 12
  adb shell settings get secure sysui_qs_tiles | grep -q speedtile && break
  adb shell cmd statusbar add-tile $TILE >> $LOG 2>&1; sleep 4
  adb shell settings get secure sysui_qs_tiles | grep -q speedtile && break
  echo "tile placement attempt $attempt failed" >> $LOG
done
echo "tile placed: $(adb shell settings get secure sysui_qs_tiles | grep -c speedtile)" >> $LOG
adb emu geo fix 85.1000 25.6000 50 9

scenario() { # name, how
  NAME=$1
  reset_app
  adb shell cmd statusbar expand-settings; sleep 3
  case $2 in
    tile) adb shell cmd statusbar click-tile $TILE ;;
    direct) adb shell cmd statusbar collapse; adb shell am start -n $PKG/.StartActivity >> $LOG 2>&1 ;;
  esac
  sleep 3
  adb exec-out screencap -p > $OUT/screens/${TAG}_${NAME}_1_after_tap.png
  feed 6
  adb shell cmd statusbar expand-settings
  feed 4
  adb exec-out screencap -p > $OUT/screens/${TAG}_${NAME}_2_panel_open.png
  local tapped=$(count "Tile tapped") handoff=$(count "Hand-off screen opened") inplace=$(count "Started in place") svc=$(count "GPS updates requested") fixes=$(count "GPS fix") sats=$(count "GNSS status")
  adb shell cmd statusbar collapse; sleep 4
  local closed=$(count "Quick Settings closed: speed off")
  echo "[$NAME] tapped=$tapped handoff=$handoff inplace=$inplace service=$svc fixes=$fixes gnss=$sats closedOff=$closed" >> $LOG
  adb logcat -d | grep -iE "SpeedTile|speedtile.*(START|BAL|blocked|denied)|Background activity|Abort background|balAllowed|launch blocked|FGS.*speedtile|speedtile.*FGS" | cut -c1-240 | tail -25 >> $LOG
  [ "$fixes" -gt 0 ] && echo "$NAME=PASSED" >> $RES.tmp || echo "$NAME=FAILED" >> $RES.tmp
}

rm -f $RES.tmp
scenario tap_handoff tile
scenario handoff_direct direct
echo "-- granting Allow all the time" >> $LOG
adb shell pm grant $PKG android.permission.ACCESS_BACKGROUND_LOCATION >> $LOG 2>&1
adb shell dumpsys package $PKG | grep -i "BACKGROUND_LOCATION" | head -2 >> $LOG
scenario tap_allow_all_time tile
adb shell pm revoke $PKG android.permission.ACCESS_BACKGROUND_LOCATION >> $LOG 2>&1
cat $RES.tmp >> $LOG
grep -q "tap_handoff=PASSED" $RES.tmp && echo PASSED > $RES || echo FAILED > $RES
cat $RES.tmp > $OUT/E2E_${TAG}_detail; rm -f $RES.tmp
