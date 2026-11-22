# Bahi Khata — Audit round 2 (independent re-audit)

App: `com.sushant.bahikhata` · versionName 1.0.0 / versionCode 1 · Distribution: personal (PLAY = N/A) · MASVS L2
Source audited: HEAD `15126c4` (repo) · CI evidence: `/home/claude/result_r2` built from `6c94380` (SOURCE_SHA) — the last commit `15126c4` (KeyBus typing + StateRestoreTest) is **not covered by this CI run**.
Previous: `apps/bahi-khata/audit/audit-1.0.0-2026-11-21-round1.md` (grade D, 52 %, 4 Critical).
Auditor: independent agent (repo + CI outputs only) · Date: 2026-11-21

## 0. Result

**Grade C · overall 77 % (86 PASS / 26 FAIL of 112 scored) · 0 Critical · 0 regressions** (round 1: D, 52 %, 59/113, 4 Critical → +25 points)

Grade is C, not B, only because the score is under 85 %. No Critical items remain.

| Section | Round 1 | Round 2 | Change |
|---|---|---|---|
| BP Blueprint | 2/6 (33 %) | 2/6 (33 %) | = (BP1 🔴→🟢) |
| CQ Core quality | 8/11 (73 %) | 10/11 (91 %) | +2 |
| AD Adaptive | 4/9 (44 %) | 7/9 (78 %) | +3 |
| A11Y | 2/10 (20 %) | 7/10 (70 %) | +5 |
| PERF | 3/7 (43 %) | 4/6 (67 %) | +1 (PERF2 now "Needs real phone") |
| STAB | 3/8 (38 %) | 6/8 (75 %) | +3 |
| SEC | 12/19 (63 %) | 18/19 (95 %) | +6 |
| PERM (rows) | 6/8 (75 %) | 8/8 (100 %) | +2 |
| L10N | 0/3 (0 %) | 0/3 (0 %) | = |
| UX | 6/10 (60 %) | 8/10 (80 %) | +2 |
| FORM | 1/5 (20 %) | 3/5 (60 %) | +2 |
| VIS | 4/6 (67 %) | 5/6 (83 %) | +1 |
| FUNC | 3/5 (60 %) | 3/5 (60 %) | = |
| REL | 5/6 (83 %) | 5/6 (83 %) | = |
| PLAY | N/A | N/A | — |

**Regression check (section 6):** every round-1 PASS item was re-checked; none went PASS → FAIL.
**Caution (not scored):** commit `8a3c71d` changed how the SQLCipher key is handed over (raw `x'…'` key instead of a passphrase, Db.kt:331-333). A database made by the round-1 build will not open with the new build (the app would show the "Can't open your khata" screen). This is fine for a first release that was never installed with real data, but **any test install from round 1 must be uninstalled first**, and from now on the key format must never change without a migration (`PRAGMA rekey`).

---

## 1. Tool results (T1–T19)

