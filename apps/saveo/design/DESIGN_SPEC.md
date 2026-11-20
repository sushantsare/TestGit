# Saveo 2.0 design spec — Grabbit identity

Saveo 2.0 was rebuilt using the same visual identity as Grabbit (apps/grabbit/design/DESIGN_SPEC.md).

## Tokens
- **Light theme:** primary #AD3600, on-primary #FFFFFF, background #FFF8F5. Surface containers use Grabbit's warm neutrals.
- **Dark theme:** background #121212, primary #FFB597, on-primary #5A1B00. The optional *Pure black* setting uses #000000 surfaces.
- **On-image colours (viewer and tiles):**
  - Save chip: white with an orange #C2410C icon. Once saved it turns green #1B6D35 with a tick.
  - Eye chip: white with a grey crossed-out eye. When the item is visible in Gallery it turns solid orange #C2410C.
  - Scrim: 55% black.
- **Font:** Plus Jakarta Sans (bundled) — ExtraBold headlines, all-caps overline section labels, and tabular figures for counts.
- **Shapes:** cards 24dp, media tiles 20dp, chips 8dp, buttons pill-shaped.
- **Navigation:**
  - Phones: a bottom bar with Statuses, Saved and Settings. It hides while selecting items or while the viewer is open.
  - 600dp and wider: a navigation rail.
  - 840dp and wider: two panes, with the viewer on the right.
- **Settings:** grouped rounded cards with plain leading icons. Theme and "New saves" use segmented buttons; Pure black is a switch.
- **App icon:** option 3, cream with orange cards (design/app-icon.svg). The splash screen and window background follow the theme.

## Text change
- The viewer's primary action on a status now says **Save** (it was "Save to gallery"). New saves stay private by default, so "gallery" was misleading.

## Contrast (WCAG)
| Pair | Colours | Ratio |
|---|---|---|
| Light: white on primary | #FFFFFF on #AD3600 | 6.35:1 |
| Light: primary on background | #AD3600 on #FFF8F5 | 6.05:1 |
| Dark: on-primary on primary | #5A1B00 on #FFB597 | 7.73:1 |
| Dark: primary on background | #FFB597 on #121212 | 10.98:1 |
| Viewer: orange icon on white chip | #C2410C on #FFFFFF | 5.18:1 |
| Viewer: white on saved green | #FFFFFF on #1B6D35 | 6.39:1 |
| Snackbar action on snack | #FFB597 on #392E2A | 7.70:1 |
| Viewer pill text on white | #AD3600 on #FFFFFF | 6.35:1 |

All pairs pass 4.5:1.
