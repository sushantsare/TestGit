# Bahi Khata — Audit round 1 (independent)

App: `com.sushant.bahikhata` · versionName 1.0.0 / versionCode 1 · source SHA `4da76ef` · Distribution: personal (PLAY = N/A) · MASVS L2
Auditor: independent agent (repo + blueprint + CI outputs only) · Date: 2026-11-21
Evidence roots: `apps/bahi-khata/` (source), `apps/bahi-khata/blueprint/` + `design/` (spec, mockups), `/home/claude/result/` (CI: screens/, audit/, e2e-results-*.txt, ui-tests.log, unit-tests.log, walk-log.txt, bahi-khata.apk).

## 0. Result

**Grade D · overall 52 % (59 PASS / 54 FAIL; N/A and "Needs real phone" not scored) · 4 Critical (🔴)**

| Section | PASS / (PASS+FAIL) | Score |
|---|---|---|
| BP Blueprint | 2 / 6 | 33 % |
| CQ Core quality | 8 / 11 | 73 % |
| AD Adaptive | 4 / 9 | 44 % |
| A11Y | 2 / 10 | 20 % |
| PERF | 3 / 7 | 43 % |
| STAB | 3 / 8 | 38 % |
| SEC | 12 / 19 | 63 % |
| PERM (rows) | 6 / 8 | 75 % |
| L10N | 0 / 3 | 0 % |
| UX | 6 / 10 | 60 % |
| FORM | 1 / 5 | 20 % |
| VIS | 4 / 6 | 67 % |
| FUNC | 3 / 5 | 60 % |
| REL | 5 / 6 | 83 % |
| PLAY | N/A (personal) | — |

No previous audit exists → no regression comparison. Important evidence gap: `screens/audit_T14_font200.png`, `audit_T14_night.png`, `audit_T14_tablet.png`, `audit_T9_after_kill.png`, `e2e_33_launch.png`, `e2e_34_launch.png` are **0-byte files** (screencap failed; FLAG_SECURE is on by default), and `audit/T7_gfxinfo.txt` measured **0 frames**. Items that depended on them were judged from code and marked accordingly.

---

## 1. Tool results (T1–T19)

| ID | Result | Evidence | Verdict |
|---|---|---|---|
| T1 Lint/detekt/ktlint | 1 error (`ByteOrderMark`, Export.kt:37 literal BOM), 67 warnings (30 UseKtx, 20 GradleDependency, 4 NewerVersionAvailable, 2 ObsoleteSdkInt, OldTargetApi, DefaultLocale Settings.kt:119, ApplySharedPref Prefs.kt:121, AndroidGradlePluginVersion). detekt/ktlint not run; `lintDebug` not `lintRelease`; `checkReleaseBuilds=false`, `abortOnError=false` (build.gradle.kts:47) | audit/lint-results-debug.txt "1 error, 67 warnings" | FAIL |
| T2 ATF | Not set up: no `ui-test-junit4-accessibility` dep, no `enableAccessibilityChecks()` in WalkthroughTest.kt | app/build.gradle.kts deps; grep | FAIL |
| T3 Monkey | 5000 events, crashes=0, ANRs=0; release monkey 1500 × API 33/34/35 = 0 | audit/summary.txt; e2e-results-3x.txt `monkey_crashes_*=0` | PASS |
| T4 StrictMode | `strictmode=0` is meaningless: no `StrictMode` call anywhere in source (grep), no `src/debug` | grep `StrictMode` → none | FAIL |
| T5 Leaks | LeakCanary running ("LeakCanary is running…"), 0 APPLICATION LEAKS | T3_logcat_debug.txt | PASS |
| T6 Startup | cold median 2068 ms (1891–2391), warm median 107 ms | T6_cold.txt / T6_warm.txt | PASS (rule: FAIL > 5000 ms) — misses the 1500 ms target and spec goal |
| T7 Jank | "Total frames rendered: 0" → swipes didn't hit the app; no measurement | T7_gfxinfo.txt | FAIL (invalid) |
| T8 Memory | PSS 51,818 KB → 58,548 KB after 3 relaunches (only 2 samples) | T8_mem1/2.txt | PASS (no sustained growth shown) |
| T9 Process death | 0 crashes, but test kills from Home (not mid-form), doesn't check typed data; screenshot 0 bytes | e2e.sh:123-128; audit_T9_after_kill.png 0 B | FAIL (evidence) |
| T10 Offline | 0 crashes in 800-event offline monkey; Drive screen not walked | summary.txt | PASS |
| T11 Battery modes | 0 crashes | summary.txt | PASS |
| T12 Upgrade | first release, no previous APK | — | N/A |
| T13 Large data | not run (no 5,000-row seed in CI) | e2e.sh | FAIL |
| T14 Config stress | 0 crashes, but all three screenshots 0 bytes | summary.txt; screens/audit_T14_* | FAIL (evidence) |
| T15 APK | v2 valid, 25,033,552 B, 8 .so (4 ABIs), zipalign -P 16 OK, ELF LOAD align 0x4000 (16 KB) checked with readelf | T15_*.txt; readelf on extracted libs | PASS |
| T16 Roborazzi | not set up — judged not required for a personal app | — | N/A |
| T17 Dependency scan | not set up; lint shows SQLCipher 4.6.1 (4.20.0 available) and 20+ stale libs → judged required | lint-results-debug.txt | FAIL 🟢 |
| T18 Macrobenchmark | not set up; APK has only library `baseline.prof` (8 KB); cold start misses 1.5 s → judged required | `unzip -l` → assets/dexopt/baseline.prof | FAIL 🟢 |
| T19 Release smoke | release APK: monkey 1500/API on 33/34/35 = 0 crashes, SMS e2e PASSED ×3 (slightly under 2000 events) | e2e-results-33/34/35.txt | PASS |

