# Bahi Khata — Audit round 3 (final independent re-audit)

App: `com.sushant.bahikhata` · versionName 1.0.0 / versionCode 1 · Distribution: personal (PLAY = N/A) · MASVS L2
Source audited: HEAD `1c5d994` · CI evidence: `/home/claude/result_r3` built from `1c5d994` (SOURCE_SHA matches HEAD, STATUS=BUILT).
Previous: round 1 D 52 % (`audit-1.0.0-2026-11-21-round1.md`), round 2 C 77 % (`audit-1.0.0-2026-11-22-round2.md`).
Auditor: independent agent (repo + CI outputs only; no repo files changed) · Date: 2026-11-22

## 0. Result

**Grade C · overall 84.8 % (95 PASS / 17 FAIL of 112 scored) · 0 Critical · 0 formal regressions** (round 2: C, 77 %, 86/112 → **+8 points**)

The grade stays C only because 84.8 % is just under the 85 % line for B. Fixing **one** item (AD2: no Add-entry button in phone landscape, see below) gives 96/112 = 85.7 % → **B**. Tagging the release at delivery (REL6) would add another point.

| Section | Round 1 | Round 2 | Round 3 | Δ since round 2 |
|---|---|---|---|---|
| BP Blueprint | 2/6 | 2/6 | 2/6 (33 %) | = |
| CQ Core quality | 8/11 | 10/11 | **11/11 (100 %)** | +1 (CQ5) |
| AD Adaptive | 4/9 | 7/9 | 8/9 (89 %) | +1 (AD3); AD2 still FAIL, new root cause |
| A11Y | 2/10 | 7/10 | **10/10 (100 %)** | +3 (A11Y4, A11Y8, A11Y11) |
| PERF | 3/7 | 4/6 | 4/6 (67 %) | = |
| STAB | 3/8 | 6/8 | 7/8 (88 %) | +1 (STAB2) |
| SEC | 12/19 | 18/19 | 18/19 (95 %) | = |
| PERM (rows) | 6/8 | 8/8 | 8/8 (100 %) | = |
| L10N | 0/3 | 0/3 | 0/3 (0 %) | = |
| UX | 6/10 | 8/10 | 9/10 (90 %) | +1 (UX5) |
| FORM | 1/5 | 3/5 | **5/5 (100 %)** | +2 (FORM3, FORM5) |
| VIS | 4/6 | 5/6 | 5/6 (83 %) | = |
| FUNC | 3/5 | 3/5 | 3/5 (60 %) | = |
| REL | 5/6 | 5/6 | 5/6 (83 %) | = |
| PLAY | N/A | N/A | N/A | — |

**Regression check (section 6):** every round-2 FAIL and every item in the same sections was re-checked. No item went PASS → FAIL.
**Side effect found (not a formal regression, but the most important finding of this round):** the round-2 AD2 fix (bottom bar instead of rail when height < 480 dp, AppRoot.kt:103) removed the only Add-entry button in phone landscape. Home and Entries hide their FAB whenever width ≥ 600 dp (Home.kt:68, Entries.kt:125) because they expect the rail to carry it (AppRoot.kt:130-132) — but the rail is no longer shown there. Evidence: `screens/audit_T14_landscape_home.png` shows a bottom bar and no FAB. Scored under AD2 (already FAIL).
**Process note (section 6 "every behaviour fix must come with a test"):** the round-2 fixes have no new tests except the walkthrough's discard-prompt check (WalkthroughTest.kt:58-61). Not covered: person delete + Undo (`Repo.deletePerson/restorePerson`), split-share > amount guard (AddEntry.kt:149), landscape navigation, export error snackbar. A landscape UI test would have caught the missing FAB.

---

## 1. Tool results (T1–T19)

