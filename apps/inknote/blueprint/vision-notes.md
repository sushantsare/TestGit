# InkNote — vision notes

## Original vision (his words)
"I want to build a notes app, and it should have features similar to Samsung Notes."

## Interview answers
- Users: himself + publish for others (Play Store / share APK)
- Devices: phone and tablet equally; rotation supported
- App name: InkNote
- Online: fully offline, no login; backup file he can save anywhere
- Language/theme: English UI, light + dark (follow system)
- Colours: fixed brand colour (to be chosen from style boards)
- Home on open: all-notes grid; folders in side menu
- Lock: fingerprint/face + PIN backup, for selected notes
- Analytics / crash reports: none at all

## Core features (v1)
- Handwriting & drawing (finger/stylus, pens, highlighter, eraser, colours)
- Rich text (bold, lists, checklists, headings, text colour, images)
- Voice recording inside notes
- PDF import & annotate
- Folders + colours, pin, favourites
- Lock notes
- Backup / export
- Trash with 30-day restore
- Search inside notes
- Share into the app (text, images, PDFs)

## Picked suggestions (v1)
Standard: page styles (plain/lined/grid/dotted, pages or endless page) · select & move writing (lasso) · stylus-only mode · export note as PDF/image
Extras: handwriting → typed text (offline) · shape auto-correct · recording synced to writing · note templates
Stand out: stays fast with huge notes · text in photos/scanned PDFs searchable · two notes side by side · tags as well as folders

## Added by him after summary
- "There is a lacking in Samsung Notes that we cannot change the orientation of pages like horizontal or vertical. It should also be there in our app."
  → Page orientation: portrait (vertical) or landscape (horizontal), chosen per note when creating and changeable per page later (mixed pages allowed, like a PDF); rotating an existing page keeps its content.

## Not chosen now (don't re-suggest for v1)
- Home-screen widget
- Reminders on notes
- Google Drive auto-backup, cloud sync, login
- Hindi UI switch, dark-only theme
- Whole-app lock (only selected notes locked)
- Firebase / local diagnostics
- Wallpaper-based (Material You) colours

## Design rounds
- Step 1 style: picked **1 · Clean paper** (royal blue #2B5BD7 on warm white #FBF8F3, Inter/Inter Display, 16dp cards). Not picked: 2 · Bold & colourful (coral #F2552C, Poppins), 3 · Dark & premium (charcoal + gold, Lora/DM Sans).
- Step 2 layouts: Home **A · Big note pictures** (2-per-row grid, search pill + New button at bottom; grid/list toggle). Writing screen **A · Tools at the bottom** (tool + colour bar at bottom, recording bar at top). Not picked: Home B compact list (kept as optional view toggle), Home C dashboard; Editor B floating pill, Editor C tools at top.
- Step 3–4: full mockups (20 phone screens, 4 dark, 5 tablet, 1 phone-landscape) + clickable prototype published: https://claude.ai/artifact/C3nzUPr9aVGyZ8QVJhyNks — awaiting approval.
- Change after mockups (his words): image icon next to T → a **plus icon** that shows what to add, like Samsung Notes. Options researched and given: Image, Camera, Scan, Voice recording, Audio file, PDF. Mic button kept (my suggestion; he replied "Good to go").
- **APPROVED** 20 Nov 2026 ("Good to go").
- He then asked: voice is already in the + menu, so why another voice button next to it? → **Mic button removed**; recording only via + → Voice recording; red live recording bar with Stop at top of note.