Other runs: unit tests 14/14 pass (LogicTest 8, SmsParserTest 6); instrumented 12/12 OK incl. WalkthroughTest `problems=0` (walk-log.txt); SMS e2e (receiver after kill, OTP ignored, needs-a-look, catch-up exactly once) PASSED on API 33/34/35. Note: the main audit/walkthrough emulator is API 33 (e2e-results-33.txt header), not 35+.

---

## 2. Checklist

Severity: 🔴 Critical · 🟠 Major · 🟢 Minor. "DS" = design-safe fix, "DC" = changes approved design/feature.

### BP — Blueprint conformance
| ID | Verdict | Evidence | Fix |
|---|---|---|---|
| BP1 | **FAIL 🔴** | (a) **Split edit bug:** editing a split expense and choosing "No split" keeps the old Udhar shares — `Repo.saveEntry` only touches udhar `if (r.splits.isNotEmpty())` (Repo.kt:89-94); shares also aren't recomputed when the amount is edited (AddEntry.kt:88 loads old shares) → wrong Udhar balances. (b) Spec v1 items missing: split "by amounts" (only equal, AddEntry.kt:444; leftover paise dropped), category reorder (no reorder UI), Reports "custom period" (Reports.kt:80 only Week/Month/Year; mockup 14 shows "Custom"), minimum due editable (no field in AccountEditScreen), configurable due-reminder days (`dueReminderDays` has no UI), budget-cycle first day (`monthStartDay` never read), SMS swipe right OK / left Ignore (no swipe code), OEM battery-guide screen (`batteryTipShown` unused), add-entry as side sheet on expanded, keyboard shortcuts (§6), SMS test corpus "600+ formats, 40+ banks" (42 hand cases + 624 generated from 12 templates, SmsParserTest.kt / LogicTest.kt:84-114) | (a) DS: in `saveEntry` always delete `udharOfEntry(id)` then insert current splits; recompute equal shares from the saved amount; distribute remainder paise. (b) DS (all approved in app-spec.md): implement each item; add per-person amount mode to SplitDialog, drag-reorder in CategoriesScreen, Custom chip with DateRangePicker in Reports, min-due field on card edit, reminder-days chips + month-start picker in Settings, `SwipeToDismissBox` on SmsCard, battery guide sheet, expand corpus |
| BP2 | FAIL 🟢 | vs mockups: 02 shows "Food · HDFC remembered" hint inside shop field, app shows chips (08); 04 shows account tiles with balances and a "Card" UPI chip, app shows type label and "Direct / card" (10); 04 has single "2 attachments" row, app shows Camera/Gallery/PDF pills; 09 merges "Cash & wallets", app has separate Cash / Wallets (20); 13 uses ⋮ menu + tonal WhatsApp button, app has edit+delete icons, outlined button (30); 14 has Custom chip + share icon (33); 16 SMS cards show "•• 8812" (35) | DS: align the listed screens to the mockups |
| BP3 | PASS | NavHost routes (AppRoot.kt:145-176) match screen map; Budgets reachable from Reports and Settings | — |
| BP4 | FAIL 🟢 | Spec §4 requires loading skeleton + error/retry for list screens; none exist. Detail screens render an empty `Box` while loading/missing (Entries.kt:274, Accounts.kt:140, Udhar.kt:145) | DS: add skeleton + "Couldn't load · Retry" states |
| BP5 | FAIL 🟢 | `LocalSpacing` tokens (Theme.kt:124) never used; screens use stray 6/10/14/18 dp (Components.kt:225,262; Home.kt:242,270) | DS: use Spacing tokens |
| BP6 | PASS | Diagnostics is local (Diagnostics.kt), counters only, 47_diagnostics.png | — |

