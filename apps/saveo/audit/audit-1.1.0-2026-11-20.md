# Saveo 1.1.0 — Audit scorecard (2026-11-20)

**Grade: A · 100% of applicable checks pass** · change since 1.0.0: no regressions.

Change audited: hidden saves (private vault by default), eye button (move to/from Gallery), "In gallery" filter, viewer eye, multi-select Show/Hide, Settings "New saves go to" + "Show all in gallery", one-time info sheet, `hasFragileUserData`, Room v1 → v2 migration.

## Automated suite
T1 lint 0 errors (1 warning: targetSdk 35) · T3 monkey 0 crashes / 0 ANRs (API 33/34/35) · T4 StrictMode 0 · T5 leaks 0 · T6 cold 1.09 s / warm 0.19 s · T7 release 0 % janky (viewer swipe: needs real phone) · T8 60 → 72 MB stable · T9–T11, T14 no crashes · T12 Upgrade: **MigrationTest** v1 → v2 keeps all rows, marks 1.0.0 items visible · T13 5,000 files scanned < 300 ms · T15 v2 signature (same key as 1.0.0, installs over it), 16 KB aligned, 15.6 MB.

## New tests
- RepositoryTest.newSavesAreHiddenAndEyeMovesThem — new save lives in private vault (not MediaStore); eye → moves into MediaStore and deletes vault file; eye again → back to vault, Gallery copy removed, same hash.
- RepositoryTest.gallerySettingSavesVisible — "New saves go to: Gallery" saves straight to MediaStore.
- RepositoryTest.binRestoreKeepsHiddenState — delete + restore keeps the item hidden.
- MigrationTest.v1ToV2KeepsDataAndMarksItVisible.
- Walkthrough: first-save info sheet, eye toggle, In gallery filter (asserts only gallery items shown), viewer eye.

## Found and fixed during this build
| ID | Problem | Fix |
|---|---|---|
| FUNC / UX4 | "Select" text button on Statuses re-wired by a trailing-lambda mix-up → app opened in selection mode | Passed the action by name; walkthrough would fail if it returns |
| VIS5 | Saved filter row too crowded ("In g…") | Sort / date buttons moved beside the first date header |
| A11Y1 | Eye icon on the selected "In gallery" chip had no contrast | Selected icon colour = chip text colour |
| UX1 | Saved tab count showed the filtered count | Shows total saved |
| L10N1 | Unused string | Removed |

## Data inventory (updated)
Hidden saves → `files/vault/` (private; excluded from backup; deleted on uninstall unless the user keeps app data — Android 10+ asks because of `hasFragileUserData`; Settings › Show all in gallery moves everything out). Visible saves → Pictures/Saveo, Movies/Saveo. Nothing leaves the phone except what the user shares.

## Permissions
Unchanged from 1.0.0 (see audit-1.0.0). Moving files into and out of the Gallery needs no permission on Android 10+ (Saveo's own files).

## Needs real phone
Eye on items saved by 1.0.0 after a reinstall (Android may show "Allow Saveo to delete?" once — handled), "Keep app data?" prompt on uninstall (Android 10+), plus the 1.0.0 list (real WhatsApp folder, repost, fingerprint, alerts, swipe smoothness).