| ID | Round 3 result | Evidence | Verdict | Δ since round 2 |
|---|---|---|---|---|
| T1 Code checks | Lint **0 errors, 44 warnings**: UseKtx ×32, ConfigurationScreenWidthHeight ×5 (AddEntry.kt:220, AppRoot.kt:100, AppRoot.kt:103 new, Entries.kt:125, Home.kt:68), ModifierParameter ×4 (Components.kt:127/148/180/193), NewerVersionAvailable ×2 (coroutines 1.10.2→1.11.0, serialization 1.9.0→1.13.0), ObsoleteSdkInt ×1. detekt/ktlint still not run; `lintDebug` not `lintRelease` | audit/lint-results-debug.txt | FAIL 🟢 (detekt/ktlint missing) | +1 warning (new height check) |
| T2 ATF | `AccessibilityTest.everyScreenPassesAtf` **ran on API 35 and passed**: 14 routes × light + dark with `enableAccessibilityChecks()` + `tryPerformAccessibilityChecks()` (errors throw) | ui-tests.log:8-22 (status 0), AccessibilityTest.kt:30-44, build.yml ui role api 35 | PASS | FAIL→PASS |
| T3 Monkey | 5000 events 0 crashes 0 ANRs; release monkey on 33/34/35 = 0 | audit/summary.txt, e2e-results-3x | PASS | = |
| T4 StrictMode | 9 log entries; **3 are the app** (pid 7277): `DiskReadViolation` in `getSharedPreferences` → `Prefs.<init>` (Prefs.kt:37, 38) from App.onCreate:40 (~131 ms). The other 6 are pid 1896 (contacts provider, a system process) — not the app. Round 2 had 11 app violations. Note: Prefs `read()` and `loadLibrary` were silenced with `allowThreadDiskReads` (Prefs.kt:40, Db.kt:329), i.e. still on the main thread by design, just not reported | audit/T4_strictmode.txt | FAIL 🟢 | 11 → 3 |
| T5 Leaks | 0 app leaks | summary.txt | PASS | = |
| T6 Startup | cold median **1129 ms**, warm 136 ms | summary.txt, T6_*.txt | PASS (≤ 1500 ms) | 874 → 1129 ms (emulator variance; still under target) |
| T7 Jank | 87 frames, 88.5 % janky — software-rendered CI emulator | T7_gfxinfo.txt | Needs real phone | = |
| T8 Memory | PSS 52,418 → 55,784 KB | summary.txt | PASS | = |
| T9 Process death | adb kill path: screen restored, `amount_kept=0` (adb key typing flaky on emulator). **`StateRestoreTest.typedAmountAndScreenSurviveRecreation` passed**: types ₹750 + "Chai point", `recreate()`, both restored | ui-tests.log (StateRestoreTest status 0), StateRestoreTest.kt:21-33 | PASS (real kill-from-Recents on phone still worth one check) | FAIL→PASS |
| T10 Offline | 0 crashes | summary.txt | PASS | = |
| T11 Battery modes | 0 crashes | summary.txt | PASS | = |
| T12 Upgrade | first release | — | N/A | = |
| T13 Large data | 5,000 rows, Entries cold open 1522 ms, 0 crashes/ANRs; search screenshot OK | summary.txt, audit_T13_search.png | PASS (search latency not timed) | 1336 → 1522 ms |
| T14 Config stress | 0 crashes; font200, night, landscape, tablet screenshots present. Landscape Home: no FAB (see AD2) | screens/audit_T14_* | PASS (layout finding under AD2) | = |
| T15 APK | v2 valid, 11,166,216 B, 8 .so, zipalign -P 16 exit 0 | T15_*.txt | PASS | = |
| T16 Roborazzi | not set up (personal app) | — | N/A | = |
| T17 Dependency scan | not set up | — | FAIL 🟢 | = |
| T18 Macrobenchmark / baseline profile | not set up | — | FAIL 🟢 | = |
| T19 Release smoke | release monkey ×3 APIs 0 crashes; SMS e2e PASSED on 33/34/35, `handled_once_total=5 (expected 5)` each | e2e-results-33/34/35.txt | PASS | = |

