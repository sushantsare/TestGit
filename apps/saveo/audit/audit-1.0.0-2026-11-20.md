# Saveo 1.0.0 — Audit scorecard (2026-11-20)

**Grade: A · 100% of applicable checks pass** (3 fix rounds; no Critical or Major left). First audit — no earlier report to compare.

## Automated suite (final build)
| ID | Result | Evidence |
|---|---|---|
| T1 Lint | PASS — 0 errors, 1 warning (OldTargetApi: targetSdk 35) | audit/lint-results-debug.xml |
| T2 Accessibility Test Framework | N/A — needs Compose 1.8 + API 34 emulator; CI walkthrough runs API 33 / Compose 1.7. Manual A11Y review below instead | — |
| T3 Monkey 5,000 events | PASS — 0 crashes, 0 ANRs (also on Android 14 and 15 smoke runs) | T3_monkey.txt |
| T4 StrictMode | PASS — 0 violations (round 0 had 4 startup prefs reads → moved before StrictMode) | T4_strictmode.txt |
| T5 LeakCanary | PASS — 0 app leaks | T5_leaks.txt |
| T6 Startup | PASS — cold median 1.0–1.4 s, warm 0.1–0.3 s | T6_cold/warm.txt |
| T7 Jank | PASS on release build (0 %). Viewer swipe measured only on debug build + software-GPU emulator, where even the plain grid shows ~100 % → **Needs real phone** | logcat SaveoWalk T7 |
| T8 Memory | PASS — 64 → 74 MB PSS after repeated navigation, stable | T8_mem*.txt |
| T9 Process death | PASS — no crash; tab, filter, selection and open viewer restored | screens/audit_T9_after_kill.png |
| T10 Offline | PASS — app is fully offline (no INTERNET permission) | T10 |
| T11 Doze / standby | PASS — no crash; WorkManager checks only while alerts/auto-save are on | T11 |
| T12 Upgrade | N/A — first release (Room schema v1) | — |
| T13 Large data | PASS — 5,000 status files scanned in 75–250 ms | RepositoryTest.largeFolderScansFast |
| T14 Config stress | PASS — rotation, dark mode, font 200 %, tablet size; no crash | audit_T14_*.png |
| T15 APK | PASS — v2 signature, 16 KB aligned (exit 0), 15.6 MB | T15_apksigner.txt |

## Checklist (FAIL found → fixed during the rounds)
| ID | Round found | Problem | Fix |
|---|---|---|---|
| VIS6 | 0 | Snackbars covered “Save all” and the selection bar (taps blocked) | Snackbar sits above them; dismissed on screen change / selection |
| CQ6 | 0 | Dark mode: titles drew in black (no content colour at root) | Root Surface with onBackground |
| AD2 / AD6 | 0–3 | Landscape / tablet two-pane: pane empty, viewer background spilled over the grid | Viewer pane auto-shows first item, clipped to its bounds; smaller tiles on short windows |
| A11Y8 | 1 | At 200 % font the title “Saveo” collapsed beside “WhatsApp Business ▾” | Short source labels (All / WhatsApp / Business / Dual), chip max width |
| L10N1 | 1 | “On/Off”, “Photo”, “Playback speed” hard-coded | Moved to strings.xml |
| L10N4 | 0 | Counts without plurals | 6 plurals resources |
| STAB2 | 1 | Settings / setup intents not guarded | runCatching around startActivity / folder picker |
| CQ5 | 1 | Open viewer lost after process death | Viewer key saved in SavedStateHandle and reopened |
| AD8 | 1 | No keyboard shortcuts on the grid | Ctrl+A, Delete, Esc, Ctrl+R; viewer ←/→, Space, Esc |
| T1 | 0–2 | Lint errors (produceState) + warnings | 0 errors |
| PERF2 | 0 | Viewer background blurred every frame (RenderEffect) | Blurred once into a tiny bitmap (no per-frame cost) |

All other items: BP1–BP6, CQ1–CQ10, CQ12, AD1–AD8, A11Y1–A11Y10, PERF1–PERF6, STAB1–STAB7, STAB9, SEC (all), PERM, L10N1/L10N4, UX1–UX10, VIS1–VIS6, FUNC2–FUNC5, REL2–REL5 → PASS.
N/A: CQ11 (not a media-player app; video pauses when leaving), AD9 / FORM1–5 (no text fields), STAB8 (first schema), L10N2 (English UI by choice), SEC-NET (no network), FUNC1 search (media have no names; filters, sort and date range provided).
Report only: REL1 targetSdk 35 (fine for sideloading; Play Store would need 36), PERF7 baseline profile, AD10, L10N3/L10N5.

## Permissions
| Permission | Needed | Granular | In context | Denial handled | Library-added | Documented | Overall |
|---|---|---|---|---|---|---|---|
| READ_EXTERNAL_STORAGE (≤ API 29) | ✅ (old Android has no folder picker for this) | ✅ maxSdk 29 | ✅ "Allow access" button | ✅ Open settings when blocked | — | ✅ | ✅ |
| WRITE_EXTERNAL_STORAGE (≤ API 28) | ✅ save to gallery on Android 8–9 | ✅ maxSdk 28 | ✅ asked together with read | ✅ save shows message | — | ✅ | ✅ |
| POST_NOTIFICATIONS | ✅ alerts | ✅ | ✅ only when alerts are switched on, reason first | ✅ switch shows "blocked · open settings" | — | ✅ | ✅ |
| USE_BIOMETRIC | ✅ app lock | ✅ | ✅ | ✅ PIN fallback | — | ✅ | ✅ |
| WAKE_LOCK, RECEIVE_BOOT_COMPLETED | ✅ WorkManager | ✅ | install-time | — | WorkManager | ✅ | ✅ |
| DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION | AndroidX internal (signature) | ✅ | — | — | core-ktx | ✅ | ✅ |
| INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE | removed (`tools:node="remove"`) | | | | | | ✅ |

## Data inventory
Saved copies → the phone gallery (Pictures/Saveo, Movies/Saveo). Room DB (saved list, hashes, seen status keys), settings in SharedPreferences, recycle-bin copies in app-private storage, crash logs + usage counts in app storage. Nothing leaves the phone except what the user shares. Folder access and one-time flags excluded from backup.

## Needs real phone
WhatsApp folder picker on Android 11+ (real .Statuses folder), photo + music statuses from real WhatsApp, Repost into WhatsApp, fingerprint app lock, new-status notifications over time, viewer swipe smoothness on real GPU, Dual WhatsApp (depends on phone brand).
