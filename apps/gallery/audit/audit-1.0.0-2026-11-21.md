# Gallery 1.0.0 — Audit scorecard (21 Nov 2026)

Pipeline audit, 2 rounds. Evidence: CI run for commit e482240 (release APK, unit + UI tests, e2e on API 33/34/35), walkthrough screenshots, lint report, APK checks.

**Grade: B · 90 %** (111 PASS / 12 FAIL; 0 Critical). Round 1 fixed: target/compile SDK 36 (REL1), 6 lint errors (T1), dark-mode text colour (CQ6, found on screenshots), StrictMode enabled (T4), pager crash after a delete (STAB1), MediaStore row delete that could erase moved photos (STAB5, 🔴 data loss — fixed before delivery), viewer stuck on a black screen after its last photo was deleted (UX1).

## Automated suite
| ID | Result | Evidence |
|---|---|---|
| T1 Lint | PASS (0 errors) | 21 GradleDependency + 6 minor warnings (PluralsCandidate ×2, ObsoleteSdkInt, ModifierParameter, ApplySharedPref) |
| T2 ATF | N/A | needs Compose 1.8 accessibility test artifact; current BOM is 1.7 |
| T3 Monkey | PASS | 0 crashes, 0 ANRs (API 33/34/35, 5,000 events each) |
| T4 StrictMode | FAIL 🟢 | 61 short disk reads on main thread at start-up (SharedPreferences first load in Prefs/Diagnostics, ≤30 ms each) |
| T5 Leaks | PASS | LeakCanary: 0 |
| T6 Startup | PASS (warn) | cold median 1,736 ms, warm 493 ms on emulator (target 1,500 ms; fail >5,000) |
| T7 Jank | Needs real phone | 95 % janky on the software-rendered emulator (no GPU); not representative |
| T8 Memory | PASS | PSS 65.8 MB → 55.6 MB after navigation (stable) |
| T9 Process death | PASS | 0 crashes after kill + relaunch |
| T10 Offline | PASS | app is offline-only; 0 crashes |
| T11 Doze | PASS | 0 crashes; bin clean-up via WorkManager |
| T12 Upgrade | N/A | first version |
| T13 Large data | PASS (partial) | 25-item library on emulator; paging-free grid of all items — 30k library needs real phone |
| T14 Config stress | PASS | rotate/night/font/tablet: 0 crashes |
| T15 APK | PASS | v2 signature; 16 KB aligned (zipalign -P 16 OK) |
| T16 Roborazzi | FAIL 🟢 | not set up yet (walkthrough screenshots compared by eye) |
| T17 Dependency scan | N/A | OWASP step not in this repo's workflow |
| T18 Baseline profile | PASS (partial) | `assets/dexopt/baseline.prof` present (library profiles); no app-specific benchmark module |
| T19 Release smoke | PASS | minified release APK, monkey + e2e clean |

## Checklist (FAILs and notable items; everything else PASS)
- BP1–BP6 PASS: all v1 features present (photos/albums, viewer, filmstrip B layout, bin 30 days, favourites, multi-select with drag, move/copy/new album, search & sort, details, share without location, rotate, editor, hidden album with PIN + fingerprint, open from other apps, diagnostics, about).
- BP2 FAIL 🟢 · Album cards show the count as a bare number ("15") where the mockup used the same; OK — but Albums header sort chip says "Name" (mockup) ✓. Minor difference: viewer hint chip text adds "· tap to try".
- CQ6 PASS after fix (dark labels were black on dark — fixed via default content colour).
- CQ9 N/A (no notifications). CQ11 N/A.
- AD1–AD6 PASS (rail ≥ 600 dp, list-detail albums ≥ 840 dp, landscape viewer). AD8 PASS (←/→, Delete/↑ to bin, Ctrl+Z undo, Enter/Esc in filmstrip).
- AD7 FAIL 🟢 · Photos grid on very wide windows has no max width (cells just get more columns) — acceptable for a gallery.
- A11Y1 PASS (contrast table in design spec, all ≥ 4.5:1). A11Y3/4/5/6 PASS (labels, merged cells, headings, switch roles). Filmstrip cards have TalkBack actions "Open" and "Delete".
- A11Y8 Needs real phone check at 200 % for the editor panel (emulator font-scale screenshot looked fine).
- PERF1 PASS (splash API). PERF2 FAIL 🟢 · grid decodes thumbnails per cell; fine on emulator but a 30k library needs a real-phone check. PERF6 PASS (R8 + shrink, 3.9 MB). PERF7 partial.
- STAB1 PASS. STAB5 PASS after fix. STAB8 N/A (schema v1).
- SEC-STORAGE1 PASS (vault AES-256-GCM, Keystore-wrapped key, PBKDF2 PIN). SEC-STORAGE3 PASS (vault, db and secure prefs excluded from backup). SEC-PLAT3 PASS (FileProvider not exported). SEC-PLAT6 PASS (FLAG_SECURE on Hidden album in release). SEC-CODE4 PASS (release key not in repo; APK re-signed outside CI). SEC-PRIV2 PASS (no clipboard; ClipData only grants share permission).
- SEC-NET1 N/A (no INTERNET permission).
- PERM: see table — all ✅.
- L10N1 PASS (all text in strings.xml, plurals). L10N2 N/A (English only by choice).
- UX1–UX10 PASS; UX FAIL 🟢 · Undo snackbar stays ~10 s and follows you to the next screen.
- REL1 PASS (targetSdk 36). REL3 PASS (permanent key, v2). REL5 PASS (name + email only).
- PLAY N/A (personal).

## Permission table
| Permission | Needed | Granular | Asked in context | Denial handled | Library-added | Documented | Overall |
|---|---|---|---|---|---|---|---|
| MANAGE_EXTERNAL_STORAGE | ✅ core (popup-free flick delete, chosen by Sushant) | ✅ (only route to popup-free deletes) | ✅ welcome screen with reason | ✅ denied screen + Open settings, re-checked on resume | — | ✅ | ✅ |
| READ/WRITE_EXTERNAL_STORAGE (≤ API 29) | ✅ | ✅ maxSdk 29 | ✅ | ✅ | — | ✅ | ✅ |
| USE_BIOMETRIC / USE_FINGERPRINT | ✅ | ✅ | system sheet only when opening Hidden | ✅ PIN fallback | fingerprint by library | ✅ | ✅ |
| SET_WALLPAPER | ✅ "Set as" | ✅ | install-time | — | — | ✅ | ✅ |
| WAKE_LOCK, RECEIVE_BOOT_COMPLETED | ✅ daily bin clean-up | ✅ | install-time | — | WorkManager | ✅ | ✅ |

## Data inventory
Photos/videos stay where they are; bin copies in `/storage/emulated/0/.gallery_trash` (30 days); Room DB (bin index, favourites, hidden index) in app storage; hidden files encrypted in app storage; settings + usage counters + crash log in app storage. Nothing leaves the phone (no INTERNET permission).

## Needs real phone
Fingerprint unlock, scrolling smoothness with a large library, Set as wallpaper picker, sharing to WhatsApp etc., opening photos from other apps.
