# Tarang 1.0.0 — audit (round 1 of 3, 22 Nov 2026)

Evidence: CI run on build/tarang-v1 @ 7547f73 (walkthrough on API 35, e2e on API 33/34/35, audit run on API 35).

| Area | Result | Evidence |
|---|---|---|
| Builds | Release (R8) + debug build, lint clean | build.log |
| Unit tests | PASSED (lyrics, artists, formatting, EQ filter maths, streak, stats) | LogicTest |
| Every screen walked (light, dark, pure black) | PASSED, 0 problems, 60 screenshots | WalkthroughTest |
| Real playback (open file, background, pause/play/next by media keys) | PASSED on Android 13, 14, 15 | e2e-results-*.txt |
| Fresh start shows "Continue listening", no mini player | PASSED | e2e_*_continue_listening.png |
| Monkey 1,500 random actions | 0 crashes on 13, 14, 15 | monkey_*.txt |
| Permission denied / offline / battery saver / big font / tablet | 0 crashes | T5, T10, T11, T14 |
| No internet permission | 0 | T15 |
| Startup (emulator) | cold 1.35 s, warm 0.33 s | T6 |
| Scroll smoothness | Needs real phone (CI emulator renders in software) | T7 |

## Fixed during the audit
- Player could freeze half-open when closing (animation tied to a part that disappeared) — fixed.
- Lyrics tab said "Looking for lyrics…" forever; lyrics inside the file now read via a safe copy — fixed.
- "Playing from" header overlapped the title with Up next open — fixed.
- Inner pages' top bar sat under the status bar — fixed.
- Home tab reopened the last inner page instead of Home — fixed.
- Dark mode: some text invisible; status-bar icons didn't follow the app theme — fixed.
- Sound moved into a content-sized bottom sheet; panel tabs slide; panel content rises in (his feedback).

## Needs a real phone
Scroll smoothness; Bluetooth/headphone auto-resume; lock-screen cover; widget on home screen; edit details "Allow" prompt; delete "Allow" prompt; .lrc lyrics via the lyrics-folder picker.
