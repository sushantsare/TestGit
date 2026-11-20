# InkNote 1.0.0 — Audit scorecard (20 Nov 2026)

**Grade: B · 93% (126 PASS / 9 FAIL / 22 N/A)** · first audit, 3 rounds (round 1 → 15 fixes, round 2 → 1 regression fixed, round 3 → clean)
Evidence: GitHub Actions run of commit "InkNote tests: assert the text tool opens a text box" (emulator API 33 walkthrough, real-world e2e on API 33/34/35).

## Automated suite
| ID | Result | Evidence |
|---|---|---|
| T1 Lint | PASS — 0 errors, 1 warning (OldTargetApi) | audit/lint-results-debug.txt |
| T2 ATF | N/A — `ui-test-junit4-accessibility` needs Compose 1.8; build stack pinned to BOM 2024.12 | — |
| T3 Monkey 5,000 | PASS — 0 crashes, 0 ANRs (also 1,500 events on API 34 and 35: 0) | T3_monkey.txt |
| T4 StrictMode | PASS — 0 (round 2 had 11 from recording setup → fixed) | T4_strictmode.txt |
| T5 LeakCanary | PASS — 0 application leaks | T3_logcat_debug.txt |
| T6 Startup | PASS — cold median 1,205 ms, warm 422 ms | T6_cold.txt |
| T7 Jank | PASS — 0.00 % janky frames | T7_gfxinfo.txt |
| T8 Memory | PASS (watch) — 83 → 90 MB PSS after repeated navigation (image/PDF caches, capped LRU) | T8_mem*.txt |
| T9 Process death | PASS — no crash, app restores | audit_T9_after_kill.png |
| T10 Offline | PASS — 0 crashes; handwriting pack shows plain "connect once" message | summary |
| T11 Doze/standby | PASS — 0 crashes | summary |
| T12 Upgrade | N/A — first release (Room v1; migrations array ready) | — |
| T13 Large data | PASS — 2,000 notes: list and search < 1 s (RepositoryTest.largeDataStaysFast) | ui-tests |
| T14 Config stress | PASS — rotate ×6, night mode, font 200 %, tablet size: 0 crashes | audit_T14_*.png |
| T15 APK | PASS — v2 signature valid, 16 KB zipalign OK; arm64 APK 39.3 MB (was 99 MB before per-CPU split) | T15_*.txt |

## Fixed during the audit
STAB5 save failure no longer crashes (keeps edit, plain message) · SEC-STORAGE1 PDFs and audio of locked notes now encrypted too · AD5 navigation rail on medium widths · AD8 keyboard shortcuts Ctrl+N/F/Z/Y · CQ4/CQ11 audio focus, playback pauses when leaving · VIS5 duplicate Settings removed from ⋮ menu · A11Y2 colour dots 48 dp · A11Y5 section headings · A11Y6 paper options announce selection · L10N1 ~45 hard-coded strings moved to resources, plurals · SEC-PLAT6 lock screen ignores obscured touches · PERF3/T4 recording + playback disk work off main thread · PERF6 per-CPU APKs · T1 lint errors 2 → 0 · T13 large-data test added.

## Remaining FAILs (with reason)
| ID | Sev | What | Why not fixed |
|---|---|---|---|
| REL1 | 🟠 | targetSdk 35; Google Play now asks for 36 | needs a build-stack upgrade (AGP/compileSdk 36) — next version |
| BP2 | 🟢 | Selection actions sit in a bar above the tools, mockup shows them floating next to the selection | layout choice; works the same |
| BP1 | 🟢 | Search has no "Recordings" filter (would need speech-to-text) | new feature → suggestion |
| PERF1 | 🟢 | Note pictures on Home take a moment to appear the first time (rendered on demand) | polish |
| UX1 | 🟢 | No visible "saved" indicator (autosave is silent) | polish |
| AD10 | 🟢 | No drag-and-drop between notes/apps | tier-1 polish |
| A11Y11 | 🟢 | T2 accessibility scanner not run (stack) | stack |
| STAB8 | 🟢 | No Room migration test yet (only one schema version) | will be added with the first schema change |
| FUNC5 | 🟢 | No reminders / widget | he chose "later" |

## Needs a real phone
Fingerprint unlock · real stylus pressure, palm rejection and "stylus-only" auto-switch · microphone recording quality and screen-off recording · audio playback sound (emulator runs with no audio) · camera photo and document scanner (needs Google Play services) · handwriting pack download + handwriting→text · share-in from WhatsApp/Gallery/Files · saving exports to Google Drive.

## Permissions
| Permission | Needed | Granular | Asked in context | Denial handled | Library | Documented | Overall |
|---|---|---|---|---|---|---|---|
| RECORD_AUDIO | ✅ | ✅ | ✅ (+ Voice recording, reason first) | ✅ Open settings | — | ✅ | ✅ |
| POST_NOTIFICATIONS | ✅ | ✅ | ✅ first recording only | ✅ recording still works | — | ✅ | ✅ |
| FOREGROUND_SERVICE(_MICROPHONE) | ✅ | ✅ | install-time | — | — | ✅ | ✅ |
| USE_BIOMETRIC | ✅ | ✅ | install-time | ✅ PIN | — | ✅ | ✅ |
| INTERNET | ✅ one-time ML Kit pack | ✅ | on Download tap | ✅ | — | ✅ | ✅ |
| ACCESS_NETWORK_STATE, WAKE_LOCK, RECEIVE_BOOT_COMPLETED | ✅ WorkManager | ✅ | install-time | — | WorkManager | ✅ | ✅ |
No storage, camera, location, contacts permissions (photo picker, camera app, file picker, share sheet used instead).

## Data inventory
Notes, pages, ink, typed text, images, PDFs, recordings, folders, tags: app-private storage only. Locked notes: page content, images, PDFs and audio encrypted (AES-GCM, Android Keystore). PIN: PBKDF2 hash + salt, excluded from Android backup (allowBackup=false). Nothing leaves the phone except files the user shares/saves and the one-time ML Kit model download. No analytics, no crash reporting (his choice). No clipboard features.
