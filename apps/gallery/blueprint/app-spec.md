# Gallery — App Spec

**Distribution: personal**
**Package:** `com.sushant.gallery` · **Security level:** MASVS L1 (+ vault hardening) · **DI:** manual through the Application class (proven repo stack; one repository)

## 1. Vision brief
- **Name:** Gallery
- **Pitch:** A fast, ad-free offline gallery that brings back Jelly Bean's filmstrip: pinch a photo to shrink it into a strip, flick sideways to browse, flick up to delete.
- **User:** Sushant (personal use; phone first).
- **Success:** opens in < 1 s with 30k photos; deleting 20 junk photos takes 20 flicks with no popups; Undo always saves a mistake; zero ads.

## 2. Features
### v1
1. **Photos tab** — all photos & videos, grouped by day/month, pinch to change grid density (2–6 columns), fast scroller with date bubble.
2. **Albums tab** — folder albums (Camera, Screenshots, WhatsApp…), plus Favourites, Videos, Recycle Bin, Hidden (locked) entry; sort and search.
3. **Viewer** — full-screen pager, pinch-zoom up to 8×, double-tap zoom, swipe left/right, tap to toggle bars, video playback (Media3 ExoPlayer) with seek bar.
4. **Filmstrip mode (core)** — on a photo at fit-scale, pinch fingers together → photo animates down to ~60% height card; neighbours appear left/right. In filmstrip: swipe left/right to browse (snap per item), swipe a card **up** to delete (card flies off, gap closes, snackbar "Moved to Recycle Bin · UNDO" for 5 s, several undos stack), swipe down does nothing (rubber-band), tap a card or pinch out → back to full viewer. Videos show as their first frame with a play badge. Fast delete: no confirmation dialogs.
5. **Recycle Bin** — app-managed `.gallery_trash` folder; items kept 30 days with days-left label; restore / delete forever / empty bin; auto-purge via WorkManager daily.
6. **Favourites** — heart in viewer and multi-select; Favourites album.
7. **Multi-select & batch** — long-press then drag-select; delete, share, move to album, copy to album, favourite, hide.
8. **Search & sort** — search album and file names; sort albums by name/date/count, photos by date taken/modified/name/size, asc/desc.
9. **Info panel** — swipe up in viewer (not filmstrip) or ⓘ: date, file name, path, size, resolution, camera, ISO/aperture/shutter, location (lat/long text).
10. **Share** — system share sheet; optional "Remove location before sharing" (Settings toggle + per-share choice); strips GPS EXIF into a temp copy, original untouched.
11. **Rotate** — lossless EXIF orientation rotate for JPEG; re-encode others.
12. **Basic editor** — crop (free/1:1/4:3/16:9), rotate 90°, flip, brightness/contrast/saturation, 8 filters; save as copy or overwrite; EXIF kept.
13. **Hidden album (vault)** — move items into app-private encrypted storage (AES-GCM via Jetpack Security / Tink); unlock with PIN + fingerprint (BiometricPrompt); FLAG_SECURE; auto-lock on leave; recovery note: forgetting PIN = loss (shown at setup).
14. **Open from other apps** — handles ACTION_VIEW image/* video/*; "Set as" wallpaper/contact.
15. **Settings** — theme (system/light/dark), dynamic colour toggle, grid columns default, show hidden files, recycle-bin days (fixed 30, show info), remove-location default, vault PIN change, Diagnostics, About.
16. **Diagnostics** — local crash log + local usage counts; export/share log file.
17. **About** — Gallery, version, "Developer: Sushant Kumar · sushant.sare@gmail.com".

### Later
Slideshow, widget, duplicate finder, Quick Clean summary.

### Out of scope
Cloud sync, accounts, ads, analytics leaving phone, clipboard features.

## 3. Screen map
```
Onboarding (permission: All files access) 
└─ Home [bottom bar: Photos | Albums]
   ├─ Photos grid ──► Viewer ⇄ Filmstrip
   ├─ Albums grid ──► Album grid ──► Viewer ⇄ Filmstrip
   │   ├─ Favourites, Videos
   │   ├─ Recycle Bin ──► Trash viewer
   │   └─ Hidden ──► Lock screen ──► Vault grid ──► Viewer
   ├─ Search
   ├─ Multi-select mode (contextual bar)
   └─ Settings ──► Diagnostics, About
Viewer ──► Info sheet, Editor, Share sheet, Move/Copy picker
External ACTION_VIEW ──► Viewer (single item / folder)
```

## 4. Screen specs (summary; full layouts come from the mockups)
- **Onboarding:** illustration, one line why access is needed, "Allow access" button → system All-files settings; denied state: explain + retry; fallback read-only mode with READ_MEDIA_* (deletes then use system request).
- **Photos:** top bar (title, search, overflow: sort, select, settings); date headers; grid; empty state ("No photos yet"); loading shimmer only on first scan.
- **Albums:** 2-col cards (cover, name, count); pinned row: Favourites, Videos, Recycle Bin, Hidden.
- **Viewer:** black background, top bar (back, favourite, ⓘ, overflow: rotate, edit, set as, move, copy, hide, details); bottom bar (share, edit, favourite, delete); bars auto-hide.
- **Filmstrip:** dark scrim background, centred card ~60% height, neighbours peek; top hint chip shown first 3 times: "Flick up to delete"; position "12 / 340"; snackbar with UNDO.
- **Recycle Bin:** grid with "n days" badge; top actions: Empty bin; multi-select restore/delete forever; empty state.
- **Vault lock:** PIN pad + fingerprint button; first-time setup flow.
- **Editor:** image canvas; bottom tool tabs (Crop, Adjust, Filters, Rotate); Save / Save copy.
- **Settings / Diagnostics / About** as listed.
- States for every list screen: empty, loading, error (with retry), permission denied.

## 5. Design system
Filled by android-ui-designer → `design/DESIGN_SPEC.md`. App icon: 20-D glass petal bloom (design/icon/app-icon.svg), monochrome layer for themed icons.

## 6. Adaptive layout
- Compact: bottom bar; grids 3–4 cols; filmstrip card 60% height.
- Medium: navigation rail; grids 5–6 cols; filmstrip shows 2 neighbours each side.
- Expanded: rail; Albums list-detail (album list left, grid right); viewer full screen; filmstrip cards scale to height; max content width for Settings 720dp.
- Rotation keeps position, zoom, filmstrip mode, selection (SavedStateHandle). Split screen, free-form, foldables supported. Keyboard: ←/→ next/prev, Del = delete (to bin), Esc = back, Ctrl+Z = undo; mouse wheel zoom; hover states.

## 7. Quality standards
- Accessibility: content descriptions ("Photo, 12 Mar 2026"), filmstrip delete also via TalkBack custom action "Delete" and a visible delete button on the focused card; contrast ≥ 4.5:1; 48dp targets; 200% font OK.
- Strings in resources (English only).
- Privacy: no network permission at all; vault encrypted; EXIF strip on share.
- Performance: Paging 3 + MediaStore cursor; Coil 3 thumbnails with memory/disk cache; cold start < 1.5 s; 120 fps-friendly gestures; baseline profile.
- Reliability: Room migrations planned; bin index in Room; file ops atomic (move to bin = rename on same volume); undo restores original path and timestamps.
- Testing: unit tests for repositories/trash purge; UI tests for filmstrip swipe-up delete + undo, rotation, tablet.
- Release: versionCode/versionName 1 / 1.0.0; targetSdk 36; minSdk 26; signing via GitHub secrets.

## 8. Analytics & crash
Local crash handler + local usage stats; Diagnostics screen with export. No Firebase.

## 9. Technical plan
- Kotlin, Jetpack Compose, Material 3, MVVM, Hilt, Coroutines/Flow.
- Room tables: `trash_item(id, originalPath, trashPath, deletedAt, mimeType, size, dateTaken)`, `favourite(path/mediaId)`, `vault_item(id, encPath, originalName, mime, addedAt)`, `usage_event`.
- MediaStore for indexing (images + video), ContentObserver for live refresh.
- Permissions: MANAGE_EXTERNAL_STORAGE (requested at onboarding with explanation), READ_MEDIA_IMAGES/VIDEO fallback, USE_BIOMETRIC, SET_WALLPAPER. No INTERNET.
- Libraries: Coil 3 (+video frames), Media3 ExoPlayer, Telephoto (zoomable image) or custom transform gestures, ExifInterface, WorkManager, androidx.security/Tink, Biometric, Paging 3.
- Filmstrip gesture: custom `pointerInput` on viewer — when scale < 0.85 at fit level during pinch, transition (shared-element style) to Filmstrip state; filmstrip uses HorizontalPager with per-page vertical drag (`anchoredDraggable`, threshold 35% height or fling velocity) for up-delete.

## 10. Fixed rules
- Full-fledged app, not a demo.
- About: Sushant Kumar · sushant.sare@gmail.com only.
- No clipboard features.
