# PageOne — build notes

Distribution: personal use

## Stack (Nov 2026)
AGP 9.3.2 (built-in Kotlin) · Kotlin 2.3.0 · KSP 2.3.4 · Gradle 9.9.2 · Compose BOM 2026.08.00 · Material 3 1.5.0-alpha22 (Expressive)
compileSdk 37 · targetSdk 36 · minSdk 31 · Room 2.8.5 · Navigation Compose · Biometric 1.1.0 · PDFBox-Android 2.0.27.0 (Apache 2.0) · R8 on
PDF engine: Android's own PdfRenderer (rendering; Android 15+ text, search, selection), PDFBox-Android for editing (merge, split, compress, save annotations, forms, outline).
No Google PDF Viewer code, no Foxit, no MuPDF.
Dependency wiring: Application holder (same as Tarang) instead of Hilt: Hilt's Gradle plugin isn't proven with AGP 9 in this repo yet.

## Permissions
| Permission | Why it's needed | When it's asked | If denied |
|---|---|---|---|
| MANAGE_EXTERNAL_STORAGE ("All files access") | List every PDF on the phone, grouped by folder; rename, delete, save edits in place | Setup guide step 2, or Home's "Allow access" card (opens Android's settings page) | Home shows an explanation + "Open a file" (system picker); PDFs from other apps still open; new files go to Download/PageOne |
| USE_BIOMETRIC | App lock (face, fingerprint or phone PIN via Android's own unlock sheet) | Never asked (normal permission); app lock is off until you turn it on | — |
INTERNET, ACCESS_NETWORK_STATE and WAKE_LOCK are removed from the merged manifest. Images for Merge come through the Photo Picker (no permission).

## Honest limits
- Text selection, search highlights and read-aloud highlighting need Android 15+ (PdfRenderer text APIs). On Android 12–14 search still finds pages (no highlight boxes).
- Highlights, drawings and notes are written into the page itself when saved (every PDF app shows them); notes also get a sticky-note popup.
- Rotated pages can't be annotated until turned upright.

## Signing
CI signs with debug.keystore unless the secrets PAGEONE_KEYSTORE_B64, PAGEONE_KEYSTORE_PASSWORD, PAGEONE_KEY_ALIAS, PAGEONE_KEY_PASSWORD exist (sent to Sushant on 27 Nov 2026). No release key is in git.