Other runs: unit tests BUILD SUCCESSFUL (LogicTest, SmsParserTest, BankCorpusTest 45 banks / 675 SMS); instrumented **16/16 OK on API 35** (incl. ATF and StateRestoreTest); walkthrough `problems=0`, 0 ignored (walk-log.txt:347-349), now also flags unexpected discard prompts.

---

## 2. Checklist

Severity: 🔴 Critical · 🟠 Major · 🟢 Minor. DS = design-safe fix, DC = design change. "Δ" = change since round 2.

### BP — Blueprint conformance (2/6)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| BP1 | FAIL 🟢 | Split-share guard added: "by amounts" shares > amount now blocks save with a message (AddEntry.kt:148-149). **Still missing:** add-entry as a side sheet on expanded width (spec) | DS: side sheet for `add` at ≥ 840 dp | = (one gap closed) |
| BP2 | FAIL 🟢 | Mockup differences unchanged (account tiles with type labels, separate Cash / Wallets sections vs mockup "Cash & wallets") | DS: align with mockups | = |
| BP3 | PASS | Routes match screen map (AppRoot.kt) | — | = |
| BP4 | FAIL 🟢 | Detail screens have spinner + "isn't here"; list screens still have no loading skeleton or error/Retry state | DS: skeleton while `loaded=false`; "Couldn't load · Retry" | = |
| BP5 | FAIL 🟢 | `LocalSpacing` tokens still unused; off-scale 6/10/14/18 dp values remain (e.g. Accounts.kt:323 `18.dp`, Settings.kt:197 `10.dp`) | DS: use spacing tokens | = |
| BP6 | PASS | Local Diagnostics only; crash log now loaded on IO (Settings.kt:458-459) | — | = |

### CQ — Core app quality (11/11)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| CQ1 | PASS | M3 components | — | = |
| CQ2 | PASS | BackHandlers + discard prompt; edit-mode dirty check now covers tags/UPI/repeat/splits (AddEntry.kt:127-131) | — | = |
| CQ3 | PASS | Launch route not replayed after restore | — | = |
| CQ4 | PASS | Lock after timeout | — | = |
| CQ5 | PASS | Category dialog name/icon/colour and recurring amount now `rememberSaveable` (Settings.kt:193-195, 250); remaining plain `remember` holds only loaded/derived state (AddEntry.kt:92-93, Entries.kt bitmaps, flags); StateRestoreTest green | — | FAIL→PASS |
| CQ6 | PASS | Light + dark complete; ATF ran on both | — | = |
| CQ7 | PASS | Edge-to-edge, bar icons themed, imePadding | — | = |
| CQ8 | PASS | Vector icons | — | = |
| CQ9 | PASS | Channels, private notifications | — | = |
| CQ10 | PASS | WorkManager; T11 0 crashes | — | = |
| CQ11 | N/A | Not media | — | = |
| CQ12 | PASS | `createChooser` | — | = |

### AD — Adaptive (8/9)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| AD1 | PASS | No locks | — | = |
| AD2 | FAIL 🟠 | Round-2 root (rail items clipped in landscape) fixed: short windows get the bottom bar (AppRoot.kt:103; audit_T14_landscape_home.png shows 5 tabs clearly). **New root, caused by that fix:** in phone landscape (and any window ≥ 600 dp wide and < 480 dp tall, e.g. split screen) there is **no Add-entry button** on Home or Entries — their FAB is shown only when width < 600 (Home.kt:68, Entries.kt:125), the rail that carries the FAB (AppRoot.kt:130-132) is now hidden. The app's main action is only reachable by rotating, Ctrl+N or the launcher shortcut | DS (one line each): show the screen FAB when the rail isn't shown — pass `rail` down (e.g. a `LocalRail` composition local) and use `if (!rail)` instead of `screenWidthDp < 600`; add a landscape UI test that asserts `fab_add` or `rail_add` exists | 🟠 = (new root cause) |
| AD3 | PASS | Same as CQ5 | — | FAIL→PASS |
| AD4 | PASS | ImeAction.Next, Enter = Next; KeyBus now also takes Enter/period | — | = |
| AD5 | PASS | Bottom bar (compact or short height), rail ≥ 600 dp wide and ≥ 480 dp tall. Bottom bar for compact-height windows is an accepted choice | — | = |
| AD6 | PASS | Two panes ≥ 840 dp (landscape Home shows two panes) | — | = |
| AD7 | PASS | Forms/non-tab screens max widths | — | = |
| AD8 | PASS | Ctrl+N, Ctrl+F, Esc | — | = |
| AD9 | PASS | Handwriting not disabled | — | = |
| AD10 | N/A | Report only | — | = |

