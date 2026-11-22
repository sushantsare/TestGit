# Tarang — build notes

Distribution: personal use

## Stack (Nov 2026)
AGP 9.3.2 (built-in Kotlin) · Kotlin 2.3.0 · KSP 2.3.4 · Gradle 9.9.2 · Compose BOM 2026.08.00 (Material 3)
compileSdk 37 · targetSdk 36 · minSdk 26 · Room 2.8.5 · Media3 1.11.1 (ExoPlayer + MediaSession) · Glance 1.2.0 widget
Navigation Compose · Palette + Material Color Utilities (cover colours) · jaudiotagger 3.0.1 (edit details) · R8 on

## Permissions
| Permission | Why it's needed | When it's asked | If denied |
|---|---|---|---|
| READ_MEDIA_AUDIO (Android 13+) / READ_EXTERNAL_STORAGE (≤12) | Find and play the songs on the phone | Welcome screen, after tapping "Allow access to music" | Welcome screen explains; "Open Settings" when blocked for good |
| WRITE_EXTERNAL_STORAGE (Android 8–9 only) | Delete a song or save edited details on old Android | Only when you delete or edit on Android 8–9 | That action is skipped with a message |
| FOREGROUND_SERVICE + FOREGROUND_SERVICE_MEDIA_PLAYBACK | Keep playing with the screen off | Never (normal permission) | — |
| WAKE_LOCK | Keep the CPU awake during playback | Never (normal permission) | — |
Delete/edit on Android 10+ uses Android's own "Allow" box (no permission). Media notifications need no notification permission. INTERNET and ACCESS_NETWORK_STATE are removed from the merged manifest.

## Honest limits
- Crossfade is fade-out then fade-in (not overlapping songs); it switches gapless off.
- On Android 13+ the app can't read .lrc files by itself: pick the lyrics folder once in Settings → Library → Lyrics folder.

## Signing
CI signs with debug.keystore unless the secrets TARANG_KEYSTORE_B64 (base64 of the .jks), TARANG_KEYSTORE_PASSWORD, TARANG_KEY_ALIAS, TARANG_KEY_PASSWORD exist. No release key is in git.
