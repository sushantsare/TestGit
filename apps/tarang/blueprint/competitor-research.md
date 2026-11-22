# Competitor research — offline music players (Nov 2026)

Sources: Android Authority, Beebom, alphonsolabs, bestappsforandroid "What is new in 2026", F-Droid listings (Gramophone, Void Player, Local Player), Namida releases.

| App | Known for | Praised | Complaints |
|---|---|---|---|
| Poweramp | Best-in-class EQ/DSP, many formats | Sound quality, 10-band EQ | Clunky, steep learning curve, paid, battery use |
| Musicolet | Free, no internet permission, multiple queues, folders, tag editor, lyrics | Fast, simple, private | Plain look |
| Gramophone 1.1 | Material You, offline, word-synced lyrics, ReplayGain, pure dark, folder whitelist, sleep timer | Clean minimal UI | Fewer features |
| Auxio 4.1 | Fast, reliable Material UI, strictly local | UX without clutter | Few extras |
| Namida 6 | Smart home mixes (recommended, underrated, discover), stats | Library depth | Heavy, complex |
| Void Player / Local Player | Compose + Material 3, Media3, album-art dynamic colour, LRC | Modern look | New, small |
| Oto Music / Pulsar | Gestures, lightweight, sleep timer | Clean UI | — |

## Patterns to follow
- Media3 ExoPlayer + MediaSessionService (lock screen, Bluetooth, notification, resumption).
- Material You dynamic colour; album-art colour on Now Playing.
- Docked mini player above bottom nav; swipe-down Now Playing; queue as sheet.
- Folder whitelist, short-audio filter, LRC lyrics, sleep timer with fade, ReplayGain.

## Pitfalls to avoid
- Too many toggles on main screens (Poweramp complaint) — hide depth in menus.
- Ads/upsell popups (common in Play Store players).
- Empty library because permission flow is broken (old Tarang).
- Fake metadata (old Tarang).
