# Saveo 2.0.0 audit — 2026-11-20

**Grade: A** — no Critical or Major failures, and no regressions compared with 1.1.0.

## What was checked
- **Build and tests:** the APK built and is signed. Unit tests, the UI walkthrough (12 tests) and E2E on API 33, 34 and 35 all passed.
- **Stability and resources:**
  - T3 monkey test: 0 crashes, 0 ANRs.
  - T4 StrictMode: 0 violations.
  - T5 LeakCanary: 0 leaks.
- **Speed and memory:**
  - T6 start time (emulator debug build): cold 1169 ms, warm 202 ms.
  - T7 janky frames: 0%.
  - T8 memory: PSS went from 69 MB to 82 MB and then stayed stable.
- **Robustness:** T9 process death, T10 offline, T11 Doze and T14 configuration changes (rotate, dark mode, font 200%, tablet) all passed.
- **APK:** T15 shows a v2 signature on the same key as before, so the app updates in place. It is 16 KB aligned.
- **Lint:** 0 errors. One warning (OldTargetApi) is accepted because the app is not going on Play.
- **Contrast:** every pair passes 4.5:1. See design/DESIGN_SPEC.md.
- **About page:** shows Sushant Kumar · sushant.sare@gmail.com only.
- **Permissions:** unchanged from 1.1.0 (see NOTES.md).

## Changes in 2.0
- The whole UI was rebuilt in the Grabbit identity: colours, Plus Jakarta Sans, 24dp cards and pill buttons.
- A bottom navigation bar (Statuses / Saved / Settings) replaces the tabs. Tablets use a navigation rail.
- Settings has been regrouped. Theme and New saves are now segmented buttons, and there is a new Pure black option.
- The viewer button on statuses says **Save** (was "Save to gallery").
- New icon: option 3, cream with orange cards.
- The onboarding text now matches private-by-default saves.

## Needs a real phone
Check these on a real phone: the real WhatsApp folder, repost, fingerprint unlock, new-status alerts, and how smooth swiping feels.
