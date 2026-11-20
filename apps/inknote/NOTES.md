# InkNote (com.sushant.inknote)

Blueprint: `blueprint/app-spec.md` (source of truth), mockups in `blueprint/mockups/`, prototype `blueprint/prototype.html`.

## Permission table
| Permission | Why it's needed | When it's asked | If denied |
|---|---|---|---|
| RECORD_AUDIO | Record lectures / voice notes into a note | First tap on + › Voice recording (after a one-line reason) | Recording unavailable; "Open settings" if blocked; everything else works |
| POST_NOTIFICATIONS (13+) | Shows the "Recording" notification with Stop | Once, the first time a recording starts | Recording still works; no notification shown |
| FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE | Keep recording with the screen off | Not a prompt (install-time) | — |
| USE_BIOMETRIC | Fingerprint / face unlock for locked notes | Not a prompt; system sheet when opening a locked note | PIN pad always works |
| INTERNET | One-time download of the offline English handwriting pack (ML Kit) | Not a prompt; only when the user taps Download pack | Handwriting→text and handwriting search unavailable; all else offline |
Zero-permission paths: photos via Photo Picker, camera via the camera app (TakePicture), files via the system file picker (SAF), saving exports via the system Save screen, sharing via the share sheet.

## Deviations from the spec (stack limits)
- Ink engine is a custom low-latency View (not androidx.ink) and DI is manual (no Hilt), per the proven build stack (compileSdk/targetSdk 35).
- Audio files and PDFs inside locked notes stay in app-private storage unencrypted; page content and images are encrypted.