### CQ — Core app quality
| ID | Verdict | Evidence | Fix |
|---|---|---|---|
| CQ1 | PASS | M3 components, system Back icons; custom `Segments` acceptable | — |
| CQ2 | **FAIL 🟠** | Back/close silently discards typed data: AddEntry step 1 and edit mode pop without prompt (AddEntry.kt:110, 142); Setup steps 2–4 have no BackHandler → system Back exits the app (Setup.kt:52-58) | DS: BackHandler + "Discard this entry?" when amount/fields changed; BackHandler(step>1){step--} in Setup |
| CQ3 | PASS | Single activity, `singleTask`, VM + rememberSaveable; lock re-check on start (MainActivity.kt:79-85) | — |
| CQ4 | PASS | Nothing playing; lock after `lockAfterSec` (MainActivity.kt:83) | — |
| CQ5 | **FAIL 🟠** | Dialog state uses `remember` → lost on rotation/process death: UdharDialog (Udhar.kt:225-227), PersonDialog (91-94), TextDialog (AddEntry.kt:431), SplitDialog (441-443), PasswordDialog, budget/goal dialogs; dialog-open flags (`adding`, `entryDlg`, `confirm`…) also `remember` | DS: `rememberSaveable` for typed text and open flags |
| CQ6 | PASS | Light + dark complete on all walked screens (49–58) | — |
| CQ7 | **FAIL 🟠** | (a) Status-bar icons dark-on-dark when the in-app theme is Dark and the phone is light (49–58: clock/icons invisible) — `enableEdgeToEdge()` default follows system, not `s.theme` (MainActivity.kt:91). (b) Bottom action buttons (Next, Save, Pay card bill, You gave/You got, Add account…) have no nav-bar inset: button bottom touches the gesture handle (crops of 08/10/23/30); with 3-button nav they sit under the bar. `contentWindowInsets = WindowInsets(0)` everywhere, no `navigationBarsPadding` (grep) | DS: `enableEdgeToEdge(SystemBarStyle.auto(..){ isDark })` re-applied when theme changes; add `Modifier.navigationBarsPadding()` to bottom button rows / list bottom padding on non-tab screens |
| CQ8 | PASS | Vector icons, adaptive + monochrome icon | — |
| CQ9 | PASS | 5 named channels with descriptions (Work.kt:34-40); POST_NOTIFICATIONS asked in context (Sms.kt:266, Settings.kt:64-73); tap routes to sms/account/budgets | (see SEC-PLAT6 for lock-screen content) |
| CQ10 | PASS | WorkManager periodic/one-time with constraints (Work.kt:126-160); T11 0 crashes | — |
| CQ11 | N/A | Not a media app | — |
| CQ12 | PASS | `Intent.createChooser` (Common.kt:146-150) | — |

### AD — Adaptive
| ID | Verdict | Evidence | Fix |
|---|---|---|---|
| AD1 | PASS | No orientation/resizable locks in manifest | — |
| AD2 | **FAIL 🟠** | Phone landscape: AddEntry step 1 has ≈468 dp of fixed-height content (top bar 64 + segments 76 + keypad 240 + Next 84) in a non-scrolling Column (AddEntry.kt:187-214) vs ≈387 dp available → Next button clipped. Tablet/landscape screenshots missing (audit_T14_tablet.png 0 B) | DS: in landscape/short heights put keypad beside amount (Row) or make the whole step scrollable; fix screenshot capture (turn off FLAG_SECURE for the debug audit run) |
| AD3 | FAIL 🟠 | Same root as CQ5: typed dialog data and open dialogs lost on resize/rotation | DS: as CQ5 |
| AD4 | FAIL 🟢 | No `keyboardActions`/`ImeAction` anywhere: Enter doesn't move to next field or submit | DS: ImeAction.Next/Done + keyboardActions on forms |
| AD5 | PASS | Rail at ≥600 dp, bottom bar below (AppRoot.kt:87-89,107,125) | — |
| AD6 | PASS | Two panes at ≥840 dp for Entries/Accounts/Udhar (Entries.kt:128, Accounts.kt:91, Udhar.kt:79) | — |
| AD7 | FAIL 🟢 | Full-width buttons and forms on tablets: Save/Next `fillMaxWidth()` outside the 640/720 dp column; `widthIn(max)` not centered (AddEntry.kt:153, Accounts.kt:261,340) | DS: center content with `widthIn(max=640.dp)` + `Alignment.CenterHorizontally` incl. bottom buttons |
| AD8 | FAIL 🟢 | Spec §6 Ctrl+N, Ctrl+F, Esc: no `onKeyEvent` in code | DS: add `onPreviewKeyEvent` at AppRoot |
| AD9 | PASS | Handwriting not disabled | — |
| AD10 | N/A | Tier 1, report only | — |