### A11Y (10/10 scored)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| A11Y1 | PASS | Contrast; ATF (includes contrast check) passed light + dark | — | = |
| A11Y2 | PASS | 48 dp targets; ATF touch-target check passed | — | = |
| A11Y3 | PASS | Labels; recurring switch now "Keep repeating" (Settings.kt:244) | — | = |
| A11Y4 | PASS | Recurring row: row = edit, switch = pause — two different actions, so two focus stops is correct; the switch now has its own label (Settings.kt:243-244). Other rows merged | — | FAIL→PASS |
| A11Y5 | PASS | Headings | — | = |
| A11Y6 | PASS | Tab/radio roles | — | = |
| A11Y7 | PASS | Live regions | — | = |
| A11Y8 | PASS | Font 200 % (audit_T14_font200.png): all five bar labels fully visible ("Home Entries Accounts Udhar Reports") via `BarLabel` (AppRoot.kt:242-247). Add step 1 (audit_T14_font200_add.png): pills now `FlowRow` (AddEntry.kt:244); "Withdraw cash" wraps to a second line inside the scrollable top area (AddEntry.kt:223) — reachable by scrolling, not clipped. Polish: at 200 % the second pill sits right at the keypad edge with no scroll hint; nav labels are capped at ~1.3× growth (deliberate) | (polish: a fading edge / smaller gap so the wrapped pill is visibly scrollable) | FAIL→PASS |
| A11Y9 | PASS | Text + colour | — | = |
| A11Y10 | Needs real phone | — | — | = |
| A11Y11 | PASS | T2 ran on API 35: 0 errors over 14 screens × 2 themes | — | FAIL→PASS |

### PERF (4/6 scored)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| PERF1 | PASS | Cold 1129 ms, SplashScreen | — | = |
| PERF2 | Needs real phone | Software-rendered emulator | Measure on phone | = |
| PERF3 | FAIL 🟢 | Improved: Diagnostics counters on a single background executor (Diagnostics.kt:32-45), crash log read on IO (Settings.kt:458-459), export work on IO. **Remaining:** T4 still shows 3 app main-thread disk reads in `getSharedPreferences` (Prefs.kt:37-38); `read()` and `System.loadLibrary("sqlcipher")` still run on main but are hidden from StrictMode with `allowThreadDiskReads` (Prefs.kt:40, Db.kt:329) — reasonable for theme/lock before the first frame, but not "T4 clean" | DS: create Prefs + DB on `Dispatchers.IO` behind the splash screen (it already waits for `loaded`) so no suppression is needed; at minimum wrap the two `getSharedPreferences` calls too | 🟢 = (11 → 3 violations) |
| PERF4 | PASS | Downscaled bitmaps, 0 leaks | — | = |
| PERF5 | PASS | No wake locks/FGS | — | = |
| PERF6 | PASS | R8 + shrink; 11.2 MB; T19 pass | — | = |
| PERF7 | FAIL 🟢 | No app Baseline Profile / T18 | DS: baselineprofile module + Macrobenchmark | = |