| ID | Round 2 result | Evidence | Verdict | Change |
|---|---|---|---|---|
| T1 Code checks | Lint **0 errors**, 43 warnings: UseKtx ×32, ConfigurationScreenWidthHeight ×4 (AddEntry.kt:216, AppRoot.kt:98, Entries.kt:125, Home.kt:68), ModifierParameter ×4, NewerVersionAvailable ×2 (coroutines 1.10.2→1.11.0, serialization 1.9.0→1.13.0), ObsoleteSdkInt ×1. detekt/ktlint still not run; `lintDebug` not `lintRelease`; `abortOnError=false` | audit/lint-results-debug.txt | FAIL 🟢 (detekt/ktlint missing) | BOM error fixed |
| T2 ATF | `AccessibilityTest` exists and uses `enableAccessibilityChecks()`, but it was **skipped**: `AssumptionViolatedException: ATF in Compose needs API 34+`; the instrumented run was on API 33 | ui-tests.log:20-22 | FAIL 🟠 (never executed) | set up, not run |
| T3 Monkey | 5000 events, 0 crashes, 0 ANRs; release monkey ×3 APIs = 0 | audit/summary.txt, e2e-results-3x | PASS | = |
| T4 StrictMode | Now really on (App.kt:35-38). **11 violations**, all at start-up on the main thread: `Prefs.<init>` (Prefs.kt:37-39 via App.kt:40), `System.loadLibrary("sqlcipher")` (Db.kt:328 via App.kt:42), `Diagnostics.time` (Diagnostics.kt:38-39 via MainActivity.onCreate). None during the walkthrough | audit/T4_strictmode.txt | FAIL 🟢 | now measured |
| T5 Leaks | 0 app leaks | summary.txt | PASS | = |
| T6 Startup | cold median **874 ms**, warm 114 ms | T6_cold/warm.txt | PASS (meets 1500 ms target) | 2068 → 874 ms |
| T7 Jank | 123 frames, 80.5 % janky, gpu p50 4950 ms — software-rendered CI emulator | T7_gfxinfo.txt | Needs real phone | measured now |
| T8 Memory | PSS 51,494 → 55,382 KB | T8_mem1/2 | PASS | = |
| T9 Process death | Add screen comes back after kill (audit_T9_after_kill.png), but `amount_kept=0`: the key-event typing didn't type on the emulator, so typed-data survival is unproven. `StateRestoreTest` (recreate with a typed amount) is in `15126c4`, which this CI run did not build | summary.txt; screens | FAIL 🟢 (evidence) | screen restored; data not proven |
| T10 Offline | 0 crashes | summary.txt | PASS | = |
| T11 Battery modes | 0 crashes | summary.txt | PASS | = |
| T12 Upgrade | first release | — | N/A | = (see key-format caution) |
| T13 Large data | 5,000 rows, Entries cold open 1336 ms, 0 crashes/ANRs, search screenshot shows results | summary.txt, audit_T13_search.png | PASS (search latency not timed) | new |
| T14 Config stress | 0 crashes; font200, night, landscape, tablet screenshots all captured (not 0 B any more) | screens/audit_T14_* | PASS (layout findings under AD2/A11Y8) | evidence fixed |
| T15 APK | v2 valid; **11,166,216 B** (was 25.0 MB); 8 .so; zipalign -P 16 exit 0 | T15_*.txt | PASS | smaller |
| T16 Roborazzi | not set up (personal app) | — | N/A | = |
| T17 Dependency scan | not set up | — | FAIL 🟢 | = |
| T18 Macrobenchmark / baseline profile | not set up; only library profile | — | FAIL 🟢 | = |
| T19 Release smoke | release APK monkey ×3 APIs 0 crashes; SMS e2e PASSED on 33/34/35 with `handled_once_total=5 (expected 5)` | e2e-results-33/34/35.txt | PASS | stronger e2e |

Other runs: unit tests 19/19 (LogicTest 12, SmsParserTest 6, BankCorpusTest 1 = 45 banks / 675 SMS); instrumented 15 tests OK (ATF skipped), walkthrough 68 screens `problems=0`. Note: `e2e_33_launch.png` and `e2e_34_launch.png` are still 0-byte (e2e only; walkthrough is complete).

---

## 2. Checklist

Severity: 🔴 Critical · 🟠 Major · 🟢 Minor. DS = design-safe fix, DC = design change. "Δ" = change since round 1.