### A11Y
| ID | Verdict | Evidence | Fix |
|---|---|---|---|
| A11Y1 | FAIL 🟢 | Text passes everywhere (computed: onSurfaceVariant on bg 5.65, on segment track 5.10, dark 7.32–8.10, income 5.26, expense 6.43, warn 6.59/11.09, onInfo 7.83/8.59). Non-text fails: budget bar at 80 % saffron vs track 2.20:1, bar track vs card 1.17:1 (Components.kt:293-297); dark status-bar icons (CQ7) | DS: darker track / outline on bars or add % text |
| A11Y2 | FAIL 🟠 | Card-colour swatches 40 dp (Accounts.kt:282), category colour swatches 34 dp with 8 dp gap and icon tiles 42 dp with 6 dp gap (Settings.kt:190,194) | DC (minor visual): 48 dp targets |
| A11Y3 | FAIL 🟠 | Colour swatches have no label — `semanticsLabel()` only sets a testTag (Accounts.kt:283,312); category colour swatches unlabeled (Settings.kt:194); keypad "⌫" read as a symbol (AddEntry.kt:222) | DS: `semantics { contentDescription = "Colour 3, dark blue" }`, "Delete digit" for ⌫ |
| A11Y4 | FAIL 🟢 | Checkbox rows have both a clickable row and a separately focusable Checkbox (AddEntry.kt:448-451); `SettingSwitch` row clickable + separate Switch (Components.kt:337-340) → two focus stops | DS: `Modifier.toggleable(role=Checkbox/Switch)` on the row, child `onCheckedChange=null` |
| A11Y5 | PASS | SectionLabel/SectionTitle marked `heading()` (Components.kt:226,234) | (polish: mark screen titles as headings) |
| A11Y6 | FAIL 🟠 | `Segments` (Expense/Income, They owe/I owe, Money out/in) have no Role.Tab / selected state (Components.kt:321-334); swatch/icon pickers no selected state | DS: `selectable(selected, role=Role.Tab)` |
| A11Y7 | FAIL 🟢 | Form errors (supportingText) and scan progress not announced; no liveRegion (grep) | DS: `liveRegion = Polite` on error/progress text |
| A11Y8 | FAIL 🟠 | 200 % screenshot is 0 B. Code: `MoneyText` `maxLines=1, softWrap=false` with no ellipsis (Components.kt:211-212) clips large amounts; keypad keys fixed 54 dp with titleLarge (42 sp at 2×) (AddEntry.kt:229,242); AddEntry fixed-height step (AD2) | DS: autosize/ellipsis amounts, `heightIn(min=54.dp)`, scrollable step; re-capture font200 |
| A11Y9 | PASS | Amounts carry +/− and "they owe / you owe" labels (29_udhar, 15_entries) | — |
| A11Y10 | Needs real phone | Not exercised in CI | Check with "Remove animations" |
| A11Y11 | FAIL 🟠 | T2 not set up | DS: add ATF to walkthrough |

### PERF
| ID | Verdict | Evidence | Fix |
|---|---|---|---|
| PERF1 | PASS | T6 2068/107 ms, SplashScreen API (MainActivity.kt:88-90) | (target 1500 ms missed → PERF7) |
| PERF2 | FAIL 🟢 | T7 invalid (0 frames); all filtering/aggregation of the full entries list runs in composition every recomposition (Entries.kt:66-70, Home.kt:204-214, Reports.kt:56-61, Settings.kt Export `rows`) | DS: move to VM `derivedStateOf`/flows; fix T7 (bring app to front, scroll Entries) |
| PERF3 | **FAIL 🟠** | No StrictMode (T4 invalid). Main-thread file I/O: Reports.kt:69 `f.writeBytes`, Export share `f.writeBytes` (Settings.kt ~403), Diagnostics `f.writeText` (Settings.kt ~449); keystore + key-file read in `App.onCreate` via `AppDb.open(key = DbKey.get())` (App.kt:33, Crypto.kt:41-57) | DS: `withContext(IO)` for those writes; lazy DB open; add StrictMode `detectAll().penaltyLog()` in a debug-only Application hook |
| PERF4 | FAIL 🟢 | T5 0 leaks, T8 stable-ish; but tapping a photo decodes full resolution (Entries.kt:360) | DS: `inSampleSize` to screen size |
| PERF5 | PASS | No wake locks/FGS; work batched | — |
| PERF6 | PASS | `isMinifyEnabled` + `isShrinkResources` (build.gradle.kts:38-40); APK 25.0 MB (SQLCipher ×4 ABIs); T19 pass | (optional ABI split) |
| PERF7 | FAIL 🟢 | Only library baseline.prof; no app profile; T18 absent | DS: baselineprofile module + Macrobenchmark |

