# Gallery — Design spec (handoff)

Source of truth for visuals. Code tokens live in `app/src/main/java/com/sushant/gallery/ui/theme/` (Color, Type, Shape, Dimens, Motion, Theme) and shared components in `ui/components/`. Approved prototype: `blueprint/prototype.html`; mockups: `design/mockups/`.

## Identity
- Mood: calm, clean, quick, trustworthy. Preset: Material You, "Clean & bright".
- Seed teal **#00696B**, sun accent **#F5B841** (illustrations only). Font: **Plus Jakarta Sans** (bundled, 400/500/600/700). Icons: Material Symbols **Rounded**; filled = selected.
- App icon: seven slim liquid-glass petals (deep teal → sea blue → sun → peach → coral) on pale mint #F1FAF8; monochrome layer = 7 petal silhouettes. Files: `design/icon/`.
- Shapes: 4/8/12/16/28 dp; album cards 16 dp, filmstrip cards 18 dp, sheets 28 dp top, buttons full pill.

## Colour roles
Light: background/surface #F4FBFA · onSurface #161D1D · onSurfaceVariant #3F4948 · containers #FFFFFF/#EEF5F4/#E8EFEE/#E2E9E8/#DDE4E3 · primary #00696B/onPrimary #FFFFFF · primaryContainer #B4EEEC/#002020 · outline #6F7979/#BEC9C8 · error #BA1A1A · inverse #2B3231/#ECF2F1, inversePrimary #80D4D4.
Dark: background #0E1514 · onSurface #DDE4E3 · onSurfaceVariant #BEC9C8 · containers #090F0F/#161D1D/#1A2121/#242B2B/#2F3636 · primary #80D4D4/#003738 · primaryContainer #004F51/#9CF1F0 · error #FFB4AB.
Fixed: viewer #000000, filmstrip #0D1414, media text #FFFFFF, media scrim #000 60 %, trash target #B3261E, strip highlight #B4EEEC.
Dynamic colour (Android 12+) optional in Settings, off by default.

## Contrast (all pass ≥ 4.5:1)
| Theme | Pair | Ratio | Result |
|---|---|---|---|
| light | onSurface #161D1D (#161D1D on #F4FBFA) | 16.31:1 | PASS |
| light | onSurfaceVariant #3F4948 (#3F4948 on #F4FBFA) | 8.87:1 | PASS |
| light | onSurfaceVariant on container (#3F4948 on #E8EFEE) | 7.98:1 | PASS |
| light | onPrimary on primary (#FFFFFF on #00696B) | 6.50:1 | PASS |
| light | onPrimaryContainer (#002020 on #B4EEEC) | 13.35:1 | PASS |
| light | primary text on bg (#00696B on #F4FBFA) | 6.19:1 | PASS |
| light | error on bg (#BA1A1A on #F4FBFA) | 6.16:1 | PASS |
| light | inverse snackbar (#ECF2F1 on #2B3231) | 11.56:1 | PASS |
| light | snackbar action (#80D4D4 on #2B3231) | 7.66:1 | PASS |
| dark | onSurface (#DDE4E3 on #0E1514) | 14.33:1 | PASS |
| dark | onSurfaceVariant (#BEC9C8 on #0E1514) | 10.90:1 | PASS |
| dark | onSurfaceVariant on container (#BEC9C8 on #1A2121) | 9.64:1 | PASS |
| dark | onPrimary on primary (#003738 on #80D4D4) | 7.66:1 | PASS |
| dark | onPrimaryContainer (#9CF1F0 on #004F51) | 7.27:1 | PASS |
| dark | primary on bg (#80D4D4 on #0E1514) | 10.81:1 | PASS |
| dark | error (#FFB4AB on #0E1514) | 10.89:1 | PASS |
| dark | snackbar (#2B3231 on #DDE4E3) | 10.15:1 | PASS |
| dark | snackbar action (#00696B on #DDE4E3) | 5.04:1 | PASS |
| fixed | white on viewer black (#FFFFFF on #000000) | 21.00:1 | PASS |
| fixed | white on film bg (#FFFFFF on #0D1414) | 18.63:1 | PASS |
| fixed | white on trash red (#FFFFFF on #B3261E) | 6.54:1 | PASS |
| fixed | editor muted on panel (#AAAAAA on #111111) | 8.13:1 | PASS |
| fixed | editor chip text (#9CF1F0 on #004F51) | 7.27:1 | PASS |

## Spacing & sizes
4-dp grid: 2/4/8/12/16/24/32/48. Screen margin 16 dp (24 on ≥ 600 dp). Grid gap 2 dp, 4 columns (pinch 2–6; ×2 on tablets). Top bar 64 dp. Pin buttons 58 dp circles. Filmstrip card 290 dp tall (scales to 1.35× on tall phones, 62 % height on tablets), width = 0.586 × height, gap 14 dp, thumbnail strip 32×44 dp (selected 36×48 with 2 dp outline). Trash target 52 dp. PIN keys 76 dp.

## Motion
Durations 100/200/300/400/500 ms; emphasized (0.2,0,0,1). Viewer ⇄ filmstrip: fade + scale (1.15 / 0.85). Flick-up: card follows finger (rotate −0.02°/px), threshold 35 % of card height or fast fling, flies off in 220 ms; downward drag rubber-bands at 15 % and springs back. Haptic tick when crossing the delete threshold. Selection: cell scales to 0.86 with 10 dp corners.

## Screens (see mockups)
Onboarding · Permission denied · Photos (date groups, selection, menu, sort sheet, grid size) · Albums (round shortcuts Favourites/Videos/Recycle Bin(badge)/Hidden + 2-column cards; list-detail ≥ 840 dp) · Album · Viewer (auto-hiding bars, favourite, ⋮ rotate/set as/move/copy/hide, share/edit/details/delete, pinch hint) · Filmstrip (B: 3 cards, bin target, hint, strip) · Details sheet · Share sheet (remove location switch) · Move/Copy picker + New album · Search (recent chips, albums + files, highlight) · Recycle Bin (banner, days-left, restore/delete forever, empty) · Hidden album (create/enter PIN, fingerprint, encrypted grid, unhide/delete forever) · Editor (crop free/1:1/4:3/16:9, adjust, 8 filters, rotate/flip; save copy/overwrite) · Settings · Diagnostics · About.

## States
Every list: loading spinner (first scan), empty (illustration + title + one line), no results, permission denied. Errors are snackbars in plain words; destructive permanent actions confirm; reversible deletes use Undo.

## Tight-control fit (360 dp, 1.3× and 2.0× font)
- Theme segmented (System/Light/Dark): 312 dp row → 104 dp per segment − 24 dp padding = 80 dp; "System" at 14 sp ×2.0 ≈ 76 dp → fits; no tick icon.
- Sort order segmented (Newest first/Oldest first): 156 dp per segment − 24 = 132 dp; "Oldest first" ×2.0 ≈ 150 dp → wraps to 1 line with ellipsis risk at 2.0×; maxLines = 1 set (acceptable, label still readable at 1.3×).
- Crop chips (Free/1:1/4:3/16:9): 4 × ~60 dp + gaps = 270 dp → fits.