### BP — Blueprint conformance (2/6)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| BP1 | FAIL 🟢 | Fixed: split edits (Repo.kt:98-104 always replaces shares; equal shares re-computed from saved amount AddEntry.kt:146; test `editingToNoSplitRemovesShares`), split by amounts (AddEntry.kt:505-551), category order (Repo.moveCategory + Up/Down, Settings.kt:209-213), custom report period (36/37), min due (25), reminder days (Settings.kt:520-533), budget start day (47), SMS swipe OK/Ignore (Sms.kt:128-129, 303), battery guide (40), shortcuts, 45-bank corpus. **Still missing:** add-entry as a side sheet on expanded width (spec). Small gap: in "By amounts" mode, lowering the amount on the edit screen can leave shares bigger than the amount (no check at save, AddEntry.kt:146) | DS: side sheet for `add` at ≥840 dp; at save, block or re-ask when `sum(shares) > amount` | 🔴→🟢 |
| BP2 | FAIL 🟢 | Round-1 mockup differences remain: account tiles show type labels + "Direct / card" (AddEntry.kt:411, 420; 10_add_details), separate Cash / Wallets sections (Accounts.kt:71) vs mockup 09 "Cash & wallets", etc. | DS: align listed screens with mockups | = |
| BP3 | PASS | Routes in AppRoot.kt:168-201 match screen map | — | = |
| BP4 | FAIL 🟢 | Detail screens now show spinner then "isn't here" (Common.kt:209-219 `MissingRecord`). List screens still have no loading skeleton or error/retry state required by spec §4 (no skeleton/Retry anywhere, grep) | DS: skeleton rows while `loaded=false`; "Couldn't load · Retry" | improved |
| BP5 | FAIL 🟢 | `LocalSpacing` tokens still unused (only Theme.kt); 152 off-scale `6/10/14/18/22/26/30.dp` uses | DS: use Spacing tokens | = |
| BP6 | PASS | Local Diagnostics only (53_diagnostics) | — | = |

### CQ — Core app quality (10/11)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| CQ1 | PASS | M3 components | — | = |
| CQ2 | PASS | AddEntry BackHandlers + discard prompt (AddEntry.kt:125-134; 15_discard_prompt.png); Setup `BackHandler(step>1)` (Setup.kt:50); `rememberDiscardGuard` on AccountEdit/Transfer (Accounts.kt:278, 362). Gap in edit-mode dirty check is scored under FORM3 | — | FAIL→PASS |
| CQ3 | PASS | Launch route not replayed after restore (MainActivity.kt:122) | — | = |
| CQ4 | PASS | Lock after timeout (MainActivity.kt:104) | — | = |
| CQ5 | FAIL 🟢 | Most dialogs now `rememberSaveable`/`recordSaver`. Still `remember`: category edit dialog name/icon/colour (Settings.kt:193-195 `remember(c)`), recurring amount (Settings.kt:249) → typed text lost on rotation/process death (the dialog itself reopens) | DS: `rememberSaveable(c.id)` for those fields | 🟠→🟢 |
| CQ6 | PASS | Light + dark complete (55-64) | — | = |
| CQ7 | PASS | Bar icons follow app theme (MainActivity.kt:133-141; 56_dark_add shows white icons); `navigationBarsPadding()` + `imePadding()` at root (AppRoot.kt:157) — Next/Save sit above the gesture bar | — | FAIL→PASS |
| CQ8 | PASS | Vector icons | — | = |
| CQ9 | PASS | Channels; private notifications with public version (Work.kt:61-62) | — | = |
| CQ10 | PASS | WorkManager; T11 0 crashes | — | = |
| CQ11 | N/A | Not media | — | = |
| CQ12 | PASS | `createChooser` (Common.kt:170-174) | — | = |

### AD — Adaptive (7/9)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| AD1 | PASS | No locks | — | = |
| AD2 | FAIL 🟠 | Add step 1 in landscape fixed (keypad beside amount, audit_T14_landscape_add.png). **New finding:** phone landscape Home — the NavigationRail (FAB + 5 items) doesn't fit 411 dp height: "Udhar" and "Reports" overlap and "Reports" is cut off at the bottom (audit_T14_landscape_home.png). The rail isn't scrollable (AppRoot.kt:127-137) | DS: in short windows (height < 480 dp) use the bottom bar instead of the rail, or put the FAB in the screen and use `NavigationRail` with `verticalArrangement = Center` + `verticalScroll` | 🟠 (new root cause) |
| AD3 | FAIL 🟢 | Same root as CQ5 (category/recurring dialog text) | DS: as CQ5 | 🟠→🟢 |
| AD4 | PASS | `ImeAction.Next` + `keyboardActions` in TextIn/MoneyField (Common.kt:136-149); Enter = Next on amount step (AddEntry.kt:253-256) | — | FAIL→PASS |
| AD5 | PASS | Rail ≥600 dp, bar below | — | = |
| AD6 | PASS | Two panes ≥840 dp | — | = |
| AD7 | PASS | Non-tab screens centred at ≤840 dp (AppRoot.kt:158); forms 640/720 dp columns; tablet screenshots OK | — | FAIL→PASS |
| AD8 | PASS | Ctrl+N, Ctrl+F, Esc (MainActivity.kt:153-165) | — | FAIL→PASS |
| AD9 | PASS | Handwriting not disabled | — | = |
| AD10 | N/A | Report only | — | = |