### STAB (7/8 scored)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| STAB1 | PASS | 0 crashes/ANRs in every run | — | = |
| STAB2 | PASS | Export Save/Share wrapped in `runCatching` + "Couldn't make the file. Please try again." (Settings.kt:420-430); route allow-list, attachment `runCatching`, guarded `vm.launch` unchanged. (Polish: `runCatching` inside a coroutine also swallows `CancellationException`) | — | FAIL→PASS |
| STAB3 | PASS | appScope for long work | — | = |
| STAB4 | Needs real phone | Drive OAuth | — | = |
| STAB5 | FAIL 🟢 | Backup still built fully in memory and restore reads whole file + plaintext into memory (Backup.kt:44-61) → OOM risk with many receipts | DS: stream zip → cipher → SAF (and reverse) | = |
| STAB6 | PASS | T13 5,000 rows 1.5 s, 0 ANR | — | = |
| STAB7 | PASS | Zone read dynamically | — | = |
| STAB8 | PASS | Schema exported, v1 | — | = |
| STAB9 | PASS | Setup + empty states | — | = |

### SEC (18/19)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| SEC-STORAGE1 | PASS | SQLCipher fail-closed, encrypted attachments | — | = |
| SEC-STORAGE2 | PASS | Release strips Log.v/d/i | — | = |
| SEC-STORAGE3 | PASS | allowBackup=false | — | = |
| SEC-STORAGE4 | PASS | Private / SAF only | — | = |
| SEC-CRYPTO1 | PASS | SecureRandom, AES-GCM, PBKDF2 | — | = |
| SEC-NET1 | PASS | Cleartext off | — | = |
| SEC-NET2 | PASS | Default TLS | — | = |
| SEC-PLAT1 | PASS | Explicit exported | — | = |
| SEC-PLAT2 | PASS | FLAG_IMMUTABLE | — | = |
| SEC-PLAT3 | PASS | FileProvider private | — | = |
| SEC-PLAT4 | N/A | No WebView | — | = |
| SEC-PLAT5 | PASS | Route allow-list | — | = |
| SEC-PLAT6 | PASS | FLAG_SECURE (MainActivity.kt:65), locked UI hidden, tapjacking guard | — | = |
| SEC-CODE1 | PASS | Release not debuggable | — | = |
| SEC-CODE2 | PASS | Room; CSV guard | — | = |
| SEC-CODE3 | FAIL 🟢 | Lint still lists coroutines 1.11.0 and serialization 1.13.0; T17 not set up | DS: bump both; add OWASP dependency-check | = |
| SEC-CODE4 | PASS | Verified: only `apps/bahi-khata/debug.keystore` tracked; release signing reads `BAHIKHATA_KEYSTORE_PASSWORD` / `BAHIKHATA_KEY_PASSWORD` from env (app/build.gradle.kts:24-41), falls back to debug key in CI; no passwords in Gradle/properties. (Outside this app: other apps in the same repo commit release `.jks` files — grabbit, gym-manager, inknote, lekhak, saveo, speed-tile — worth checking in their own audits) | — | = |
| SEC-LEVEL | PASS | L2 met | — | = |
| SEC-PRIV1 | PASS | §3 | — | = |
| SEC-PRIV2 | PASS | No clipboard | — | = |

### PERM (8/8 rows) — unchanged from round 2 (no manifest changes in round-2 fixes)
| Permission | Q1 | Q2 | Q3 | Q4 | Q5 | Q6 | Overall | Note |
|---|---|---|---|---|---|---|---|---|
| READ_SMS, RECEIVE_SMS | ✅ | ✅ | ✅ | ✅ | — | ✅ | ✅ | polish: `denied` not re-checked on resume (Sms.kt) |
| POST_NOTIFICATIONS | ✅ | ✅ | ✅ | ✅ | — | ✅ | ✅ | — |
| USE_BIOMETRIC | ✅ | ✅ | n/a | ✅ | — | ✅ | ✅ | — |
| USE_FINGERPRINT | ✅ | ✅ | n/a | ✅ | lib | ✅ | ✅ | — |
| INTERNET | ✅ | ✅ | n/a | n/a | — | ✅ | ✅ | — |
| WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED | ✅ | ✅ | n/a | n/a | WorkManager | ✅ | ✅ | — |
| DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION | ✅ | ✅ | n/a | n/a | androidx.core | ✅ | ✅ | — |
| Contacts number pick (no permission) | ✅ | ✅ | ✅ | ✅ | — | ✅ | ✅ | confirm on phone |

