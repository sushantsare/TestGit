# PageOne — app spec

Distribution: personal

## 1. Vision brief
- **Name:** PageOne · package `com.sushant.pageone`
- **Pitch:** a fast, private, fully offline PDF reader and editor that works like Google PDF Viewer, adds a real home with Home · Recent · Starred tabs, and is pure Material 3 Expressive.
- **User:** Sushant (exam aspirant): study books, PYQs, forms, PDFs arriving via WhatsApp/Telegram.
- **Success:** a WhatsApp PDF opens in < 1 s; smooth scroll on 500+ page books; any PDF on the phone found fast; every element M3 Expressive.

## 2. Features
### v1
**Library**
- MediaStore + All-files scan of every PDF, grouped by folder; folder list → folder → PDF list (list format).
- Home top: "Continue reading" card (last PDF, progress), storage summary (count · total size).
- File-name search across all folders (search bar in the top app bar).
- Sort: name / date modified / size, ascending/descending; remembered per screen.
- Row details (each toggle in Settings): first-page thumbnail, page count, size + date, reading-progress bar.
- Recent tab: last-opened time, progress, swipe to remove (file untouched), Clear all (confirm), auto-clean of missing files.
- Starred tab: star/unstar from any row or the viewer; manual order by drag.
- Duplicate PDF finder (same content hash) with keep/delete choices (delete → system trash where available, confirm).
- Reading position keyed by content fingerprint, so copies of the same PDF share last page/zoom.
- Expressive FAB menu on Home: Merge PDFs & images (mixed merge: PDFs and images in one list, any order, each image becomes one page) (also covers images-only → PDF), Split/extract, Compress.