### STAB
| ID | Verdict | Evidence | Fix |
|---|---|---|---|
| STAB1 | PASS | T3/T10/T11/T14 0 crashes; walkthrough problems=0; e2e ×3 PASSED | — |
| STAB2 | **FAIL 🔴** | Crash paths: exported `MainActivity` reads any `route` extra (MainActivity.kt:126) and `nav.go(r)` throws `IllegalArgumentException` for an unknown route (AppRoot.kt:101) — any app can crash Bahi Khata; attachment tap has no try/catch around decrypt/decode (Entries.kt:357-361); `pdf()` no runCatching (Reports.kt:63-71); `vm.launch` saves have no error handling (AddEntry.kt:121-127, Accounts.kt:306) | DS: whitelist routes (see SEC-PLAT5); `runCatching` + snackbar on those paths |
| STAB3 | **FAIL 🔴** | Backup save/restore, Drive restore, 90-day SMS scan and PDF run in `rememberCoroutineScope()` (Settings.kt BackupScreen, Sms.kt:225-237): leaving the screen cancels them. Restore commits the DB transaction, then deletes and rewrites `attachments/` (Backup.kt:177-187) — cancellation between the two loses receipts (data loss) | DS: run in `app.appScope`/WorkManager; write attachments to a temp dir first, swap after DB commit |
| STAB4 | Needs real phone | T10 0 crashes, but Drive (only online feature) needs the OAuth client | Verify offline Drive backup message + retry |
| STAB5 | FAIL 🟢 | Backup built fully in memory (zip + encrypted copy, Backup.kt:148-156) → OOM with many receipts; attachment copy failures swallowed silently (Repo.kt:98) | DS: stream to file; show "1 receipt couldn't be saved" |
| STAB6 | FAIL 🟢 | T13 not run; whole tables loaded into memory, no Paging (spec §7) | DS: seed 5,000 rows test; Paging for Entries/Search |
| STAB7 | PASS | Zone read dynamically (Money.kt:105-111) | — |
| STAB8 | FAIL 🟢 | `exportSchema = false` (Db.kt:315) contrary to spec §7 ("exported schemas") — future migrations can't be tested; T12 N/A | DS: `exportSchema=true` + `room.schemaLocation`, commit schema 1 |
| STAB9 | PASS | 01–06 setup, 60–62 empty states | — |

