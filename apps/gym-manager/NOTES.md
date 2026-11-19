# Gym Manager — app notes

Offline gym management app (Kotlin, Jetpack Compose, Room). Package `com.sushant.gymmanager`.

## Permissions (v2.4)

| Permission | Why it's needed | When it's asked | If denied |
|---|---|---|---|
| CAMERA | Read members' QR ID cards for check-in | When you tap the Scan button on Attendance, after a one-line explanation | Scanning is off with a clear message; tick members by hand. "Open Settings" if permanently denied |
| POST_NOTIFICATIONS (Android 13+) | One morning summary (expiring plans, pending dues) | From a card on the Dashboard or the switch in Settings, never at app start | Settings shows "Notifications are blocked" with an Allow button; re-checked on resume |
| WRITE_EXTERNAL_STORAGE (Android 8–9 only, `maxSdkVersion=28`) | Copy backups and PDF reports into Downloads/GymManager | Right before the first save to Downloads | File is still created and shared; only the Downloads copy is skipped |
| USE_BIOMETRIC / USE_FINGERPRINT | Optional app lock (falls back to the phone's PIN) | Not a runtime prompt | — |
| WAKE_LOCK, RECEIVE_BOOT_COMPLETED (WorkManager) | Daily job (renewals, backup, summary) keeps running after a restart | Not a runtime prompt | — |

Removed: VIBRATE (unused), ACCESS_NETWORK_STATE and FOREGROUND_SERVICE (added by WorkManager, not needed offline). No INTERNET.
Zero-permission paths: Photo Picker for member photos, MediaStore for Downloads on Android 10+, file picker for restore and CSV import, share sheet for receipts/reports, intents for WhatsApp/call/email.

## Data

- Room schema v3 (v2 → v3 adds waived dues and a receipt counter). Migrations 1→2→3 are tested.
- Receipt numbers come from `gym_settings.lastReceiptNo` and only go up.