### A11Y (7/10)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| A11Y1 | PASS | Text contrast as round 1; budget bars now outlined (65_home_hidden_amounts); dark status-bar icons fixed | — | FAIL→PASS |
| A11Y2 | PASS | Swatches/icon tiles 48 dp (Accounts.kt:306, Settings.kt:202-207), split rows `heightIn(min=48.dp)` | — | FAIL→PASS |
| A11Y3 | PASS | "Card colour Black…", "Colour n", "Icon …", keypad "Delete digit" (AddEntry.kt:305) | — | FAIL→PASS |
| A11Y4 | FAIL 🟢 | SettingSwitch and split checkboxes fixed (`toggleable`, child `onCheckedChange=null`). **Remaining:** Recurring list row is clickable and has a separate `Switch` (Settings.kt:243) → two focus stops | DS: move on/off into the edit dialog or give the switch row its own `toggleable` row | 🟢 = |
| A11Y5 | PASS | Headings | — | = |
| A11Y6 | PASS | `Segments` use `selectable(role=Role.Tab)` + `selectableGroup` (Components.kt:194-205); pickers RadioButton role | — | FAIL→PASS |
| A11Y7 | PASS | `liveRegion` on amount display, field errors, split total (AddEntry.kt:225, 537; Common.kt:135, 146) | — | FAIL→PASS |
| A11Y8 | FAIL 🟠 | Font 200 % (audit_T14_font200.png): bottom-bar labels clipped — "Entrie", "Acco", "Repor" (AppRoot.kt:148 `maxLines=1`); "Withdraw cash" pill cut to "Withdra" (audit_T14_font200_add.png; Row at AddEntry.kt:240 isn't scrollable); budget amount "₹4,0…" runs under the FAB. Keypad and amount OK now | DS: let nav labels shrink (`autoSize` / smaller style at fontScale>1.3) or hide unselected labels; make the pill row `horizontalScroll` or `FlowRow` | 🟠 = (new evidence) |
| A11Y9 | PASS | Text/sign with colour | — | = |
| A11Y10 | Needs real phone | — | — | = |
| A11Y11 | FAIL 🟠 | ATF test skipped on API 33 (ui-tests.log:20) → 0 checks ran | DS: run `AccessibilityTest` on the API 34/35 emulator job (e2e already runs 34/35) | set up, not run |

### PERF (4/6 scored)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| PERF1 | PASS | Cold 874 ms, SplashScreen | — | = (faster) |
| PERF2 | Needs real phone | T7 on software renderer not meaningful; aggregations moved into `remember(entries, r)` (Reports.kt:61-66); LazyColumn lists | Measure on phone | FAIL→NRP |
| PERF3 | FAIL 🟢 | Export/PDF/crash-log writes moved off main (Reports.kt:66-71, Settings.kt:419-421, 466-470). Remaining main-thread I/O: start-up `Prefs` read + `loadLibrary` + `Diagnostics.time` (T4, 11 violations); `Diagnostics.crashLog(ctx)` read during composition (Settings.kt:449) | DS: build Prefs/DB lazily on IO before first frame (splash already waits for `loaded`); move `Diagnostics.time` to appScope; load crash log in `LaunchedEffect` + IO | 🟠→🟢 |
| PERF4 | PASS | Thumbnails `inSampleSize=4`, full view `decodeForScreen` (Entries.kt:358, 389-393) | — | FAIL→PASS |
| PERF5 | PASS | No wake locks/FGS | — | = |
| PERF6 | PASS | R8 + shrink; APK 11.2 MB | — | = |
| PERF7 | FAIL 🟢 | No app baseline profile / T18 | DS: baselineprofile module + Macrobenchmark | = |

### STAB (6/8 scored)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| STAB1 | PASS | 0 crashes/ANRs in all runs | — | = |
| STAB2 | FAIL 🟢 | Fixed: route allow-list (AppRoot.kt:66-68, 116), attachment open `runCatching` (Entries.kt:367-372), saves via guarded `vm.launch` (AppVm.kt:283-291). **Remaining:** Export "Save"/"Share" icons run `build(k)` + `writeBytes` in `rememberCoroutineScope().launch` with no `runCatching` (Settings.kt:419-420) — any build/IO error is an uncaught exception = crash | DS: wrap in `runCatching` + "Couldn't make the file" snackbar (or `vm.launch`) | 🔴→🟢 |
| STAB3 | PASS | Backup/restore/Drive/PDF/SMS scan on `appScope` (`vm.background`, AppVm.kt:294; Sms.kt:257); restore stages receipts then swaps under `NonCancellable` (Backup.kt:74-91) | — | 🔴 FAIL→PASS |
| STAB4 | Needs real phone | Drive needs OAuth client; messages are plain (Drive.kt:39-80) | — | = |
| STAB5 | FAIL 🟢 | Attachment failures now reported (Repo.kt:108, AddEntry.kt:151-152). Backup still built entirely in memory (ByteArrayOutputStream + whole decrypted receipts, Backup.kt:44-51) and restore reads the whole file and plaintext into memory (Backup.kt:58-61; Settings.kt:351) → OOM with many receipts | DS: stream zip → CipherOutputStream → SAF stream (and reverse on restore) | = |
| STAB6 | PASS | T13: 5,000 rows, Entries opens in 1.3 s, 0 ANR | (time search on phone) | FAIL→PASS |
| STAB7 | PASS | Zone read dynamically | — | = |
| STAB8 | PASS | `exportSchema = true`, `room.schemaLocation`, `schemas/…/1.json` in CI output; one version, nothing to migrate yet | — | FAIL→PASS |
| STAB9 | PASS | Setup + empty states (01-06, 66-68) | — | = |

### SEC (18/19)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| SEC-STORAGE1 | PASS | Fails closed: `checkNotNull(key)` (Db.kt:327), `StorageLocked` screen (AppRoot.kt:224-237, MainActivity.kt:111-114), attachments never plaintext (Crypto.kt:96); decrypted copies removed on return (MainActivity.kt:102) and image copy deleted at once (Entries.kt:369) | — | 🟠 FAIL→PASS |
| SEC-STORAGE2 | PASS | `-assumenosideeffects` strips Log.v/d/i in release (proguard-rules.pro) | — | FAIL→PASS |
| SEC-STORAGE3 | PASS | allowBackup=false, rules exclude | — | = |
| SEC-STORAGE4 | PASS | App-private / SAF only | — | = |
| SEC-CRYPTO1 | PASS | SecureRandom, AES-GCM, PBKDF2 | — | = |
| SEC-NET1 | PASS | `network_security_config.xml` cleartext false, system CAs only | — | FAIL→PASS |
| SEC-NET2 | PASS | Default TLS | — | = |
| SEC-PLAT1 | PASS | Explicit exported | — | = |
| SEC-PLAT2 | PASS | FLAG_IMMUTABLE | — | = |
| SEC-PLAT3 | PASS | FileProvider private | — | = |
| SEC-PLAT4 | N/A | No WebView | — | = |
| SEC-PLAT5 | PASS | `SAFE_ROUTE` regex allow-list, unknown routes ignored, `runCatching` around navigate (AppRoot.kt:66-68, 109-118) | — | 🟠 FAIL→PASS |
| SEC-PLAT6 | PASS | Locked UI `alpha(0)` + `clearAndSetSemantics` and routes wait for unlock (AppRoot.kt:111, 124-125); notifications VISIBILITY_PRIVATE + public version (Work.kt:61-62); `filterTouchesWhenObscured` on ConfirmDialog (Components.kt:224-227); Hide amounts uses `hm()` and alpha-0 below API 31 (Components.kt:299-309; 65_home_hidden_amounts shows "₹ ••••" everywhere incl. "more than last month") | — | 🟠 FAIL→PASS |
| SEC-CODE1 | PASS | Release not debuggable | — | = |
| SEC-CODE2 | PASS | Room; CSV formula guard (Export.kt:36) | — | = |
| SEC-CODE3 | FAIL 🟢 | Stack refreshed (SQLCipher 4.20.0, navigation 2.10.2, work 2.12.0, core 1.19.1…); lint still lists coroutines 1.11.0 and serialization 1.13.0; T17 not set up so "0 CVSS ≥ 7" can't be shown | DS: bump the two libs; add OWASP dependency-check to CI | 🟠→🟢 |
| SEC-CODE4 | PASS | No release key/password in repo; CI APK debug-signed, re-signed at delivery | — | = |
| SEC-LEVEL | PASS | L2: SQLCipher fail-closed, app lock, FLAG_SECURE, backups excluded, private notifications | — | FAIL→PASS |
| SEC-PRIV1 | PASS | See §4 | — | = |
| SEC-PRIV2 | PASS | No clipboard | — | = |

### PERM (8/8 rows) — merged APK (audit/permissions.txt)
| Permission | Q1 | Q2 | Q3 | Q4 | Q5 | Q6 | Overall | Fix / note |
|---|---|---|---|---|---|---|---|---|
| READ_SMS, RECEIVE_SMS | ✅ core | ✅ | ✅ rationale screen first (Sms.kt:270-298) | ✅ manual works, "Open settings" when blocked | — | ✅ | ✅ | polish: `denied` still not re-checked on resume (Sms.kt:248) |
| POST_NOTIFICATIONS | ✅ | ✅ | ✅ in context | ✅ re-checked on resume | — | ✅ | ✅ | — |
| USE_BIOMETRIC | ✅ | ✅ | n/a | ✅ PIN | — | ✅ | ✅ | — |
| USE_FINGERPRINT | ✅ API 26-27 | ✅ | n/a | ✅ | biometric lib | ✅ | ✅ | — |
| INTERNET | ✅ Drive | ✅ | n/a | n/a | — | ✅ | ✅ | — |
| WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED | ✅ WorkManager / boot | ✅ | n/a | n/a | WorkManager | ✅ | ✅ | — |
| DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION | ✅ | ✅ | n/a | n/a | androidx.core | ✅ NOTES.md:15 | ✅ | ❌→✅ |
| Contacts number pick (no permission) | ✅ | ✅ | ✅ | ✅ type by hand | — | ✅ NOTES.md:16 | ✅ (confirm on phone) | Fixed: `ACTION_PICK` with `Phone.CONTENT_TYPE`, reads NUMBER from returned URI (Udhar.kt:99-104) ❌→✅ |
Removed: FOREGROUND_SERVICE(_DATA_SYNC), VIBRATE. CAMERA not declared (intent).

### L10N (0/3)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| L10N1 | FAIL 🟠 | strings.xml still 7 strings; 0 `stringResource` calls; all UI text hard-coded. Owner chose English-only UI, which explains it but doesn't meet the standard (or spec §7 "all text in string resources") | DS (mechanical): move strings to `strings.xml`; or record the owner's waiver in app-spec.md so it can be N/A | = |
| L10N2 | Needs real phone | Hindi only in WhatsApp text | — | = |
| L10N3 | FAIL 🟢 | No en-XA run; font-200 clipping (A11Y8) shows the same risk | DS: pseudo-locale run in walkthrough | = |
| L10N4 | FAIL 🟢 | Hand-built plurals (Home.kt:119, Settings.kt:186, 455, AddEntry.kt:152, 431) | DS: `plurals` resources / `pluralStringResource` | = |
| L10N5 | N/A | Report only | — | = |

### UX (8/10)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| UX1 | PASS | Progress, snackbars, step bar | — | = |
| UX2 | PASS | User's words | — | = |
| UX3 | FAIL 🟢 | Fixed: Udhar line tap now confirms then offers Undo (Udhar.kt:201, 220-223); Undo for budget, goal, recurring, SMS OK/OK all/Ignore. **Remaining:** "Delete account" deletes the account and all its entries with no undo (Accounts.kt:241-242, Repo.kt:47-50); "Delete person" same (Udhar.kt:225) — confirm dialog only | DS: snapshot + "Undo" snackbar (Repo already has `Deleted` restore pattern for entries) | 🟠→🟢 |
| UX4 | PASS | Badge = pending + look, same as Home card (AppRoot.kt:106) | — | FAIL→PASS |
| UX5 | FAIL 🟢 | Fixed in Udhar, Teach, Goal, Budget, Category, Person dialogs (Save disabled until valid). **Remaining:** Recurring edit dialog Save is always enabled; an empty/invalid amount closes the dialog and silently drops the change (Settings.kt:250-251) | DS: `enabled = (Money.parse(amt) ?: 0) > 0` | 🟠→🟢 |
| UX6 | PASS | Chips, pickers | — | = |
| UX7 | PASS | Shortcuts, OK all, swipe, search | — | = |
| UX8 | PASS | Clean | — | = |
| UX9 | PASS | Plain messages, no exception text (AppVm.kt:286, Drive.kt:39-80, Settings.kt:348-361) | — | FAIL→PASS |
| UX10 | PASS | Helper text | — | = |

### FORM (3/5)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| FORM1 | PASS | MoneyField validates on blur and clears when fixed (Common.kt:128-138); day fields inline (Accounts.kt) | (polish: account name also on blur) | FAIL→PASS |
| FORM2 | PASS | supportingText below fields | — | = |
| FORM3 | FAIL 🟢 | Discard prompts added (AddEntry, AccountEdit, Transfer). **Gaps:** edit-mode `dirty` ignores tags, UPI app, split and repeat changes (AddEntry.kt:126-128) → Back discards those silently; `TextDialog` (note/tags/min due/real balance) dismisses on outside tap and loses typed text (AddEntry.kt:495-501) | DS: include `tags/upi/splits/rule` in the edit dirty check; `DialogProperties(dismissOnClickOutside = false)` when text changed | 🟠→🟢 |
| FORM4 | PASS | Words capitalisation on names/merchants (Common.kt:143-148, AddEntry.kt:231, 540); correct keyboards | — | FAIL→PASS |
| FORM5 | FAIL 🟢 | Required fields still unmarked; "Add account" enabled with blank name, error only after tap (Accounts.kt:286-287, 330-332). Other dialogs fixed | DS: "Name *" label + disable Save with "Give it a name" visible | = |

### VIS (5/6)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| VIS1 | FAIL 🟢 | 152 off-scale dp values (see BP5) | DS | = |
| VIS2 | PASS | Type scale | — | = |
| VIS3 | PASS | Colour roles | — | = |
| VIS4 | PASS | Short fades | — | = |
| VIS5 | PASS | No clutter | — | = |
| VIS6 | PASS | `imePadding()` + nav-bar padding at root (AppRoot.kt:157); FAB lists have 96 dp bottom padding | — | 🟠 FAIL→PASS |

### FUNC (3/5)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| FUNC1 | PASS | Search/filter/calendar | — | = |
| FUNC2 | FAIL 🟢 | List loading/error states missing (BP4) | DS | = |
| FUNC3 | FAIL 🟢 | Account/person delete without undo (UX3); no change history for edited entries | DS: undo; optional "edited" history | = |
| FUNC4 | PASS | Encrypted backup/restore, exports, Drive | — | = |
| FUNC5 | PASS | Recurring, reminders, shortcuts | — | = |

### REL (5/6)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| REL1 | PASS | targetSdk 36 (note: walkthrough emulator is still API 33; e2e on 33/34/35) | run walkthrough + ATF on 35 | = |
| REL2 | PASS | min 26 / compile 37 / vc 1 / 1.0.0 | — | = |
| REL3 | PASS | v2 valid; debug-signed in CI, permanent key at delivery | keep same key for all updates | = |
| REL4 | PASS | 16 KB aligned | — | = |
| REL5 | PASS | About: Sushant Kumar · sushant.sare@gmail.com | — | = |
| REL6 | FAIL 🟢 | No tag `bahi-khata-v1.0.0` yet | DS: tag at delivery | = |

### Extra (unscored)
- X1 CSV formula injection — fixed (Export.kt:36).
- X3 income step shows only income merchants — fixed (merchant `kind`, AddEntry.kt:233-234).
- X4 🟢 `KeyBus`: `dispatchKeyEvent` returns true for unhandled digit/Del keys on every screen, even when no screen is listening (MainActivity.kt:163) — swallows those keys elsewhere. Only return true when a collector is active (`subscriptionCount > 0`).
- X5 🟢 Lint `ConfigurationScreenWidthHeight` ×4: width/height classes from `LocalConfiguration` instead of `LocalWindowInfo.containerSize` / `currentWindowAdaptiveInfo()` — can mis-size in split screen.

---

## 3. Data inventory
| Data | Where | Protection | Leaves phone? |
|---|---|---|---|
| Accounts, entries, categories, merchants, recurring, budgets, goals | Room `bahikhata.db` | SQLCipher, raw 256-bit key wrapped by Keystore AES-GCM (`noBackupFilesDir/db.key`); fails closed | Only in user backups/exports |
| People (name, phone), udhar | same DB | same | WhatsApp reminder (user action) |
| Financial SMS + parsed fields, learned formats | DB `sms_items`, `learned` | encrypted DB; OTP/promo not stored | inside encrypted backup |
| Seen-SMS keys | DB `sms_seen` (hashes) | encrypted | No |
| Receipts / PDFs | `filesDir/attachments` | AES-GCM with DB key; decrypted copies removed when user returns / immediately for images | encrypted backup; share (user action) |
| Settings | prefs `settings.xml` | private, non-secret | in backup |
| Backup password (Drive) | prefs `device_local` | AES-GCM with DB key | No |
| Usage counters, crash log | prefs `diag`, `filesDir/crash.log` | private | only if user shares |
| Backups | SAF file / Drive appDataFolder | PBKDF2 120k + AES-GCM | Drive only if on |
| Exports | SAF / `cache/share` | plain (user's choice) | user action |
Matches spec privacy statements; round-1 exceptions (fail-open, lingering plaintext cache) are closed.

## 4. Needs real phone
PERF2/T7 jank (CI emulator renders in software); T13 search latency and scroll smoothness with 5,000 rows; A11Y10 remove animations; STAB4 Drive offline/online (OAuth client + release SHA-1); L10N2 Hindi WhatsApp text; contact number pick (no-permission picker); fingerprint/PIN unlock; OEM battery killers vs SMS receiver (battery guide); WhatsApp deep link; T9 typed-amount survival (until `StateRestoreTest` from `15126c4` runs green in CI).

## 5. Remaining FAIL list (26)
🟠 Major (4): AD2 landscape rail clipped · A11Y8 font-200 clipping · A11Y11 ATF never ran (= T2) · L10N1 hard-coded strings
🟢 Minor (22): BP1 tablet side sheet · BP2 mockup diffs · BP4 list loading/error · BP5 tokens · CQ5 two dialogs not saveable · AD3 same · A11Y4 recurring row · PERF3 start-up main-thread I/O · PERF7 baseline profile · STAB2 export crash path · STAB5 in-memory backup · SEC-CODE3 two libs + T17 · L10N3 en-XA · L10N4 plurals · UX3 account/person delete undo · UX5 recurring Save · FORM3 dirty-check gaps · FORM5 required marks · VIS1 spacing · FUNC2 · FUNC3 · REL6 tag.

## 6. Change since round 1 (summary)
Fixed (28 items FAIL→PASS): CQ2, CQ7, AD4, AD7, AD8, A11Y1, A11Y2, A11Y3, A11Y6, A11Y7, PERF4, STAB3, STAB6, STAB8, SEC-STORAGE1, SEC-STORAGE2, SEC-NET1, SEC-PLAT5, SEC-PLAT6, SEC-LEVEL, PERM DYNAMIC row, PERM contacts row, UX4, UX9, FORM1, FORM4, VIS6 (+ PERF2 moved to Needs real phone). Downgraded in severity: BP1 🔴→🟢, STAB2 🔴→🟢, CQ5/AD3/PERF3/SEC-CODE3/UX3/UX5/FORM3 🟠→🟢. All 4 round-1 Criticals are resolved. New finding: landscape NavigationRail clipping (AD2). No regressions.
