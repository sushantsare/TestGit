# Saveo — Competitor research (Nov 2026)

## Leading apps in the category
- **Status Saver · Status Downloader** (10M+ downloads, ~224K reviews) — view → save/share in a tap; ads + IAP.
- **Status Saver: Video Downloader** (4.8★, ~150K reviews) — users praise auto-download and the "open WhatsApp" link; ads.
- **LibreStatus** (F-Droid, open source, 2026) — one-time system folder picker on Android 11+; no ads.
- **Dele: Status Saver & Recovery** (updated Oct 2026) — status saver + message recovery + direct chat bundle.
- Classic "Status Saver" layouts (help pages): Statuses tab | Saved tab (swipe), long-press multi-select, select-all, share/repost, delete.

## Common layout
- Top tabs: Images / Videos / Saved, or Statuses / Saved; 2–3 column thumbnail grid; download icon on each tile; full-screen swipe viewer with Save/Share/Repost bar; long-press multi-select.

## What users like
- One-tap save, auto-save, "open WhatsApp" shortcut, few/no ads, full quality.

## Common complaints (to fix)
- Statuses missing on Android 11+ (wrong folder / access lost) → clear one-time folder setup with WA / Business / dual picker and a re-grant fix screen.
- Must watch full video first before it appears (WhatsApp caches it only after viewing) → explain in empty state.
- Heavy ads, bloat (fonts, direct chat, trackers) → none.
- Saved items vanish when WhatsApp clears the cache → Saveo copies to its own gallery album (permanent).
- Duplicate saves → mark tile with tick and skip duplicates (content hash).

## Technical notes
- Status folder: Android 11+ `Android/media/com.whatsapp/WhatsApp/Media/.Statuses` (Business: `com.whatsapp.w4b/WhatsApp Business/...`); older: `WhatsApp/Media/.Statuses`. Access via SAF `ACTION_OPEN_DOCUMENT_TREE` with initial URI + persistable permission (no MANAGE_EXTERNAL_STORAGE).
- Photo + music statuses are delivered as MP4 video → detect by MIME/extension, not by assumption.
- Dual/clone WhatsApp lives in a separate user profile (e.g. user 95/999) on many phones; usually not reachable by other apps — offer "add another folder" and best-effort.
- Save via MediaStore into Pictures/Saveo and Movies/Saveo (album "Saveo") — no storage permission needed on Android 10+.
