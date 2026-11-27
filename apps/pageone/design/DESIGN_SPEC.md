# PageOne — design spec (handoff to android-apk-builder)

The builder implements this exactly. Mockups in `design/mockups/` (and `blueprint/mockups/`) and `blueprint/prototype.html` are the visual reference. Any deviation is a bug.

## 1. Identity
- Mood: calm, focused, Google-native, quietly expressive.
- Style: **pure Material 3 Expressive** (`MaterialExpressiveTheme`, `MotionScheme.expressive()`), Google-apps feel (Files/Drive on Android 16/17). Colour only on key parts (primary button, active nav pill, FAB, progress, selection).
- Colour: **Sage**, seed #5F8D6A, tonal spot. Wallpaper colours (dynamic) ON by default; Sage is the fallback and the icon colour. Light, dark, pure black.
- Type: Roboto Flex variable, bundled (`res/font/roboto_flex.ttf`, OFL — download from github.com/googlefonts/roboto-flex). English only.
- Icons: Material Symbols **Rounded** (use `material-icons-extended` Rounded set or bundled Material Symbols Rounded font); selected = filled.
- Shapes: Expressive `MaterialShapes` for icon containers (Cookie9Sided brand shape, Clover4Leaf, Sunny, Flower, Burst, Circle, rotating by stable index); grouped lists with 24dp outer / 6dp inner corners and 3dp gaps; connected button groups (17dp outer / 8dp inner; selected segment fully rounded).
- App icon: "Expressive cookie": 9-sided sage cookie (#386947) with a white page (#E8FFE9) and three lines, on #F7FAF4. Files: `design/icon/app-icon.svg`, `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` (themed icon), `ic_notification.xml`, `mipmap-anydpi-v26/ic_launcher*.xml`. Export a 512px PNG from app-icon.svg for the launcher fallback mipmaps.
- Splash: AndroidX SplashScreen, background #F7FAF4 / #0B0F0C, icon = foreground; kept only while the library index loads.

## 2. Tokens
`theme/Color.kt` (SageLight / SageDark / SageBlack + SemanticColors), `Type.kt`, `Shape.kt`, `Dimens.kt`, `Motion.kt`, `Theme.kt` (`PageOneTheme(themeMode, dynamicColor = true, pureBlack)`). Contrast results for all role pairs in light, dark and black: `design/contrast.md` (all pass ≥ 4.5:1).

## 3. Navigation
Bottom `NavigationBar` (short, Expressive): Home · Recent · Starred (pill indicator 64×32, filled icon when selected). Medium/expanded: `NavigationRail` with the + FAB on top; Home becomes list-detail (folders | PDFs). Settings opens from the gear on Home. Reader is full screen with **no** nav bar. Tabs keep their own scroll/back stacks; re-tapping a tab scrolls to top.

## 4. Screens (match mockup numbers)
01 **Home**: large title "PageOne" (headlineLarge) + "412 PDFs · 3.2 GB on this phone"; gear in a 48dp tonal circle; search pill 56dp (surfaceContainerHigh) → Search; Continue-reading card (surfaceContainer, 32dp corners, 84×112 first-page cover, primary label "Continue reading", title, "142 / 418 pages", 5dp progress bar, filled Resume button); "Folders" header with connected buttons Name | Date | Size | ↑↓; grouped folder rows (Expressive shape 48dp in secondaryContainer + icon, name, "128 PDFs · 412 MB", chevron); Expressive FAB 80dp, 24dp corners, primaryContainer → `FloatingActionButtonMenu` with **Merge PDFs & images · Split or extract pages · Compress PDF** (close button = primary). Hidden when no recent: card not shown.
02 Home without All-files access: explanation card (cookie shape with folder), Allow access (filled) + Open a file (outlined, system picker).
36 First scan: cookie `LoadingIndicator`, "Scanning your phone · N PDFs found so far", skeleton rows.
03 **Folder**: small top bar (back, folder name + count, search, ⋮), "PDFs" header with sort buttons, PDF rows (thumb 40×52, name 1 line ellipsis, meta, progress bar if opened, star toggle). Row details each switchable in Settings.
04 Multi-select: long-press; contextual bar "2 selected" share/star/delete/select all; selected rows secondaryContainer + filled check.
05 **Search**: expanded search field, filter chips (All folders, Starred, This week), match bolded, "N results".
07 **Recent**: large title + "Last opened first" + ⋮ (Clear all history, confirm dialog). Groups Today / Yesterday / Earlier. Swipe end-to-start removes from Recent (errorContainer background, Undo snackbar). Auto-cleans missing files.
08 **Starred**: large title + "Drag to reorder"; drag handles; long-press drag.
09 / 35 Empty states (Sunny star / Clover history illustrations, 160dp).
10 **Reader**: page canvas surfaceContainerHigh; **top chrome = floating back circle (52dp, surfaceContainerHighest, shadow) · PDF title + "142 of 418" as plain text (no container) · split pill search | ⋮ (two 52dp halves, 26/8dp corners, 3dp gap)** over a fade from surfaceContainerHigh to transparent; fast-scroll handle (6×56dp primary) with page bubble on drag; bottom **HorizontalFloatingToolbar**: Pages · Reading options · Night · Read aloud · Bookmark; **pen FAB** 64dp primary (20dp corners) → annotate. Tap page toggles all chrome (11 full screen shows only a "142 / 418" pill).
12/13 Find: search field pill with "3 of 12", ↑ ↓; chips Whole word, Match case, All results → results sheet with page numbers + recent searches. Current match orange, others yellow.
14 Text selection popup: Copy · Highlight · Underline · Share · ⋮ (Read aloud from here). Copy = clipboard (explicit exception requested by Sushant; only here).
15 Annotate: top shows "Editing · 142 of 418" and redo; bottom floating toolbar: highlighter, underline, pen, eraser, note, undo, ✓ Done (primary); colour dots row above (5 highlight colours). Ink via androidx.ink.
16/17 Sheet with segmented Pages | Contents | Bookmarks; Go to page field; thumbnails grid (current outlined primary); TOC with current chapter highlighted.
18 Reading options sheet: Normal / Night / Sepia tiles; Scroll | Page by page; switches Reflow, Crop white margins, Lock rotation, Keep screen on, Volume keys. (Night is ALSO a toolbar shortcut — keep both, Sushant approved.)
19 Night mode: page inverted (white text on black).
20 Read aloud card: voice, speed, prev, play/pause (64dp primary), next, sleep timer; current sentence highlighted.
21 ⋮ menu: Star, Share, Open with, Print, Save page as image | Merge with PDFs & images…, Split or extract pages, Compress | Rename, Details, Delete.
22 Save dialog: "Save your changes?" Discard · Save as copy · Save.
23 Password dialog: asks every time, never stored.
24 **Merge PDFs & images** (mixed merge): list of PDFs AND images (tag chip PDF/Image), drag order, "Each image becomes one page", Add PDFs / Add images (Photo Picker), Save as field, sticky "Merge N files". Images-only merge = images to PDF (no separate tool).
25 Compress: Light / Balanced / Strong connected tiles with size estimates, wavy progress, Cancel.
26 Duplicates: groups by content hash, Keep/Delete chips, error-coloured "Delete N copies · free X".
27/28 **Settings**: Appearance (Theme, Wallpaper colours, Pure black — shown only in dark), PDF rows show (4 switches), Reading (Default scroll, Keep screen on, Volume keys, Clear search history), Library (Find duplicate PDFs, Rescan), Privacy (App lock, Files access, "Works fully offline" note), About.
29 About: icon, PageOne, Version 1.0.0 (1), Developer Sushant Kumar, Email sushant.sare@gmail.com, Open-source licences, Privacy note. **Diagnostics hidden**: tap the version line 5 times.
30 **App lock**: lock screen + system **BiometricPrompt** (BIOMETRIC_STRONG | BIOMETRIC_WEAK | DEVICE_CREDENTIAL): face, fingerprint or phone PIN/pattern, whichever first. No custom keypad, no app PIN.
31–34 Setup guide (fresh install only, after a first-launch animation of the cookie icon morphing into the page): Welcome → Find all your PDFs (Allow access / Skip) → Pick your look (System/Light/Dark + Pure black) → You're all set. "Step X of 4" bottom-left; next button morphs circle → 24dp square → cookie → tick.
37 Can't-open error state (Flower shape, errorContainer).

## 5. Adaptive
Compact: as above. Medium/expanded: rail + list-detail Home (tablet mockup), reader shows a two-page spread in landscape with the floating toolbar centred and pen inside it. Phone landscape: reader page fills width, chrome identical. State survives rotation, split screen, resizing; keyboard: Ctrl+F, arrows, PgUp/PgDn, +/−.

## 6. States matrix
Every list: loading skeleton, empty (first-time / cleared / no results), error with retry, permission denied (Home). Reader: loading (blurry → sharp tiles), password, damaged file, very large file. Tools: running (wavy progress), success snackbar with Open/Share, failure with retry.

## 7. Copy
All strings in `res/values/strings.xml` (English only, his choice) with plurals. Sentence case, verbs on buttons.

## 8. QA bar
Rubric ≥ 8 on all 9 items. Screens compared with mockups after build; 1.4× font screenshots of every connected-button row; every page tested on Android 17 and 16 emulators only.
