# Tarang — app spec

Distribution: personal

## 1. Vision brief
- **Name:** Tarang (तरंग, "wave"). Package `com.sushant.tarang`.
- **Pitch:** A fully offline music player for the songs on your phone that looks and feels like Google made it.
- **User:** Sushant (personal use), phone and tablet equally.
- **Success:** opens instantly and resumes the last song; looks beautiful while playing; never stops or crashes in the background; any song in 2 taps.

## 2. Features (v1)
**Signature**
- Mini player is its own progress bar: tinted fill (cover colour) grows left→right behind cover, title and buttons; tap → container-transform into Now Playing; fill continues into the full player's wavy seek bar with no jump.
- Material 3 Expressive look; wallpaper dynamic colour for the app; cover-derived colour for Now Playing + mini-player fill.
- Wavy seek bar (amplitude animates when playing, flattens when paused).

**Playback** (Media3 ExoPlayer + MediaSessionService)
- Resume exactly where left off (song, position, queue, shuffle/repeat) after app close / reboot (playback resumption).
- Shuffle (proper shuffled order, Previous walks back), repeat off/all/one.
- Gapless playback; crossfade 0–12 s (Settings).
- Playback speed 0.5–2.0×.
- Equalizer: presets + 10-band + bass boost + virtualizer (custom Media3 AudioProcessor; system EQ often only 5 bands).
- Sleep timer: 15/30/45/60 min, custom, stop after current song, fade out at the end.
- Audio focus: pause on call, resume after; duck for short sounds; pause on headphones unplug.
- Settings toggle: resume when headphones/Bluetooth connect.
- 3 saved queues, switch on Queue screen; each keeps own song + position.
- Queue rules: tapping a song slips it in after the current song and plays it, existing list continues; if queue empty, tapping a song plays the rest of that folder/list from it; "Play all", "Shuffle all" on folders/lists; long-press → "Play from here" (replaces queue). Queue sheet: drag to reorder, swipe to remove.
- "End the queue" in the full player's ⋮ menu: stop, clear queue, hide players.

**Library**
- Main browsing by folder (his main music folder); also Albums, Artists, Playlists, Liked.
- Choose which folders to scan; skip audio under 30 s (adjustable).
- Default sort A–Z; sort options: name, date added, file name, most played, duration.
- Search across songs, artists, albums, folders, playlists.
- Playlists: create, rename, delete, add, remove, drag reorder; Liked songs.
- Edit song details (title, artist, album, cover) written into the file (MediaStore write request).
- Delete song file with confirm (MediaStore.createDeleteRequest on Android 11+).
- Song info sheet: real format, bitrate, sample rate, channels, size, path, date added.
- Rescan removes songs that no longer exist.

**Lyrics**
- Sources: .lrc next to the song; embedded lyrics (plain + synced); typed/pasted in app; import a .lrc file and attach it.
- Synced lyrics scroll line by line; tap a line to seek. Plain lyrics scroll normally. No fake lyrics — empty state with "Add lyrics".

**Home & stats**
- Home content decided with pictures (continue listening, smart mixes, recently played, stats teaser candidates).
- Smart mixes from real listening: Most played, Forgotten favourites, Recently added, On repeat.
- Wrapped-style stats: total minutes, top songs/artists/folders, streaks, hour-of-day, weekly and monthly charts. A play counts after 30 s.

**Android extras**
- Media notification + lock screen; 2 extra buttons user-selectable in Settings (Like / Shuffle / Repeat).
- Home-screen widgets: small, wide, big (Glance), resizable.
- Pure black (AMOLED) mode; Light / Dark / System.
- No INTERNET permission.

**Later / out**
- Later: backup & restore, Quick Settings tile.
- Out: streaming, Android Auto, online lyrics, accounts, analytics leaving the phone.

## 3. Screen map
Onboarding/permission → Main shell (bottom nav / nav rail): Home · Folders · Library · Search
- Folder detail; Album detail; Artist detail; Playlist detail; Liked
- Now Playing (overlay) → Queue sheet (3 queues) · Lyrics · Sleep timer sheet · Equalizer · Song info · Edit details · ⋮ menu (End the queue, add to playlist, speed, share, delete)
- Stats (Wrapped)
- Settings → Appearance · Playback · Library (folders, short-audio) · Notification · Diagnostics · About
(Final tab set confirmed in the layout step.)

## 4. Screen specs
To be filled per screen after layout picks; every screen gets empty, loading, error and permission-denied states.

## 5. Design system
Material 3 Expressive (Google style); detailed tokens in `design/DESIGN_SPEC.md` (android-ui-designer). Dynamic colour from wallpaper (fallback seed for Android < 12); Now Playing scheme from cover (Material Color Utilities). Font: closest open Google-style font. Light, dark, pure black.

## 6. Adaptive layout
- Compact: bottom nav, mini player docked above it, full-screen Now Playing.
- Medium: navigation rail; Now Playing two columns (cover | controls+lyrics).
- Expanded: rail; list-detail (folders → songs); Now Playing side panel or full screen with queue/lyrics pane; max content width.
- Rotation without losing state; split screen; keyboard (space = play/pause, arrows = seek) and mouse hover.

## 7. Quality standards
TalkBack labels; contrast ≥ 4.5:1; 48 dp targets; 200 % font; strings in resources (English). No network; nothing leaves phone. Cold start < 1.5 s; smooth with 5,000+ songs (paging, indexed Room). Room migrations — never destructive. Every screen tested.

## 8. Analytics & crashes
Local crash log + local usage counts; Diagnostics screen in Settings to view/share log. No Firebase.

## 9. Technical plan
Kotlin, Jetpack Compose (latest BOM), Material 3 Expressive, Navigation Compose, MVVM, Hilt (>10 screens), Room, DataStore, Media3 (ExoPlayer, Session), Glance widgets, Coil 3, Palette/Material Color Utilities, jaudiotagger-style tag writing. minSdk 26, target/compile 36. Permissions: READ_MEDIA_AUDIO (≤32: READ_EXTERNAL_STORAGE), POST_NOTIFICATIONS, FOREGROUND_SERVICE(_MEDIA_PLAYBACK), WAKE_LOCK. Permission flow: welcome → request → rationale "Allow access" → after 2nd denial "Open Settings" → recheck on resume. Security: MASVS L1. R8 on for release.

## 10. Fixed rules
Full-fledged app. About: Sushant Kumar · sushant.sare@gmail.com. No clipboard features.
