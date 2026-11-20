# Saveo — App spec (blueprint v1)

## 1. Vision brief
- **Name:** Saveo · **Package:** `com.sushant.saveo`
- **Pitch:** Save WhatsApp statuses (photos, videos, photo-with-music) in one tap and keep them forever — no ads, fully offline.
- **User:** Sushant (personal, sideloaded).
- **Success:** a viewed status appears in Saveo instantly; saving is 1 tap; saved items never disappear unless he deletes them; app opens in < 1.5 s.

## 2. Features
### v1
- Two tabs: **Statuses** | **Saved** (tabs + swipe).
- Sources: WhatsApp, WhatsApp Business, dual/clone WhatsApp (best-effort, extra folder). Source switcher chip row/menu; Business items get a small "B" badge.
- Thumbnail grid (3 cols phone), grouped **Today / Earlier**; video badge + duration; photo+music statuses detected as video by MIME.
- Save button bottom-right on each tile → progress → ✓ tick (tile stays). Duplicate-proof via content hash; already-saved items show ✓.
- Filter chips All · Photos · Videos (both tabs).
- Full-screen viewer: swipe left/right, pinch-zoom photos, video player (play/pause, seek, mute, loop, speed 0.5–2×); bottom bar Save / Share / Repost / Delete (Saved only).
- Share to any app (Android share sheet via FileProvider/MediaStore URI).
- Repost to my WhatsApp status: share intent targeted at `com.whatsapp` (or w4b) — WhatsApp opens with the media ready.
- Long-press multi-select; Select all; bulk Save / Share / Delete.
- Saved tab: sort newest/oldest; date search (date-picker range); delete with Undo snackbar → Recycle bin (30-day auto purge, restore / delete forever).
- Pull to refresh; auto-refresh when app returns to foreground.
- "Open WhatsApp" button (top bar + empty state).
- Settings: auto-save new statuses (OFF default), new-status notifications (on/off), app lock (fingerprint / device credential, lock whole app), theme (system/light/dark), dynamic colour toggle, manage folders, recycle bin, diagnostics, about.
- Notifications: WorkManager periodic check (15 min) + check on app open; "3 new statuses" notification, tap opens Statuses tab.
- Smart folder setup + auto-fix screen.
- No ads, no trackers, no internet permission.

### Later
- Home-screen widget (not picked now), Hindi UI, favourites/collections.

### Out of scope
- Seeing statuses not already viewed in WhatsApp (impossible — WhatsApp only downloads viewed ones), message recovery, direct chat, clipboard features.

## 3. Screen map
```
Splash → (first run) Onboarding (3 cards) → Folder setup (WhatsApp / Business / Dual) → Home
Home [tabs: Statuses | Saved]
 ├─ Viewer (from either tab)
 ├─ Selection mode (long-press)
 ├─ Settings
 │   ├─ Manage folders (re-grant / add)
 │   ├─ Recycle bin
 │   ├─ Diagnostics
 │   └─ About
 └─ Lock screen (when app lock on, on cold start / after 1 min in background)
```

## 4. Screen specs
**Home — Statuses tab (top→bottom):** top bar: "Saveo" title, source chip ("WhatsApp ▾"), Open-WhatsApp icon, overflow (Select all, Settings). Tabs Statuses (count) | Saved (count). Filter chips All · Photos · Videos. Section headers Today / Earlier. 3-column square grid; each tile: thumbnail, top-left video badge+duration, top-left "B" for Business, bottom-right round save button (download → spinner → ✓ green). Extended FAB on scroll-up: "Save all" (only when unsaved items exist).
- Empty: illustration + "Watch statuses in WhatsApp first — they'll show up here" + [Open WhatsApp].
- Loading: shimmer grid. Error/permission lost: card "Saveo lost access to the WhatsApp folder" + [Fix now].

**Home — Saved tab:** same grid; tiles without save button; top-right sort icon (Newest/Oldest), calendar icon (date range); grouping by save date. Empty: "Nothing saved yet — tap ⬇ on any status".

**Viewer:** black background, top bar back + date + source; media pager; bottom action bar Save(✓)/Share/Repost/Delete; video controls overlay (tap to toggle) with seek bar, mute, loop, speed.

**Selection mode:** top bar turns into "3 selected" + Select all + close; bottom bar Save / Share / Delete.

**Folder setup:** step cards for each app: icon, name, status (Connected ✓ / Not connected), [Connect] → opens system picker pre-pointed to .Statuses with a short explanation picture ("Tap USE THIS FOLDER → ALLOW"). Validates the chosen folder; wrong folder → plain message + retry. Dual: "Add another folder" (best-effort note).

**Settings:** grouped list as above. **Recycle bin:** grid with days-left label, Restore / Delete forever, Empty bin. **Diagnostics:** crash log list, usage counts, Export log (file share). **About:** app name, version, "Developer: Sushant Kumar · sushant.sare@gmail.com".

**Lock screen:** app icon, "Saveo is locked", [Unlock] → BiometricPrompt (fingerprint or device PIN).