### L10N (0/3)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| L10N1 | FAIL 🟠 | All UI text hard-coded (owner chose English-only UI) | DS: move to `strings.xml`, or record the owner's waiver in app-spec.md so the item can be N/A | = |
| L10N2 | Needs real phone | Hindi only in WhatsApp text | — | = |
| L10N3 | FAIL 🟢 | No en-XA run (font-200 now OK, which lowers the risk) | DS: pseudo-locale walkthrough | = |
| L10N4 | FAIL 🟢 | Hand-built plurals | DS: `pluralStringResource` | = |
| L10N5 | N/A | Report only | — | = |

### UX (9/10)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| UX1 | PASS | Progress, snackbars | — | = |
| UX2 | PASS | User's words | — | = |
| UX3 | FAIL 🟢 | Fixed: delete person now offers Undo that restores person, udhar lines and their entries (Repo.kt:187-202, Udhar.kt:225). **Remaining:** "Delete account" removes the account and all its entries with confirm only, no Undo (Accounts.kt:241-242, Repo.kt:47-50) — the dialog does point to Archive | DS: snapshot account + entries (+ attachments) and offer Undo, same pattern as person | 🟢 = (half fixed) |
| UX4 | PASS | Consistent (see AD2 for the landscape FAB gap) | — | = |
| UX5 | PASS | Recurring Save disabled until amount > 0, label "Amount *" with inline error (Settings.kt:251-252); account Save disabled with reason | — | FAIL→PASS |
| UX6 | PASS | Chips, pickers | — | = |
| UX7 | PASS | Shortcuts, bulk OK | — | = |
| UX8 | PASS | Clean | — | = |
| UX9 | PASS | Plain messages incl. new export / split messages | — | = |
| UX10 | PASS | Helper text | — | = |

### FORM (5/5)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| FORM1 | PASS | Validate on blur | — | = |
| FORM2 | PASS | Errors under fields | — | = |
| FORM3 | PASS | Edit dirty check includes normalised tags, UPI, repeat, split changes (AddEntry.kt:127-131); untouched edits no longer prompt (walkthrough flags unexpected prompts, problems=0); `TextDialog` ignores outside taps once text changed (AddEntry.kt:501-502). (Polish: Back key still closes the text dialog) | — | FAIL→PASS |
| FORM4 | PASS | Keyboards, capitalisation | — | = |
| FORM5 | PASS | Account Save disabled with visible reason "Add a name to save this account" / "Bill and due dates must be days 1–31" (Accounts.kt:323-325); other dialogs use "Name *" / "Amount *"; account form marks the optional fields "(optional)" instead — acceptable convention | (polish: "*" on account name for consistency) | FAIL→PASS |

### VIS (5/6)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| VIS1 | FAIL 🟢 | Off-scale dp values remain (BP5); new code adds `18.dp` (Accounts.kt:324) | DS | = |
| VIS2 | PASS | Type scale (BarLabel scales from labelMedium) | — | = |
| VIS3 | PASS | Colour roles | — | = |
| VIS4 | PASS | Short fades | — | = |
| VIS5 | PASS | No clutter | — | = |
| VIS6 | PASS | imePadding, FAB bottom padding | — | = |

