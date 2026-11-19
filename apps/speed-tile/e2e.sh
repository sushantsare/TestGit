#!/bin/bash
# Real-world tile test: no instrumentation (so Android's normal background rules apply),
# real emulator GPS receiver fed with "adb emu geo fix".
OUT=/tmp/out
PKG=com.sushant.speedtile
TILE=$PKG/.SpeedTileService
LOG=$OUT/e2e-results.txt
echo "== Speed Tile real tile test ==" > $LOG
adb install -r -g $OUT/speed-tile.apk >> $LOG 2>&1
adb shell settings put secure location_mode 3
adb shell settings put secure sysui_qs_tiles "custom\($TILE\),wifi,bt,flashlight,dnd,airplane,rotation,location"
adb shell am force-stop $PKG
adb shell input keyevent KEYCODE_HOME
sleep 2
adb emu geo fix 85.1000 25.6000 50 9
adb logcat -c
adb shell cmd statusbar expand-settings
sleep 3
echo "-- tap tile (start)" >> $LOG
adb shell cmd statusbar click-tile $TILE
sleep 4
lon=85.1000
for i in $(seq 1 15); do
  lon=$(python3 -c "print(f'{$lon + 0.00015:.5f}')")   # ~15 m per second = ~54 km/h
  r=$(adb emu geo fix $lon 25.6000 50 9 29 2>&1)
  echo "$r" | grep -q KO && adb emu geo fix $lon 25.6000 50 9 > /dev/null 2>&1
  sleep 1
done
adb shell cmd statusbar expand-settings
sleep 3
adb exec-out screencap -p > $OUT/screens/20_real_tile_tap_live.png
adb shell dumpsys location | grep -i -A2 "speedtile" | head -20 >> $LOG
adb logcat -d -s SpeedTile:* >> $LOG
FIXES=$(adb logcat -d -s SpeedTile:I | grep -c "GPS fix")
echo "FIX_LOG_LINES=$FIXES" >> $LOG
echo "-- tap tile (stop)" >> $LOG
adb shell cmd statusbar click-tile $TILE
sleep 3
adb exec-out screencap -p > $OUT/screens/21_real_tile_tap_off.png
adb shell cmd statusbar collapse
if [ "$FIXES" -gt 0 ]; then echo PASSED > $OUT/E2E; else echo FAILED > $OUT/E2E; fi
cat $OUT/E2E >> $LOG