## 5. Design system
- Follows system light/dark. Accent green (not WhatsApp's exact hex): primary `#1B8A5A` light / `#5FD49A` dark; surfaces Material 3 neutral; error `#BA1A1A`/`#FFB4AB`. Optional Material You dynamic colour toggle (off by default to keep green).
- Type: Roboto Flex / system default; titles semibold.
- Radius: tiles 16dp, chips 8dp, buttons full-round; spacing 4/8/12/16/24.
- Icons: Material Symbols Rounded.
- Motion: shared-element tile → viewer; save button morph download→✓ with a small pop.
- App icon: **14a** — photo card behind a frosted-glass video card with play sign + white glossy save badge on a green gradient (`design/icon/app-icon.svg`, themed: `app-icon-mono.svg`).
- Chosen style: **Clean & Fresh** + his direction "3D/glossy, a mix of One UI, iOS and Pixel (Google)": glossy gradient buttons (#2FA56F→#1B8A5A), glossy round save buttons with soft shadow, frosted-glass controls on media, 22dp tile radius, 28dp sheets/cards.
- Dark scheme: bg #0F1512, card #1B231F, primary #5FD49A, ink #E3EAE5, muted #9AABA1.
- **Home layout (picked B):** title "Saveo" + source chip + Open-WhatsApp + overflow; underline tabs Statuses | Saved with counts; filter chips; **2-column large rounded cards** (3 on landscape/medium, 4 landscape phone); extended "Save all · N" button bottom-right.
- **Viewer (picked C):** media in a 28dp rounded card over a blurred copy of its colours; round frosted back/more buttons; page dots; big white pill "Save to gallery" (turns green "✓ Saved") + round frosted Share / Repost; in Saved: Share / Repost / Delete. Video controls in a frosted panel inside the card.
- Tablet: navigation rail + 3-column grid + viewer pane on the right (see mockups/tablet-*.png, prototype.html wide mode).
- References: `mockups/*.png`, `prototype.html` (published: https://claude.ai/artifact/4uQpWiyvbFNYtyNcM1B5H9).

## 6. Adaptive layout plan
- **Compact (<600dp):** tabs at top, 3-column grid, viewer full-screen.
- **Medium (600–839dp):** 4–5 columns, navigation rail (Statuses / Saved / Settings).
- **Expanded (≥840dp):** rail + list-detail: grid left, viewer pane right; max content width 1200dp; 6 columns.
- Rotation keeps tab, scroll position, selection, video position (ViewModel + SavedState). Resizable windows, split screen, foldables handled by WindowSizeClass. Keyboard: arrows in viewer, Delete key, Ctrl+A select all; hover states on tiles with mouse.

## 7. Quality standards
- Accessibility: content descriptions on all tiles/buttons ("Video status, 0:15, not saved, save button"), contrast ≥ 4.5:1, 48dp targets, works at 200% font.
- Localization: all strings in `strings.xml` (English).
- Privacy & security: no INTERNET permission; nothing leaves the phone except what he shares. Data stored: Room DB of saved items (uri, hash, source, dates), settings in DataStore. No keys.
- Performance: thumbnails via Coil (video frame decoder) with memory+disk cache, paging for large lists; cold start < 1.5 s; no background work except the 15-min WorkManager check (only if notifications or auto-save are on).
- Reliability: plain-word errors + retry; Room migrations planned; if persisted folder permission is lost → auto-detect and show Fix card.
- Testing: save image/video/photo+music, duplicate save, delete+undo, bin restore, permission revoked, empty WhatsApp, 1000+ items, rotation, tablet, app lock.
- Release: versionName 1.0.0 / versionCode 1; target latest SDK; release-signed via pipeline.

## 8. Analytics & crash reporting
Local only: crash handler → local log; usage counters (saves, shares, reposts, screens). Diagnostics screen to view/export. No personal content logged.

## 9. Technical plan
- minSdk 26, Kotlin, Jetpack Compose, Material 3, MVVM + Hilt, Coroutines/Flow.
- Storage access: SAF `ACTION_OPEN_DOCUMENT_TREE` with `EXTRA_INITIAL_URI` = `primary:Android/media/com.whatsapp/WhatsApp/Media/.Statuses` (Business: `com.whatsapp.w4b/WhatsApp Business/Media/.Statuses`); `takePersistableUriPermission`. API 26–29: READ_EXTERNAL_STORAGE + legacy paths.
- Skip `.nomedia` and partial files; detect type via DocumentFile MIME (mp4 → video, jpg/webp → image).
- Saving: MediaStore insert to `Pictures/Saveo` and `Movies/Saveo` (IS_PENDING pattern); SHA-256 hash for duplicates.
- Room: `saved_items(id, mediaStoreUri, hash, type, source, originalName, savedAt, deletedAt?)`, `seen_statuses(hash, firstSeenAt)` for new-status detection.
- Recycle bin: on delete, file moved to app-private `bin/`, removed from MediaStore; restore re-inserts; WorkManager daily purge > 30 days.
- Player: Media3 ExoPlayer. Images: Coil + zoomable.
- App lock: androidx.biometric (BIOMETRIC_STRONG or DEVICE_CREDENTIAL).
- Permissions: POST_NOTIFICATIONS (asked when he turns notifications on, with reason); READ_EXTERNAL_STORAGE (≤ API 29 only). No INTERNET.
- Repost: `ACTION_SEND` with `setPackage("com.whatsapp")`.

## 10. Fixed rules
- Full-fledged app. About: Sushant Kumar · sushant.sare@gmail.com only. No clipboard features.

## 11. Approval notes (must-haves from his approval)
- **Viewer swipe must be perfectly smooth, no jitter:** use Compose `HorizontalPager` (beyondViewportPageCount = 1) with stable keys; pre-decode neighbour images with Coil at screen size (memory cache) and pre-load the next/previous thumbnail as placeholder so no blank frame; one shared ExoPlayer instance attached only to the settled page (prepare on `settledPage`, pause others) so video setup never blocks the swipe; blurred background crossfades (no recomposition of the whole screen per scroll frame — read `currentPageOffsetFraction` only in graphicsLayer/draw lambdas); no layout jumps (card size fixed); target 60/90/120 fps with zero dropped frames on swipe — verify with a Macrobenchmark/JankStats check in the audit.
