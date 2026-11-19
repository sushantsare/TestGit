# Speed Tile (com.sushant.speedtile)

Quick Settings tile that shows live GPS speed in km/h. Tap tile = start/stop GPS.
Tile label "64 km/h", subtitle "GPS · ±4 m", icon = speed number drawn as a bitmap.
No launcher icon (no MAIN/LAUNCHER activity, so Android 10+ adds no synthetic icon either).
Long-press the tile opens the screen: speedometer, Start/Stop, trip (top speed, average, distance, moving time, reset),
settings (theme, scale, decimal, auto-stop, keep screen on), About.
Setup for the user: Quick Settings → pencil (Edit) → drag Speed in.

How starting from the tile works: Android only allows a location foreground service to start
while the app is visible, so the tile opens an invisible StartActivity (startActivityAndCollapse),
which starts SpeedService and closes in 0.3 s.

## Permissions
| Permission | Why | When asked | If denied |
|---|---|---|---|
| ACCESS_FINE_LOCATION (+COARSE, required pair on 12+) | GPS speed | First tap on the tile (system prompt from the invisible StartActivity) | Card with Allow / Open Settings; approximate-only shows "Precise location needed" |
| FOREGROUND_SERVICE + FOREGROUND_SERVICE_LOCATION | Keep measuring while the shade/other apps are open | Not a prompt | — |

No INTERNET, no background location, location never stored.
