# Gallery (com.sushant.gallery)

Blueprint: `blueprint/app-spec.md` (source of truth, Distribution: personal), design `design/DESIGN_SPEC.md`, mockups `design/mockups/`, prototype `blueprint/prototype.html`.

## Permission table
| Permission | Why it's needed | When it's asked | If denied |
|---|---|---|---|
| MANAGE_EXTERNAL_STORAGE ("All files access", Android 11+) | Show photos in every folder; move to Recycle Bin, restore, move/copy, rotate, edit and hide without a system popup for every file (core of the flick-to-delete filmstrip) | Welcome screen → "Allow access" opens the system switch (never on launch without explanation) | "Gallery can't see your photos" screen with "Open settings"; photos opened from other apps still work |
| READ/WRITE_EXTERNAL_STORAGE (Android 8–10 only, maxSdk 29) | Same as above on old Android | Welcome screen → "Allow access" | Same as above |
| USE_BIOMETRIC | Fingerprint unlock for the Hidden album | Not a prompt; system sheet when opening the Hidden album (if "Unlock with fingerprint" is on) | PIN works |
| SET_WALLPAPER | Declared for "Set as wallpaper" hand-off | Not a prompt (install-time) | — |
| WAKE_LOCK, RECEIVE_BOOT_COMPLETED | Added by WorkManager: daily Recycle Bin clean-up (items older than 30 days), resumed after restart | Not prompts | — |

Removed: INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE, VIBRATE (fully offline).

## Notes
- Recycle Bin = `/storage/emulated/0/.gallery_trash/` (with `.nomedia`); index in Room table `trash`; daily purge after 30 days.
- Hidden album: AES-256-GCM in 1 MB chunks, random data key wrapped by an Android Keystore key, PIN stored as PBKDF2 (120k) hash; excluded from backups; locks when the app goes to the background; FLAG_SECURE on vault screens.
- Release signing: permanent key is NOT in the repo. CI signs with the test debug key unless `GALLERY_KEYSTORE_*` env/secrets exist; delivered APKs are re-signed with the permanent key.