### SEC
| ID | Verdict | Evidence | Fix |
|---|---|---|---|
| SEC-STORAGE1 | **FAIL 🟠** | Encryption fails open: if `DbKey.get()` returns null the DB is created **unencrypted** (Db.kt:324-330) and attachments stored plaintext (Crypto.kt:92); decrypted receipts stay in `cache/open` until the next cold start (Repo.kt:163-168, App.kt:39) | DS: fail closed (error screen), never plaintext; delete decrypted copies on leaving the viewer |
| SEC-STORAGE2 | FAIL 🟢 | Release logs routes and SMS stats: `Log.d("BahiNav")` (AppRoot.kt:56,79), `Log.i(BahiSms)` (SmsEngine.kt:79,88,103); no `-assumenosideeffects` | DS: proguard strip `Log.d/i/v` in release (keep tests on debug) |
| SEC-STORAGE3 | PASS | `allowBackup=false`, rules exclude all domains | — |
| SEC-STORAGE4 | PASS | Only app-private + user-chosen SAF destinations | — |
| SEC-CRYPTO1 | PASS | SecureRandom, AES-GCM, PBKDF2 120k (Crypto.kt) | — |
| SEC-NET1 | FAIL 🟢 | No `network_security_config`; minSdk 26 → cleartext allowed by default on API 26–27 (all URLs are https, Drive.kt) | DS: add config `cleartextTrafficPermitted="false"` |
| SEC-NET2 | PASS | Default TLS | — |
| SEC-PLAT1 | PASS | Explicit exported; SmsReceiver guarded by BROADCAST_SMS; BootReceiver checks action | — |
| SEC-PLAT2 | PASS | FLAG_IMMUTABLE (Work.kt:48-49,82-83) | — |
| SEC-PLAT3 | PASS | FileProvider not exported, cache paths only | — |
| SEC-PLAT4 | N/A | No WebView | — |
| SEC-PLAT5 | **FAIL 🟠** | External `route` extra used unvalidated (MainActivity.kt:126 → AppRoot.kt:101) — e2e itself uses `am start --es route sms` | DS: accept only a fixed set (`sms`, `add`, `udhar`, `budgets`, `backup`, `entries`, `account/<digits>`) and ignore others |
| SEC-PLAT6 | **FAIL 🟠** | FLAG_SECURE OK (MainActivity.kt:98) but (a) the lock overlay is drawn over the full app, whose semantics tree stays live (AppRoot.kt:105-138) — accessibility services can read balances while locked, and pending routes navigate behind the lock; (b) SMS/due/budget notifications show amounts + merchants on the lock screen with no public version, even with app lock / Hide amounts on (Work.kt:64-97; e2e_35_sms_notification.png); (c) no `filterTouchesWhenObscured` on Restore / Delete everything confirms | DS: don't compose content while locked (or `clearAndSetSemantics`); `setPublicVersion("New bank SMS to review")` + VISIBILITY_PRIVATE; obscured-touch filter on destructive confirms |
| SEC-CODE1 | PASS | Release minified, no debuggable flag in badging; LeakCanary debug only | — |
| SEC-CODE2 | PASS | Room parameterised queries; kotlinx JSON | (see X1 CSV injection) |
| SEC-CODE3 | FAIL 🟠 | SQLCipher 4.6.1 (4.20.0 available) is the encryption engine; navigation 2.8.5, serialization 1.8.0, coroutines 1.10.1, work 2.10.0, core 1.16.0 … (lint GradleDependency ×20); T17 absent | DS: refresh deps, add dependency-check |
| SEC-CODE4 | PASS | Only `debug.keystore` in repo; release signing from `BAHIKHATA_KEYSTORE_*` env (build.gradle.kts:24-33); no passwords | Note: CI release APK is signed `CN=Android Debug` (T15) — must be re-signed with the permanent key before delivery |
| SEC-LEVEL | FAIL 🟠 | L2 partly met (SQLCipher, app lock, FLAG_SECURE, backups excluded) but fail-open encryption and lock leak (above) | DS: fix SEC-STORAGE1 + SEC-PLAT6 |
| SEC-PRIV1 | PASS | Inventory (§4) matches spec privacy note | — |
| SEC-PRIV2 | PASS | No ClipboardManager use (grep) | — |

### PERM — permission table (merged APK: audit/permissions.txt)
| Permission | Q1 needed | Q2 granular | Q3 right moment | Q4 denial | Q5 library | Q6 documented | Overall | Fix |
|---|---|---|---|---|---|---|---|---|
| READ_SMS, RECEIVE_SMS | ✅ core (spec §2.9) | ✅ | ✅ after rationale screen (Sms.kt:241-269), never on launch | ✅ manual still works, "Open settings" when blocked (Sms.kt:262); note: `denied` not re-checked on resume | — | ✅ | ✅ | (polish: re-check on resume) |
| POST_NOTIFICATIONS | ✅ | ✅ | ✅ with SMS or when a reminder switch is turned on | ✅ "Notifications are blocked · Allow", re-checked ON_RESUME (Settings.kt:64-73,125-129) | — | ✅ | ✅ | — |
| USE_BIOMETRIC | ✅ | ✅ | n/a | ✅ PIN fallback | — | ✅ | ✅ | — |
| USE_FINGERPRINT | ✅ (API 26–27) | ✅ | n/a | ✅ | biometric lib, used | ✅ | ✅ | — |
| INTERNET | ✅ Drive only | ✅ | n/a | n/a | — | ✅ | ✅ | — |
| WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED | ✅ WorkManager/boot catch-up | ✅ | n/a | n/a | WorkManager, used | ✅ | ✅ | — |
| DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION | ✅ (androidx.core, signature) | ✅ | n/a | n/a | library | ❌ not in NOTES table | ❌ 🟢 | DS: add row |
| (missing) contacts number pick | — | — | — | ❌ silently fails: PickContact grants only the contact URI, then the app queries `CommonDataKinds.Phone` by CONTACT_ID (Udhar.kt:100-101) which needs READ_CONTACTS → SecurityException swallowed by runCatching → phone never filled | — | — | ❌ 🟠 (confirm on real phone) | DS: launch `ACTION_PICK` on `CommonDataKinds.Phone.CONTENT_URI` and read NUMBER from the returned URI (no permission) |
Removed correctly: FOREGROUND_SERVICE(_DATA_SYNC), VIBRATE (`tools:node="remove"`). CAMERA not declared → camera intent works without permission ✅.

