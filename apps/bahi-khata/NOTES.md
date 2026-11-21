# Bahi Khata (com.sushant.bahikhata)

Blueprint: `blueprint/app-spec.md` (source of truth, Distribution: personal, MASVS L2), design `design/DESIGN_SPEC.md`, mockups `design/mockups/`, prototype `blueprint/prototype.html`.
Stack (refreshed 21 Nov 2026): Gradle 9.7.1 · AGP 9.3.2 (built-in Kotlin) · Kotlin 2.3.0 · KSP 2.3.4 · Compose BOM 2026.08.00 · Room 2.8.3 + SQLCipher 4.6.1 · WorkManager 2.10 · compileSdk 37 / targetSdk 36 / minSdk 26.
Dependency wiring: manual container in `App` (decided instead of Hilt to keep the build simple; ~25 screens but one repository).

## Permission table
| Permission | Why it's needed | When it's asked | If denied |
|---|---|---|---|
| READ_SMS, RECEIVE_SMS | Turn bank / card / UPI SMS into suggestions; catch-up scan of the inbox | "SMS suggestions" screen after an explanation (from Settings, Setup step 4 or the Home SMS card) — never on launch | Manual entry keeps working; screen shows "Open settings" if blocked for good |
| POST_NOTIFICATIONS (Android 13+) | SMS "OK, save" notifications, daily reminder, card due and budget alerts | With the SMS request, or when a reminder switch is turned on | Switches show "Notifications are blocked · Allow" |
| USE_BIOMETRIC (+ USE_FINGERPRINT added by the biometric library for Android 8–8.1) | App lock with fingerprint / phone PIN | Not a prompt; system sheet on unlock | Phone PIN works; lock can't be turned on without a phone screen lock |
| INTERNET | Only the optional Google Drive backup | Not a prompt | — |
| WAKE_LOCK, RECEIVE_BOOT_COMPLETED, ACCESS_NETWORK_STATE | Added by WorkManager: repeating entries, reminders, SMS catch-up after reboot, Drive backup on Wi-Fi | Not prompts | — |
Removed: FOREGROUND_SERVICE, VIBRATE. Camera / gallery / files use system pickers (no permission).

## Notes
- Database encrypted with SQLCipher; 32-byte key wrapped by an Android Keystore AES key (`noBackupFilesDir/db.key`). Attachments encrypted with the same key in `filesDir/attachments`.
- Android backup off; own encrypted backup file (.bkb, PBKDF2 + AES-GCM) and Drive appDataFolder backup.
- Google Drive backup needs a one-time OAuth client (Google Cloud → OAuth client ID, Android, package com.sushant.bahikhata + release SHA-1); until that exists Google returns DEVELOPER_ERROR and the app says sign-in isn't available.
- Release signing: permanent key is NOT in the repo. CI signs with the test debug key unless `BAHIKHATA_KEYSTORE_*` env/secrets exist; delivered APKs are re-signed with the permanent key.
- SMS engine: receiver (instant) + ContentObserver + catch-up scans (open, boot, every 6 h); keys use 10-minute buckets so receiver and inbox copies are never doubled; unknown money SMS go to "Needs a look"; "Fix & teach" stores a learned format.