### FUNC (3/5)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| FUNC1 | PASS | Search/filter/calendar | — | = |
| FUNC2 | FAIL 🟢 | List loading/error states missing (BP4) | DS | = |
| FUNC3 | FAIL 🟢 | Account delete without Undo (UX3); no change history for edited entries | DS | = (person undo added) |
| FUNC4 | PASS | Encrypted backup, exports, Drive | — | = |
| FUNC5 | PASS | Recurring, reminders, shortcuts | — | = |

### REL (5/6)
| ID | Verdict | Evidence | Fix | Δ |
|---|---|---|---|---|
| REL1 | PASS | targetSdk 36; UI tests now on API 35 | — | = |
| REL2 | PASS | min 26 / compile 37 / vc 1 / 1.0.0 | — | = |
| REL3 | PASS | v2 valid; CI debug-signed, permanent key at delivery | keep the same key for every update | = |
| REL4 | PASS | 16 KB aligned | — | = |
| REL5 | PASS | About: Sushant Kumar · sushant.sare@gmail.com | — | = |
| REL6 | FAIL 🟢 | CHANGELOG.md exists; no tag `bahi-khata-v1.0.0` yet | DS: tag at delivery | = |

### Extra (unscored)
- X4 KeyBus fixed: keys are consumed only when a screen is collecting (`subscriptionCount > 0`, MainActivity.kt:102).
- X5 🟢 Lint ConfigurationScreenWidthHeight now ×5 (new AppRoot.kt:103). Moving all size checks to one `currentWindowAdaptiveInfo()` value would also fix AD2 cleanly (one source of truth for "rail shown").
- X6 🟢 Person-delete Undo restores rows but not attachment files of deleted udhar entries if any (they're deleted via `dao.deleteEntry`, not `deleteEntryInternal`) — low impact.

---

## 3. Data inventory
Unchanged since round 2 (no storage changes in round-2 fixes): Room DB under SQLCipher (raw 256-bit key wrapped by Keystore AES-GCM, fail closed); receipts AES-GCM in `filesDir/attachments`; settings in private prefs; Drive password AES-GCM in `device_local`; diagnostics counters/crash log private; backups PBKDF2 + AES-GCM; exports plain by user choice. Matches spec privacy statements.

## 4. Needs real phone
PERF2/T7 jank (CI emulator renders in software); T13 search latency and scroll with 5,000 rows; A11Y10 remove animations; STAB4 Drive offline/online (OAuth client + release SHA-1); L10N2 Hindi WhatsApp text; contact number pick; fingerprint/PIN unlock; OEM battery killers vs SMS receiver; WhatsApp deep link; T9 real kill-from-Recents with a typed amount (recreate path proven by StateRestoreTest; adb typing flaky on emulator); phone-landscape Add entry after the AD2 fix.

## 5. Remaining FAIL list (17)
🔴 Critical (0)
🟠 Major (2): AD2 no Add-entry button in phone landscape (side effect of round-2 fix) · L10N1 hard-coded strings (owner's English-only choice)
🟢 Minor (15): BP1 tablet side sheet · BP2 mockup diffs · BP4 list loading/error · BP5 spacing tokens · PERF3 3 start-up prefs reads on main · PERF7 baseline profile · STAB5 in-memory backup · SEC-CODE3 two libs + T17 · L10N3 en-XA · L10N4 plurals · UX3 account delete undo · VIS1 spacing · FUNC2 list states · FUNC3 account undo / edit history · REL6 tag.

## 6. Change since round 2 (summary)
Fixed (9 items FAIL→PASS): CQ5, AD3, A11Y4, A11Y8, A11Y11, STAB2, UX5, FORM3, FORM5 (+ T2 and T9 now PASS). Improved but still FAIL: PERF3 (11 → 3 violations), UX3/FUNC3 (person undo added), BP1 (split guard added), AD2 (old clipping fixed, new missing-FAB root). No PASS → FAIL regressions; 0 Critical. Score 77 % → 84.8 %, grade C (0.2 points below B).