### L10N
| ID | Verdict | Evidence | Fix |
|---|---|---|---|
| L10N1 | **FAIL 🟠** | strings.xml has 7 strings (app name + shortcuts); 0 `stringResource` calls; every UI string hard-coded (spec §7 "all text in string resources") | DS: move to strings.xml |
| L10N2 | Needs real phone | Hindi only in WhatsApp text + picker label "हिंदी (Hindi)" | Check WhatsApp rendering |
| L10N3 | FAIL 🟢 | Not tested; single-line no-ellipsis money text and maxLines=1 labels (Components.kt:212, AppRoot.kt:129) | DS: en-XA run |
| L10N4 | FAIL 🟢 | Plurals hand-built (`if (n == 1)` Home.kt:277, Settings.kt:175, Work.kt:85,92,99); ₹ Indian grouping correct | DS: `plurals` resources |
| L10N5 | N/A | report only — `supportsRtl=true`; non-mirroring `Icons.Outlined.ArrowBack` | (use AutoMirrored) |

### UX (Nielsen)
| ID | Verdict | Evidence | Fix |
|---|---|---|---|
| UX1 | PASS | Progress bars (backup, SMS scan), snackbars, step bar | — |
| UX2 | PASS | "Udhar", "You gave/You got", "Pay card bill" | — |
| UX3 | **FAIL 🟠** | Tapping a Person history line **deletes it immediately** (Udhar.kt:195-198, "Tap a line to remove it"); undo re-adds without the entry link; no undo for account delete, person delete, budget remove (Reports.kt:213), "OK all sure ones" (Sms.kt:98-101) | DC (interaction): tap opens details with Delete; DS: undo snackbars for the rest |
| UX4 | FAIL 🟢 | Home nav badge counts PENDING only ("3") while the Home card counts PENDING+LOOK ("4 SMS entries need your OK") (07_home; AppRoot.kt:115 vs Home.kt:276) | DS: same count both places |
| UX5 | **FAIL 🟠** | Invalid submits silently do nothing / close: UdharDialog Save with empty amount (Udhar.kt:239), Fix & teach without account (Sms.kt:204), category Save with blank name closes (Settings.kt:201), goal Save with no target closes and drops input (Reports.kt:226-230) | DS: disable Save until valid + inline reason |
| UX6 | PASS | Merchant chips, pickers, remembered account | — |
| UX7 | PASS | Shortcuts, OK all, search, quick FAB | — |
| UX8 | PASS | Clean screens (07, 20, 41) | — |
| UX9 | FAIL 🟢 | Raw exception text shown: "Couldn't save: ${it.message}", "Drive said 403", "Google sign-in isn't available on this phone: <exception>" (Settings.kt BackupScreen, Drive.kt:62) | DS: plain messages + Retry |
| UX10 | PASS | Helper text on forms (03, 27, 24) | — |

### FORM
| ID | Verdict | Evidence | Fix |
|---|---|---|---|
| FORM1 | FAIL 🟢 | Money fields never show inline errors (`MoneyField` has no isError, Common.kt:117-120); account name error only after pressing Save | DS: validate on blur |
| FORM2 | PASS | supportingText below fields, labels always visible | — |
| FORM3 | **FAIL 🟠** | No unsaved-changes prompt on AddEntry / AccountEdit / Transfer close; dialogs dismiss on outside tap losing input | DS: confirm-discard; `dismissOnClickOutside=false` when dirty |
| FORM4 | FAIL 🟢 | Keyboards correct, but names/merchants lack `KeyboardCapitalization.Words` (TextIn, AddEntry.kt:197) | DS |
| FORM5 | FAIL 🟢 | Required fields not marked; Save enabled while invalid (Accounts.kt:297-307, Udhar/Teach/Goal dialogs) | DS |

### VIS
| ID | Verdict | Evidence | Fix |
|---|---|---|---|
| VIS1 | FAIL 🟢 | Off-scale 6/10/14/18 dp throughout (see BP5) | DS |
| VIS2 | PASS | Type scale used | — |
| VIS3 | PASS | Colours via MaterialTheme / LocalBk | — |
| VIS4 | PASS | Fade nav transitions, short bar animation | — |
| VIS5 | PASS | No duplicate menus/clutter | — |
| VIS6 | **FAIL 🟠** | Edge-to-edge + no `imePadding` anywhere (grep): with `enableEdgeToEdge` `adjustResize` no longer resizes, so the keyboard covers lower fields and bottom Save buttons (AccountEdit, Transfer, Search, AddEntry details) | DS: `Modifier.imePadding()` on form Columns/bottom bars |

### FUNC
| ID | Verdict | Evidence | Fix |
|---|---|---|---|
| FUNC1 | PASS | Entries search + filters + calendar; Udhar search > 6 people | — |
| FUNC2 | FAIL 🟢 | No loading/error states (BP4) | DS |
| FUNC3 | FAIL 🟢 | Missing undo listed in UX3; no change history | DS |
| FUNC4 | PASS | Encrypted .bkb backup/restore, CSV/XLSX/PDF export, Drive auto-backup (Drive needs OAuth client → real phone) | — |
| FUNC5 | PASS | Recurring, reminders, shortcuts, OK all | — |

