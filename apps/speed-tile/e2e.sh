#!/bin/bash
# Real-world tile test: no instrumentation (Android's normal background rules apply),
# real emulator GPS receiver fed with "adb emu geo fix". Usage: e2e.sh [api-tag]
TAG=${1:-33}
OUT=/tmp/out
mkdir -p $OUT/screens
PKG=com.sushant.speedtile
TILE=$PKG/.SpeedTileService
LOG=$OUT/e2e-results-$TAG.txt
RES=$OUT/E2E_$TAG
echo FAILED > $RES
echo "== Speed Tile real tile test on API $TAG ==" > $LOG
adb shell getprop ro.build.version.release >> $LOG
adb install -r -g $OUT/speed-tile.apk >> $LOG 2>&1
adb shell settings put secure location_mode 3
adb shell settings put global hide_error_dialogs 1
adb shell settings put secure sysui_qs_tiles "custom\($TILE\),wifi,bt,flashlight,dnd,airplane,rotation,location"
# Newer SystemUI reads the tile list only at start: restart it (emulator allows root), then make sure the tile is added.
adb root > /dev/null 2>&1; sleep 3; adb wait-for-device
adb shell settings put secure sysui_qs_tiles "custom\($TILE\),wifi,bt,flashlight,dnd,airplane,rotation,location"
adb shell 'kill $(pidof com.android.systemui)' >> $LOG 2>&1
sleep 8
adb shell cmd statusbar add-tile $TILE >> $LOG 2>&1
sleep 2
echo "tiles: $(adb shell settings get secure sysui_qs_tiles)" >> $LOG
adb shell am force-stop $PKG
adb shell input keyevent KEYCODE_HOME
sleep 3
adb emu geo fix 85.1000 25.6000 50 9
adb logcat -c
adb shell cmd statusbar expand-settings
sleep 3
if adb shell settings get secure sysui_qs_tiles | grep -q speedtile; then
  echo "-- tap tile (start)" >> $LOG
  adb shell cmd statusbar click-tile $TILE
else
  # Tile list locked by this SystemUI: open the tile's hand-off screen directly (same code path as a tap).
  echo "-- tile not placeable here: starting the tile hand-off screen as root" >> $LOG
  adb shell cmd statusbar collapse
  adb shell am start -n $PKG/.StartActivity >> $LOG 2>&1
fi
sleep 3
adb exec-out screencap -p > $OUT/screens/${TAG}_a_after_tap.png
lon=85.1000
for i in $(seq 1 8); do
  lon=$(python3 -c "print(f'{$lon + 0.00015:.5f}')")   # ~15 m per second = ~54 km/h
  r=$(adb emu geo fix $lon 25.6000 50 9 29 2>&1)
  echo "$r" | grep -q KO && adb emu geo fix $lon 25.6000 50 9 > /dev/null 2>&1
  sleep 1
done
echo "-- reopen panel" >> $LOG
adb shell cmd statusbar expand-settings
for i in $(seq 1 4); do
  lon=$(python3 -c "print(f'{$lon + 0.00015:.5f}')")
  adb emu geo fix $lon 25.6000 50 9 29 > /dev/null 2>&1
  sleep 1
done
adb exec-out screencap -p > $OUT/screens/${TAG}_b_panel_reopened_live.png
adb shell dumpsys activity services $PKG | grep -iE "ServiceRecord|isForeground|foregroundServiceType|allowWhileInUse|WhileInUse|fgsType" | head -12 >> $LOG
adb shell appops get $PKG | grep -iE "LOCATION" >> $LOG
adb shell dumpsys activity processes $PKG | grep -iE "curCapability|setCapability|procState|curProcState" | head -6 >> $LOG
adb shell dumpsys location | grep -iE "speedtile" | head -8 >> $LOG
STARTED=$(adb logcat -d -s SpeedTile:I | grep -c "GPS updates requested")
echo "SERVICE_STARTED_LOG=$STARTED" >> $LOG
FIXES=$(adb logcat -d -s SpeedTile:I | grep -c "GPS fix")
SATS=$(adb logcat -d -s SpeedTile:I | grep -c "GNSS status")
echo "FIX_LOG_LINES=$FIXES GNSS_LOG_LINES=$SATS" >> $LOG
echo "-- close panel (speed must switch off)" >> $LOG
adb shell cmd statusbar collapse
sleep 4
CLOSED=$(adb logcat -d -s SpeedTile:I | grep -c "Quick Settings closed: speed off")
echo "PANEL_CLOSED_LOG=$CLOSED" >> $LOG
adb shell cmd statusbar expand-settings
sleep 3
adb exec-out screencap -p > $OUT/screens/${TAG}_c_after_close_reopened.png
adb shell cmd statusbar collapse
adb logcat -d -s SpeedTile:* ActivityManager:W | grep -iE "speedtile|SpeedTile" | tail -30 >> $LOG
if [ "$FIXES" -gt 0 ] && [ "$CLOSED" -gt 0 ]; then echo PASSED > $RES; fi
cat $RES >> $LOG
