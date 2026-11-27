# PageOne — vision notes

**Distribution: personal** (APK on his phone, or shared as an APK)

## His vision (his words)
> "I want the app to work as it is this app works, right? But additionally, I want the app icon to be displayed in the home screen of my phone, as well as when the app opens, it shows me two tabs, right? One is like I'm telling you, so if you have something better, just correct me. uh, Home tab and recent. In the home tab, it will be folder view in list format, and on the recent it will show the latest PDFs that have been opened, right? And it will be purely in material 3 expressive, the hard coded way. Like literally every single element I want is to be in material 3 expressive."

"This app" = Google PDF Viewer 2.19.381.03 (inspected in chat). Rule: no Google code, assets or libfoxit.so reused; behaviour and ideas only.

## Interview (32 questions)
### Purpose and use
- Q1 Opens: PDFs from WhatsApp/Telegram, forms and documents, question papers/PYQs, study PDFs and books.
- Q32 Success: opens any WhatsApp PDF in under a second; smooth scrolling on huge books; finds any PDF on the phone fast; fully Material 3 Expressive everywhere.

### Opening and files
- Q2 Tapping a PDF in another app → opens straight in the viewer and is added to Recent.
- Q3 Home: all PDFs on the phone, grouped by folder, folder list in list format.
- Q4 "All files access" permission: yes.
- Q5 If denied: friendly card with Grant + "Open a file" (system picker) buttons.
- Q6 PDF rows show: first-page thumbnail, page count, size and date, reading progress. **Each of these must be switchable in Settings** (user chooses what rows show).
- Q7 Sort: user chooses (name, date, size, A–Z/Z–A), remembered.
- Q8 File-name search bar at the top (across all folders).
- Q25 PDF only.
- Q26 Fully offline: no INTERNET permission.

### Reading
- Q9 Vertical continuous scroll by default, switch for page-by-page.
- Q10 Night mode (inverted pages), sepia tint, keep screen on, full-screen (tap to hide bars).
- Q11 Reopen at last page and zoom.
- Q12 Fast-scroll handle, go to page number, page thumbnails strip, table of contents.
- Q16 Reflow reading mode: yes.
- Q18 Find in PDF: highlight all matches + count, whole word / match case, results list with page numbers, search history.
- Q27 Big files: open instantly, pages load as you scroll (blurry then sharp).
- Q28 Phone mainly; tablet layout too, rotation allowed (two pages side by side in landscape on big screens).

### Editing and actions
- Q13 Highlight/underline, pen/free drawing, text notes/comments, fill forms and sign.
- Q14 On save: ask Save or Save as copy.
- Q15 Share / Open with / Print; rename, delete, file details; save page as image; bookmark pages.
- Q17 **Copy selected text: YES** — explicit exception to his no-clipboard rule, only for copying selected PDF text.
- Q19 Password PDFs: ask each time, nothing saved.

### Recent
- Q20 Shows last-opened date/time + progress; swipe to remove (file not deleted); clear all history button. (Pinning moved to Starred tab, see S1.)

### Look and app
- Q21 Name: **PageOne**.
- Q22 English only.
- Q23 Light, dark and pure black (AMOLED), Material You wallpaper colours on by default with a fixed scheme as backup.
- Design: purely **Material 3 Expressive**, every element (replaces his Warm ledger default for this app).
- Q24 Long-press launcher shortcuts; app lock (fingerprint/PIN).
- Q29 Tabs in a bottom bar.
- Q30 App lock protects the whole app, with an on/off switch.
- Q31 Local crash log, viewable/shareable from Settings; nothing leaves the phone.
- Standing rules: launcher icon; first-launch animation + step-by-step setup guide (fresh install only); About = Sushant Kumar · sushant.sare@gmail.com; full-fledged app.

## Suggestions he picked
- S1 Tabs: **Home · Recent · Starred**.
- S2 Home top: "Continue reading" card, storage summary. (Rejected: quick folder chips.)
- S3 Crop white margins, read aloud (TTS, offline), rotate page / lock orientation, volume keys turn pages.
- S4 Page tools: merge, split/extract pages, images to PDF, compress.
- S5 Tools live in **both** an Expressive FAB menu on Home and the viewer's ⋮ menu.
- S6 Auto-clean Recent, duplicate PDF finder, reading position shared across copies of the same PDF.
- Rejected: Tools tab, quick folder chips, reading stats, home-screen widget, pin-PDF-to-home-screen, remembering passwords.

## Engine decision
Jetpack androidx.pdf (beta), Apache 2.0; PDFium fallback; no MuPDF.

## UI design picks
- Style round 1 (pastel M3: Coral, Teal, Indigo) → "more". Round 2 (vibrant: Tangerine, Ruby, Lime) → "more"; steer: too colourful/loud, wants Google's-own-apps feel. Round 3 → **picked 9 · Sage** (calm, Google-native Material 3 Expressive, subtle Expressive shapes, colour only on key parts; seed #5F8D6A, tonal spot). Rejected: options 1–8.
- Icon batch 1 (1 Page with a one, 2 Open book, 3 Expressive cookie, 4 Bookmarked page, 5 Page stack, 6 Page with lens) → "more"; batch 2 (7 P made of a page, 8 Leaf page, 9 Page sunrise, 10 Clover one, 11 Page curl, 12 Big bold 1) → he picked **3 · Expressive cookie** (9-sided sage cookie with a white page inside, pale background); locked as is (declined light-sage background). Saved design/icon/app-icon.svg.
- Layouts: Home **A · Big resume card** (large title + subtitle count, search pill, Continue card with cover + Resume, Folders header with Name/Date/Size connected buttons, grouped folder list with Expressive shapes, FAB). Reader **A · Floating toolbar** (top bar title + page, find, ⋮; horizontal floating toolbar bottom-left: thumbnails, reading modes, night, read aloud, bookmark; pen FAB bottom-right; fast-scroll handle). Rejected: Home B compact, Home C dashboard, Reader B docked+scrubber, Reader C side toolbar.
- Reader top area (his idea, refined): back button in its own floating **circle**; PDF title + "142 of 418" as **plain text, no pill**, over a soft top fade; search + ⋮ as a **Z · split pill** (M3 Expressive connected button group, two halves with a small gap). Rejected: full-width top bar, title in its own pill, title inside the shared pill, single plain pill (X), pill with divider line (Y).
- Mockups (37 phone screens + dark, pure black, tablet, landscape) reviewed element by element: all good. Keep the Night shortcut in the reader toolbar as well as in Reading options. Crash log hidden (tap version 5 times). **App lock = Android system unlock**: face, fingerprint or phone PIN, whichever first (his words: "It should be biometric lock… either the face, fingerprint, or pin, anything, whichever captures first"); custom keypad removed.
- Approval round 1 → change: Merge becomes **Merge PDFs & images** (mixed merge — PDFs and images together in one list, any order; Add PDFs / Add images buttons; each image = one page). Same in the reader's ⋮ menu. He chose mixed merge over 'add pages into an existing PDF'.
- Approval round 2 → he pointed out Images to PDF is now redundant; **removed**. Tools are: Merge PDFs & images (images-only merge = images to PDF), Split/extract, Compress — in the + menu and the reader ⋮ menu.