**Viewer**
- Opens `ACTION_VIEW`/`ACTION_SEND` `application/pdf` (content:// and file://); added to Recent.
- Vertical continuous scroll (default) / page-by-page switch; pinch zoom; double-tap zoom.
- Progressive tile rendering (low-res first, sharp after); parsing out-of-process (androidx.pdf document service).
- Resume last page + zoom.
- Fast-scroll handle with page bubble; go-to-page dialog; page thumbnails strip/grid; table of contents (outline); bookmarks.
- Find: highlight all, "3 of 12", whole word, match case, results list with page numbers, search history (clearable).
- Text select → Copy (clipboard; explicit user exception), Highlight, Underline, Share text, Read aloud from here.
- Reading modes: night (invert), sepia, reflow (text PDFs; scanned pages fall back to image), crop white margins, rotate page, orientation lock, keep screen on, full-screen (tap toggles bars), volume keys turn pages.
- Read aloud: Android TextToSpeech, offline voices; play/pause/next page; follows page.
- Annotations: highlight, underline, pen (androidx.ink), eraser, text note/comment; undo/redo.
- Forms: fill AcroForm fields; signature (draw, saved locally for reuse).
- Save dialog: Save / Save as copy / Discard.
- Password PDFs: prompt every time, never stored.
- ⋮ menu: Share, Open with, Print (Android print framework), Rename, Delete, Details, Save page as image (PNG/JPG), page tools (same 4 tools on this file).
- Tablet/landscape: two-page spread option.

**App**
- Bottom bar: Home · Recent · Starred; Settings via top-bar action.
- Settings: theme (system/light/dark), pure black, dynamic colour, row details toggles, default scroll mode, keep screen on, volume keys, app lock (Android system unlock: face, fingerprint or phone PIN/pattern via BiometricPrompt, BIOMETRIC_STRONG | BIOMETRIC_WEAK | DEVICE_CREDENTIAL — whichever the user uses first; no separate app PIN), clear search history, About. Diagnostics (crash log view/share) is hidden: tap the version line in About 5 times.
- Launcher shortcuts: Continue last PDF, Search, Starred.
- First-launch animation + step-by-step setup guide (permission, tabs, viewer gestures, tools); fresh install only.
- Local crash handler + Diagnostics; local usage counters only.
- About: Sushant Kumar · sushant.sare@gmail.com.

### Later
- Home-screen widget; reading stats; quick folder chips; Hindi UI.
### Out of scope
- Internet of any kind, accounts, cloud, ads, other file types, remembered passwords.

## 3. Screen map
```
Splash/first-launch animation → Setup guide (first install) → Main
Main (bottom bar)
 ├ Home ─ Folder → PDF list → Viewer
 │   ├ Search → results → Viewer
 │   ├ FAB menu → Merge PDFs & images | Split | Compress → Result
 │   └ Duplicates finder
 ├ Recent → Viewer
 ├ Starred → Viewer
 └ Settings → Row details · Reading · Appearance · App lock · Diagnostics · About
Viewer ─ Find · Thumbnails · TOC/Bookmarks · Annotate toolbar · Forms · Read aloud · Reflow · ⋮ menu → Details/Rename/Save page/Print/tools
External VIEW intent → (App lock?) → Viewer
App lock screen (on open when enabled)
```

## 4. Screen specs (states for each: empty, loading, error, permission denied)
- **Home:** large flexible top app bar "PageOne" + search + settings; Continue-reading card; storage summary; folder list (folder icon, name, PDF count, size); FAB menu bottom-right. Empty: "No PDFs found" + Open a file. Permission denied: card explaining + Grant + Open a file. Loading: M3 Expressive LoadingIndicator.
- **Folder:** medium top app bar (folder name, sort, search); PDF rows per Settings toggles; long-press → multi-select (share, star, delete).
- **Recent:** list grouped Today / Yesterday / Earlier; swipe to remove with Undo snackbar; Clear all in top-bar menu. Empty illustration.
- **Starred:** list, drag handle to reorder. Empty: "Star PDFs to keep them here".
- **Viewer:** floating back button in a circle; PDF title + page as plain text over a soft top fade; search | ⋮ as a split pill (connected button group); all auto-hide on tap; page indicator pill; fast-scroll handle; horizontal floating toolbar bottom (thumbnails, modes, read aloud, annotate) with edit FAB; no navigation bar here. Error: corrupt/unsupported file card. Password dialog.
- **Annotate mode:** floating toolbar swaps to tools (highlight, underline, pen, eraser, note, undo, redo, done); colour/size picker as bottom sheet.
- **Find:** search field in top bar, match count, prev/next, options chips, results bottom sheet.
- **Tools:** each a full screen with file picker list, options, ButtonGroup, progress (wavy), result card with Open/Share.
- **Duplicates:** groups of identical files, keep one, delete rest (confirm).
- **Settings:** grouped lists with M3 switches; Diagnostics; About.
- **Setup guide:** 4–5 steps, pager, Expressive shapes and motion.

## 5. Design system
Pure Material 3 Expressive (MaterialExpressiveTheme), calm Google-native look; style **Sage** (seed #5F8D6A, tonal spot) as the fixed fallback scheme; dynamic colour on by default; light / dark / pure black; Roboto Flex; Material Symbols Rounded; subtle Expressive shapes (cookie, clover, sunny, flower, burst) for icon containers and illustrations. Full tokens: `design/DESIGN_SPEC.md` (written by android-ui-designer). App icon: 3 · Expressive cookie (design/icon/app-icon.svg).

## 6. Adaptive layout
- Compact: bottom nav bar, single pane; viewer full screen.
- Medium: navigation rail; folder + list two columns where useful; viewer optional two-page spread in landscape.
- Expanded: rail; list-detail (folders | PDFs); max content width 840dp for lists; viewer two-page spread.
- State survives rotation, split screen, resizing; keyboard shortcuts in viewer (Ctrl+F, arrows, PgUp/PgDn, +/-), mouse wheel and hover.

## 7. Quality standards
- Accessibility: TalkBack labels, 4.5:1 contrast, 48dp targets, 200% font.
- Localization: all strings in resources (English).
- Privacy: no INTERNET permission; data stays on device (Room DB of library index, recents, stars, bookmarks, positions, annotations metadata, signature image); passwords never stored; app lock via BiometricPrompt with device credential (no app PIN stored).
- Performance: cold start < 1.5 s; external PDF first page < 1 s; 60/120 fps scroll; incremental library scan in background with WorkManager.
- Reliability: plain-word errors + retry; Room migrations planned; never overwrite original without the Save dialog.
- Testing: every screen on phone, tablet, landscape; Android 17 and 16 emulators only.
- Release: versionName 1.0.0 / versionCode 1; target SDK 36; signing keys in GitHub secrets.

## 8. Analytics & crash
Local crash handler → log file; Diagnostics screen view/export; local counters only. No Firebase.

## 9. Technical plan
- Kotlin, Jetpack Compose, Material 3 Expressive (latest material3), MVVM, Hilt (> 10 screens), Navigation Compose, Room, DataStore, WorkManager, Coil (thumbnails), androidx.pdf (viewer, document service, ink annotations), androidx.ink, BiometricPrompt, TextToSpeech, PdfDocument / PDFium for merge (PDFs + images)/split/compress.
- minSdk 31, targetSdk 36. Where androidx.pdf's SDK-extension features are missing, fall back to PDFium-based rendering.
- Permissions: MANAGE_EXTERNAL_STORAGE (explained first), no media permission: images for Merge come through the Android Photo Picker, USE_BIOMETRIC, no INTERNET.
- Security: MASVS L1 (+ app lock, FLAG_SECURE optional when lock on).
- Room tables: Pdf(id, uri, path, name, folder, size, modified, pages, hash), Folder, Recent(pdfHash, openedAt), Star(pdfHash, order), Position(pdfHash, page, zoom, offset), Bookmark(pdfHash, page, label), SearchHistory(query, at), Signature.

## 10. Fixed rules
Full-fledged app; About = Sushant Kumar · sushant.sare@gmail.com; clipboard only for "Copy selected text" (explicit request); first-launch animation + setup guide on fresh install only; build only after his explicit yes.

Mockups: ../design/mockups/ · Clickable prototype (artifact): https://claude.ai/artifact/9AEE9JJXorDinGsgfsat4m
