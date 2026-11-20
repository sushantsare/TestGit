# Saveo (com.sushant.saveo)

Blueprint: `blueprint/app-spec.md` (source of truth), mockups in `blueprint/mockups/`, prototype `blueprint/prototype.html`.

## Permission table
| Permission | Why it's needed | When it's asked | If denied |
|---|---|---|---|
| Folder access (system folder picker, Android 11+) — not a permission | Read the WhatsApp `.Statuses` folder | "Connect" on the Connect WhatsApp screen (first run, or Settings › WhatsApp folders) | Statuses tab shows "Connect WhatsApp to start"; Saved tab works |
| READ_EXTERNAL_STORAGE (Android 8–10 only, maxSdk 29) | Read the WhatsApp status folder on old Android | "Allow access" on the Connect WhatsApp screen | Same as above; "Open settings" if blocked |
| WRITE_EXTERNAL_STORAGE (Android 8–9 only, maxSdk 28) | Save copies into Pictures/Saveo, Movies/Saveo | Together with the read permission above | Saving fails with a message |
| POST_NOTIFICATIONS (Android 13+) | "New status alerts" | When the user switches New status alerts on (after a one-line reason) | Alerts stay off; switch shows "Notifications are blocked · open settings" |
| USE_BIOMETRIC | App lock (fingerprint / face, PIN fallback) | Not a prompt; system sheet when App lock is turned on and when opening the app | Phone PIN works |
| WAKE_LOCK, RECEIVE_BOOT_COMPLETED | Added by WorkManager: 15-minute new-status check (only while alerts or auto-save are on) and the daily recycle-bin clean-up, resumed after restart | Not prompts (install-time) | — |

Removed: INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE (fully offline). Saving to the gallery uses MediaStore (no permission on Android 10+), sharing uses the share sheet.

## Notes
- Photo + music statuses are MP4 videos: detected by MIME type, saved as videos with sound.
- Duplicates: SHA-256 of content; the tick on Statuses uses name+size.
- Test hook: `Prefs.testDir` makes the scanner read `<dir>/<SOURCE>/` instead of WhatsApp (used by instrumented tests only; `LOST_<SOURCE>` marker simulates lost access).
