# Gallery — vision notes

**Distribution: personal**

## His vision (word for word)
> "Okay, so now I want to make an Android app which will be uh, a photo viewer, and before we proceed, do you remember that like in Android 4 or Android 5, I do remember, like that was some Samsung phone or what, I don't remember yet, but like if you are viewing the photos, right, you are swiping here and there, but when you zoom in the photos with the two fingers, and then they suddenly come into a different mode, and like then when you swipe up any photo, it used to get deleted, and you can swipe it right or left, and when whatever photo you swipe up, that was get deleted."

Identified as the Android 4.1 Jelly Bean stock Gallery "filmstrip mode" (seen on Galaxy Nexus). He confirmed: "Yes" — build it as the main feature.

## Q&A
### Purpose / type
- App type: **Full gallery app** (replaces phone gallery: albums, all photos & videos, viewer, filmstrip, trash, share).
- Media: **Photos + videos** (videos play in-app, also appear in filmstrip).

### Filmstrip (core)
- Enter filmstrip: **pinch fingers together on a full-screen photo** (only this way; no button, no "quick clean" entry).
- Swipe left/right in filmstrip: browse.
- Swipe **UP** = delete, with UNDO bar.
- Swipe **DOWN** = **nothing**.
- Deleted items go to **app's Recycle Bin** (Undo bar right away + restore within 30 days from Trash).

### Screens
- Home: **Tabs: Photos (by date) + Albums**.

### Look & feel
- Nostalgia: **Filmstrip feel only** — modern design otherwise (no Holo theme, no stack albums, no 3D effects).
- Theme: **Light + Dark, follows phone**.
- Language: **English only**.
- Name: **Gallery** (he chose it; package com.sushant.gallery). Earlier suggestion "Flick Gallery" dropped.

### Privacy
- **Hidden locked album** (PIN/fingerprint vault for chosen photos).
- **Remove location/EXIF before share** option.

### Permissions
- **All files access** (MANAGE_EXTERNAL_STORAGE) so swipe-up deletes instantly without system popups.

### Devices
- **Phone, rotates freely**; tablet layouts still built.

### Analytics
- Default: local crash log + local usage stats, Diagnostics screen; nothing leaves the phone.

## Feature picks (v1)
- Share, rotate, info (EXIF details)
- Favourites
- Multi-select & batch (delete, share, move, copy)
- Search & sort
- Basic photo editor (crop, rotate, flip, filters, brightness)
- Open from other apps (default viewer) + Set as wallpaper
- Remove location before share

## Rejected / not now (don't suggest again)
- Swipe-down = delete or favourite
- Filmstrip button / "Quick clean" entry from album
- Albums-first home, timeline-only home
- Dark Holo theme, stack-of-photos albums, tilt/3D effects
- Lock whole app (chose hidden album instead)
- Hindi UI
- Slideshow, home-screen widget
- Quick Clean summary mode, "instant loading" as a named feature (performance still required), duplicate & screenshot finder
- Names offered: Filmstrip, Swipe Gallery, Lumo Gallery, Jelly Gallery, Reel Gallery, Pinch Gallery

## Design picks
- Style: **1 · Clean & bright** (white surfaces, teal #00696B accent, Plus Jakarta Sans, 4-col grid with 2px gaps, standard bottom bar). Rejected: 2 · Dark & cinematic (amber/Outfit), 3 · Bold & roomy (coral/Manrope, big title, floating pill).
- App icon: **20-D** — seven slim liquid-glass petals (deep teal, teal, sea blue, sun yellow, amber, peach, coral) on pale mint #F1FAF8, thinner glass so colours stay rich. Saved as design/icon/app-icon.svg (+ monochrome). His direction: "like Apple's Photos icon but liquid glass" → done as inspiration with own petal count/shape/palette (not Apple's 8-colour rainbow).
- Rejected icons: 1–6 (flat batch), 7–12 and 13–18 (glass batches), 19 five-petal bloom, 21 six round petals, 20-A/B/C shades and A/B/C more-colour versions, E/F (too deep).
- Layouts: Albums **A · Round shortcuts + big cards** (Favourites/Videos/Recycle Bin/Hidden as round buttons, albums as 2-col big cards). Filmstrip **B · 3 smaller cards + thumbnail strip** (3 cards visible, bin icon appears while flicking, thumbnail strip at bottom to jump). Rejected: Albums B (list + 3-col), C (Camera hero + list); Filmstrip A (single big card), C (light + buttons).
- **Approved** the full design (mockups + prototype https://claude.ai/artifact/1yqciYwpvQb1AvBCvxKY9M) on 21 Nov 2026: "Go ahead".