### REL
| ID | Verdict | Evidence | Fix |
|---|---|---|---|
| REL1 | PASS | targetSdk 36 (badging.txt) | (main audit emulator is API 33; run walkthrough on 35/36) |
| REL2 | PASS | min 26 / compile 37 / vc 1 / 1.0.0 | — |
| REL3 | PASS | v2 signature valid (T15). Condition: signed with **debug cert** (CN=Android Debug) — first release, so the permanent key must be applied at delivery and kept for every update | — |
| REL4 | PASS | zipalign -P 16 OK; ELF LOAD align 0x4000 | — |
| REL5 | PASS | 48_about.png: "Developed by Sushant Kumar" + sushant.sare@gmail.com only | — |
| REL6 | FAIL 🟢 | CHANGELOG 1.0.0 present, no tag `bahi-khata-v1.0.0` (`git tag -l 'bahi*'` empty) | DS: tag at delivery |

### Extra (unscored)
- X1 🟢 CSV formula injection: SMS-derived merchant names starting with `= + - @` are written raw (Export.kt:36) → prefix with `'`. DS.
- X2 🟢 SMS notifications set a group but no summary notification (Work.kt:73,85). DS.
- X3 🟢 Income step shows expense merchants as chips (14_add_income). DS.

---

## 3. Hide-amounts leaks (feeds FUNC/SEC findings, all DS)
With Hide amounts on, these still show rupees: Home "₹8,524 more than last month" (Home.kt:264; visible in 59_home_hidden_amounts.png), entry-detail split rows (Entries.kt:309), Budgets "₹x left" / over-by (Reports.kt:184), goal target (Reports.kt:196), Transfer screen balances (Accounts.kt:348-379), SMS balance-mismatch row (Sms.kt:155), Recurring list (Settings.kt:222), notifications. `Hideable` uses `Modifier.blur`, which is a no-op below Android 12 (API 26–30) → budget/net-worth amounts fully visible there (Components.kt:422-424). Scored as part of SEC-PLAT6 (sensitive amounts exposed on screen when the user chose to hide them) — 🟠. Fix: route every amount through `MoneyText`/a `hide()` helper; replace blur with "₹ ••••".

## 4. Data inventory
| Data | Where stored | Protection | Leaves phone? |
|---|---|---|---|
| Accounts, entries, categories, merchants, recurring, budgets, goals | Room `bahikhata.db` | SQLCipher, key wrapped by Keystore AES-GCM (`noBackupFilesDir/db.key`) — fails open if key unavailable | Only in user backups/exports |
| People (name, phone), udhar | Same DB | same | WhatsApp reminder text (user action) |
| Financial SMS bodies + parsed fields, learned formats | DB `sms_items`, `learned` | encrypted DB; OTP/promo/non-financial never stored (SmsEngine.kt:114) | Inside encrypted backup |
| Seen-SMS keys | DB `sms_seen` (SHA-256 hashes) | encrypted DB | No |
| Receipts / PDFs | `filesDir/attachments` | AES-GCM with DB key; decrypted copies in `cache/open` until next start | Inside encrypted backup; share (user action) |
| Settings | SharedPreferences `settings.xml` (plain, non-secret) | private | In backup |
| Backup password (for Drive) | prefs `device_local` | AES-GCM with DB key | No |
| Scan times, asked flags | prefs `device_local` | private | No (excluded) |
| Usage counters, crash log (stack traces) | prefs `diag`, `filesDir/crash.log` | private, plain | Only if user shares |
| Backups | user-chosen file / Drive appDataFolder | PBKDF2(120k)+AES-GCM with user password | Drive only if switched on |
| Exports CSV/XLSX/PDF | SAF destination or `cache/share` | plain (user's choice) | User action |
Matches spec §7/§11 privacy statements, except the fail-open fallback and the plaintext cache copies.

## 5. Needs real phone
A11Y10 remove-animations; STAB4 Drive offline/online (needs OAuth client + release SHA-1); L10N2 Hindi WhatsApp text; contacts number pick (PERM row 8, confirm); fingerprint unlock; OEM battery killers for SMS receiver; WhatsApp deep link.

## 6. New feature ideas (not FAILs)
- Home-screen widget "Today spent / Add" (already "Later" in spec) — one-tap logging.
- Per-card statement PDF from "This cycle" — matches card bill in one share.
- Monthly auto-backup reminder when Drive is off — protects data without Google.
