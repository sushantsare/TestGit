# InkNote — App Spec (source of truth)

## 1. Vision brief
- **Name:** InkNote · package `com.sushant.inknote`
- **Pitch:** A free, fully offline Samsung Notes–style notebook for any Android phone or tablet — handwriting, typing, lecture recordings synced to writing, PDF markup — that stays fast even with huge notes.
- **Users:** Sushant + public (Play Store / shared APK). Students are the main audience (lectures, PYQ PDFs, study notes).
- **Success goals:** new note in 1 tap from home; pen latency feels instant (≤ 1 frame prediction, low-latency front-buffer rendering); 200-page note scrolls smoothly; cold start < 1.5 s; nothing ever lost (autosave, Trash, backup).

## 2. Features
### v1 (build now)
**Writing & drawing**
- Pens: ballpoint, fountain (pressure), pencil (texture), brush; highlighter (draws under ink); stroke eraser + area eraser; 16-colour palette + custom colour picker; thickness + opacity; 5 saved pens.
- Pressure + tilt from stylus (MotionEvent), palm rejection; **stylus-only mode** (finger scrolls/zooms, only stylus draws; auto-enable when a stylus is first detected, toggle in settings + note menu).
- **Shape auto-correct:** draw and hold ~600 ms → line, arrow, circle/ellipse, rectangle, triangle, polygon snapped.
- **Lasso select:** loop to select ink/text/images → move, resize, recolour, duplicate (in-app only), delete, convert to text.
- Undo/redo (per note, ≥ 100 steps), pinch zoom 50–400 %, two-finger scroll.
- **Rich text:** typed text boxes and a text layer: bold, italic, underline, H1/H2, bullet & numbered lists, checklists, text colour, alignment.
- **Insert (+) menu** in the tool row (replaces the image tool): **Image** (photo picker), **Camera** (take photo), **Scan** (ML Kit Document Scanner), **Voice recording** (synced to writing), **Audio file** (pick an existing audio file via SAF → plays inside note, playable bar like recordings, no stroke sync), **PDF** (insert its pages after the current page). Inserted images move/resize/crop. There is **no separate mic button** (he rejected the duplicate). While recording, a red bar at the top of the note shows a live timer and Stop; recording also continues with a notification (Stop action).
- **Voice recording inside notes**, multiple recordings per note; **recording synced to writing:** every stroke stores a timestamp into the active recording; tap a stroke during playback to jump there; during playback strokes written at that time are highlighted. Recording continues with screen off (foreground service + notification with Stop).
- **Handwriting → typed text** (ML Kit Digital Ink Recognition, English model downloaded once ~20 MB, then offline). Results: replace, or add below.
**Pages**
- **Page direction (his request): vertical (portrait) or horizontal (landscape) chosen per note at creation, and changeable per page any time** (mixed pages allowed). Turning a page keeps its content (content is re-fit, never cropped; strokes rescaled proportionally with an option "keep size, extend page").
- Page type: pages (A4 ratio) or one long endless page.
- Paper: plain, lined, grid, dotted; templates: Cornell, to-do, weekly planner, study timetable. Change paper per page.
- Pages screen: thumbnails, add, duplicate, delete, reorder (drag), turn page, paper style.
**PDF**
- Import PDF (file picker or share-into) → becomes a note; each PDF page is a page with ink layer on top; highlight text, write, add pages between; page thumbnail strip; search inside PDF text.
**Organise**
- Home grid of note pictures (2 cols phone, 3 medium, 4 expanded) with list-view toggle; pinned notes first.
- Folders with colours (nested 1 level), favourites, pin, **tags** (#tag, many per note), sort (modified, created, title A–Z), multi-select actions (pin, move, lock, delete, tag).
- **Search** across: typed text, handwriting (recognised in background, offline), PDF text, **text in photos** (ML Kit Text Recognition, offline), titles, tags. Filter chips: All / Typed / Handwriting / PDFs / Photos / Recordings. Matches highlighted; tap opens note at that page.
- **Two notes side by side** (medium/expanded width, and phone landscape): open another note beside; each side independent; drag the divider.
**Safety**
- **Lock notes:** biometric (BiometricPrompt, fingerprint/face) with app PIN fallback (4–6 digits, PBKDF2-hashed). Locked notes' content encrypted at rest (AES-GCM, key in Android Keystore); thumbnails hidden.
- **Trash:** 30-day restore, auto-purge (WorkManager daily), empty trash with confirm.
- **Backup:** one `.inkbackup` file (zip: JSON + assets + audio) saved via SAF anywhere (Drive app, pen drive…); restore from file (merge or replace). Locked notes stay encrypted inside backup, need PIN on restore.
- **Export one note:** PDF (all/this page), PNG images per page, text file, `.inknote` file (full note incl. recording) — save or share sheet.
- **Share into InkNote:** text → new typed note; images → note with images; PDF → PDF note.
- Autosave continuously (debounced 1 s + on pause).

### Later (not v1)
Home widget, reminders, Google Drive auto-backup, cloud sync / login, Hindi UI, whole-app lock, math solver, AI features.
### Out of scope
Accounts, ads, analytics, crash reporting (he chose none), clipboard features (no system copy/paste buttons in our own UI; duplicate stays in-app).

## 3. Screen map
```
Home (All notes)  ──tap note──▶ Editor ──⋮──▶ Note menu ─▶ Pages & direction
 │  ├─ + ─▶ New-note sheet ─▶ Editor / PDF view                    ├▶ Turn page
 │  ├─ ☰ ─▶ Side menu (All, Favourites, Pinned, Locked,            ├▶ Open another note beside (split)
 │  │        Folders, Tags, Trash, Settings)                       ├▶ Convert handwriting → text
 │  ├─ search pill ─▶ Search                                       ├▶ Tags & folder · Lock · Export · Favourite · Trash
 │  ├─ hold note ─▶ Select mode (pin, move, lock, delete)
 │  └─ locked note ─▶ Unlock (fingerprint / PIN) ─▶ Editor
Editor tools: pen (tap again ▶ Pen sheet) · highlighter · eraser · select (▶ actions ▶ To text) · shapes · text (▶ format bar) · add image sheet · record
Settings ─▶ Backup sheet · Restore · Change PIN · Theme · About
Trash ─▶ Restore / Empty
```

## 4. Screen specs (phone, top → bottom; mockup file in brackets)
1. **Home [01]:** status bar · top bar (☰, title "All notes"/folder name, grid⇄list, ⋮: select, sort, import PDF, scan, settings) · filter chips (All n, Pinned, folders) · 2-col grid of cards (page picture 140 dp, pin badge, PDF badge, recording badge "● mm:ss", lock placeholder; title, date · first tag) · floating bottom: search pill (left) + New FAB 60 dp (right). States: **empty [02]** (illustration, Create a note, Import a PDF), loading (skeleton cards), select mode [16].
2. **Side menu [03]:** app mark, All notes/Favourites/Pinned/Locked with counts, Folders (+ new folder dialog with name + colour), Tags chips, Trash (count), Settings.
3. **New note sheet [04]:** Handwrite / Type / Open PDF / Voice note · Page direction segmented (Vertical | Horizontal) · Paper grid (8) · Page type (Pages | One long page) · Cancel/Create. Defaults from Settings.
4. **Editor [05]:** top bar (back, title — tap to rename, undo, redo, ⋮) · recording bar if note has audio (play/pause, seek, time) + tip line · pages canvas (vertical scroll, zoom) · bottom tool panel: option row (colours + rainbow → pen sheet + size) and tool row (pen, highlighter, eraser, select, shapes, text, **+ insert**) — 7 tools, no mic button. Option row changes per tool: eraser modes; select hint; shapes hint; **text → format bar [07]**. Selection actions float above selection [08].
5. **Pen sheet [06]**, **Handwriting→text sheet [09]**, **Note menu [10]**, **Export sheet [15]**, **Insert sheet** (3×2 grid: Image, Camera, Scan, Voice recording, Audio file, PDF), **Folder & tags sheet**.
6. **Pages [11]:** grid of page pictures with "Page n · Vertical/Horizontal"; bottom actions: Make vertical/horizontal, Paper style, Duplicate, Delete; + adds page in note's default direction.
7. **PDF view [12]:** same editor with page-thumbnail strip under top bar, search-in-PDF icon.
8. **Search [13]:** search field (auto-focus) · filter chips · "n results in m notes" · result cards (picture, title, matching snippet with highlight, source label: In PDF text · page, In handwriting · page, In a photo, In typed text, In recording · time). States: no results ("No matches — try another word"), indexing in progress (thin progress + "Reading handwriting & photos… 12/42").
9. **Unlock [14]:** lock icon, note title, fingerprint button (BiometricPrompt), 4 PIN dots, number pad; wrong PIN shake + "Wrong PIN"; 5 wrong → 30 s wait.
10. **Trash [17]:** info banner (30 days) · grid with days left + Restore · Empty (confirm dialog).
11. **Settings [18]:** Writing (stylus-only, shape auto-correct, default page, handwriting language), Notes (sort, auto-empty trash), Privacy (fingerprint unlock, change PIN), Backup (back up [19], restore), Appearance (theme System/Light/Dark), About.
12. **About [20]:** icon, InkNote, version, one-line offline note, **Developer: Sushant Kumar · Email: sushant.sare@gmail.com** (nothing else), open-source licences.

Permission/edge states: microphone denied → snackbar "Allow microphone to record" + Settings button; camera denied similarly; notification permission (Android 13+) asked only when first recording starts (for the recording notification); storage via SAF/photo picker (no storage permission). Model not downloaded + offline → "Connect once to download English handwriting pack (19 MB)". Huge PDF import → progress with cancel. Corrupt backup → plain error + keep current notes.

## 5. Design system — "Clean paper" (approved style 1)
| Token | Light | Dark |
|---|---|---|
| primary | #2B5BD7 | #9DB6FF |
| onPrimary | #FFFFFF | #0B1D4D |
| primaryContainer | #DCE5FB | #243A73 |
| background | #FBF8F3 | #141518 |
| surface (cards) | #FFFFFF | #1E2024 |
| surfaceVariant | #ECE8E1 | #2A2C31 |
| outline/divider | #E4DED4 | #34373D |
| text / muted | #1C1B1F / #625D55 | #ECE8E1 / #ABA59B |
| page lines | #DCE6F7 | #2D3A52 |
| highlight | #FFE066 | #7A6A1E |
| error | #B3261E | #F2B8B5 |
- Fixed brand colour (no Material You dynamic colour). Dark mode: pages turn dark, default ink colours shown lighter (display-only remap; stored colours unchanged; exported PDFs always light paper).
- Type: Inter Display (titles 24/22 sp bold), Inter (body 14–16 sp) — bundled fonts.
- Shapes: cards 16 dp, chips/buttons 12 dp, FAB 18 dp, sheets 24 dp top corners. Spacing 4-dp grid, screen padding 16 dp.
- Icons: outlined line icons 24 dp, 1.8 stroke (Material Symbols Outlined).
- Motion: sheets slide up 250 ms, fade 200 ms, shared-element card → editor, predictive back.
- **App icon:** royal-blue rounded square, white pen nib drawing a short ink wave; adaptive + themed (monochrome) icon.

## 6. Adaptive layout plan
- **Compact (<600 dp):** as specs above; drawer for navigation; editor single note.
- **Medium (600–839):** navigation rail (All, Favourites, Folders, Trash, Settings); home grid 3 columns; editor full width with max page width; "Open beside" available in landscape.
- **Expanded (≥840) [tablet-01..04]:** permanent navigation panel (280 dp) + 4-column grid; opening a note → list-detail: notes list 340 dp + editor; **side-by-side notes [tablet-03]** with draggable divider; content max width 1040 dp; settings max width 720 dp.
- Phone landscape [phone-landscape-editor]: list + editor two-pane when width ≥ 840 dp, otherwise editor only.
- Rotation, split screen, freeform windows, foldable postures (tabletop: tools on bottom half) keep state (ViewModel + SavedStateHandle; canvas state persisted).
- Keyboard/mouse: Ctrl+Z/Y, Ctrl+N new note, Ctrl+F search, Delete for selection, hover states, right-click context menu on cards, mouse wheel scroll / Ctrl+wheel zoom.

## 7. Quality standards
- **Accessibility:** contentDescription on every icon (labels as in prototype aria-labels), 48 dp targets, contrast ≥ 4.5:1 (checked: primary on bg 5.2:1, muted on bg 5.9:1), works at 200 % font (cards wrap titles to 2 lines, toolbars scroll), TalkBack reads note cards as "title, date, tags, pinned/locked/has recording".
- **Localization:** all strings in `strings.xml` (English); no hard-coded text.
- **Privacy & security:** no internet use except one-time ML Kit model downloads (Google Play services); no analytics, no crash reporting, no accounts; locked notes encrypted; `allowBackup=false` (own backup instead); privacy note in About ("notes never leave this device unless you share or back them up"). No API keys.
- **Performance ("stays fast with huge notes"):** ink stored as compact binary stroke blobs per page; render with Jetpack Ink / `androidx.ink` (low-latency `InProgressStrokesView` + motion prediction); finished pages cached as tiled bitmaps, only visible pages ± 1 kept in memory; images downsampled on insert; PDF pages rendered lazily with `PdfRenderer` at screen resolution; LazyColumn for pages; search indexing in background WorkManager, FTS4 table. Target: 300-page note opens < 1 s, 120 Hz drawing on capable devices.
- **Reliability:** autosave; Room with explicit migrations from v1; write-ahead file saves (temp file + atomic rename) for assets; errors shown in plain words with Retry; backup/restore round-trip tested.
- **Testing plan:** unit tests (repository, backup zip round-trip, PIN hashing, page-turn transform), instrumented: create/edit/delete/restore note, PDF import, lock/unlock, search typed text, rotate device mid-edit, export PDF; screenshot checks phone light/dark, tablet, phone landscape.
- **Release:** versionName `1.0.0`, versionCode 1 (+1 per build); targetSdk 36, minSdk 26; release signed with the pipeline keystore; R8 + resource shrinking.

## 8. Analytics & crash reporting
He chose **none**: no Crashlytics/Analytics, no Diagnostics screen. Use only Android's default crash behaviour. Never log note content.

## 9. Technical plan
- Kotlin, Jetpack Compose, Material 3 (+ `material3-adaptive` for list-detail / navigation suite), MVVM + Hilt, Navigation Compose, Coroutines/Flow, Room (+FTS4), DataStore (settings), WorkManager, `androidx.ink` (authoring/rendering/brush/geometry/storage), `androidx.biometric`, ML Kit: Digital Ink Recognition, Text Recognition (Latin, bundled), Document Scanner; Media3 / MediaRecorder (AAC m4a) for audio; PdfRenderer + `PdfDocument` for export; Coil for images; Security: Android Keystore + AES-GCM.
- **Room tables:** `Note(id, title, folderId, createdAt, updatedAt, pinned, favourite, locked, deletedAt, pageType[PAGES|ENDLESS], defaultOrientation, kind[INK|TEXT|PDF], pdfUri?, thumbnailPath)`, `Page(id, noteId, index, orientation[PORTRAIT|LANDSCAPE], paper[PLAIN|LINED|GRID|DOTTED|CORNELL|TODO|PLANNER|TIMETABLE], pdfPageIndex?, inkBlobPath, textJson, widthPt, heightPt)`, `Element(id, pageId, type[IMAGE|TEXTBOX|SHAPE], x,y,w,h,rotation, data)`, `Recording(id, noteId, path, durationMs, createdAt)`, `StrokeTime(pageId, strokeId, recordingId, offsetMs)`, `Folder(id, name, colour, parentId, sort)`, `Tag(id, name)`, `NoteTag(noteId, tagId)`, `SearchIndex` FTS4 `(noteId, pageId, source[TYPED|INK|PDF|PHOTO|TITLE], text)`.
- Files in app-private storage: `notes/<id>/pages/<pageId>.ink`, `images/`, `audio/`, `pdf/`.
- **Permissions:** RECORD_AUDIO (when first tapping Record, with a one-line reason first), CAMERA (scan/take photo, at use), POST_NOTIFICATIONS (Android 13+, at first recording), FOREGROUND_SERVICE + FOREGROUND_SERVICE_MICROPHONE (recording), USE_BIOMETRIC. No storage/media permissions (photo picker + SAF). INTERNET only for ML Kit model download.
- **Share-into:** intent filters ACTION_SEND / SEND_MULTIPLE for text/plain, image/*, application/pdf; ACTION_VIEW for application/pdf and `.inknote`/`.inkbackup`.
- **Background:** WorkManager jobs: search indexing (handwriting + OCR), trash purge daily, thumbnail regeneration. Recording = foreground service.

## 10. Fixed rules
- Full-fledged app, not a demo — every v1 item above works end to end.
- About: **Sushant Kumar · sushant.sare@gmail.com** only.
- No clipboard features (no Copy/Paste buttons we add; lasso "Duplicate" stays inside the app).
