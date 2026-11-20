# Competitor research — Android gallery apps (Nov 2026)

## Original reference: Android 4.1/4.2 Gallery filmstrip
- Pinch a full-screen photo smaller → row of shrunk photos ("filmstrip"); swipe left/right to scan; swipe a photo up (some builds up or down) to delete; UNDO to restore. (ZTE/Motorola manuals, PhoneArena Jelly Bean coverage.)
- Users complained when 4.1.1 builds lost the pinch-into-filmstrip gesture — it was the main way to delete.

## Leading apps
| App | Key features | Layout | Praise | Complaints |
|---|---|---|---|---|
| Google Photos | Cloud backup, AI search, editor | Bottom bar: Photos / Collections / Search | Search, sync | Slow, local files "load", videos hard to find, online-first |
| Samsung Gallery | Pictures/Albums/Stories tabs, editor, recycle bin, AI edits | Bottom tabs | Polished, fast | Samsung-only |
| Fossify Gallery (ex-Simple Gallery) | Recycle bin, PIN/pattern/fingerprint lock for photos or app, hidden folders, strip EXIF, editor (crop/rotate/flip/draw/filters), many formats | Folder grid → media grid | No ads, privacy, offline | Can lag when storage is full; dated UI |
| Aves | Many formats (TIFF, SVG, motion photos, 360), tags, map, batch ops, widgets, shortcuts | Collection + filters | Powerful metadata | Complex, Flutter-heavy |
| Focus Go / Piktures / A+ / F-Stop | Simple viewing; Piktures has secure space | Varies | Fast, clean | Ads, paid unlocks, bloat |

## Common review complaints to avoid
- Ads and popups; paid unlocks for basics.
- Slow loading with large libraries; thumbnails popping in.
- Online-first behaviour for local photos.
- Losing EXIF on edit/share (Fossify had this bug).

## Patterns to follow
- Material 3, bottom tabs Photos/Albums, date-grouped grid, pinch to change grid column count.
- Built-in recycle bin with 30-day restore.
- Hidden/locked album with biometric.
- Predictive back, edge-to-edge, dynamic colour optional.
