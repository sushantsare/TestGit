# Grabbit 2.0 — Design spec

## Direction
References: **Seal** and **YTDLnis** (open-source yt-dlp apps, clean Material You, but generic and settings-heavy),
**Snaptube/VidMate** (fast paste-and-grab flow, but cluttered with ads and banners).
Grabbit takes the clean Material 3 structure, keeps the one-step "paste → grab" flow front and centre,
and adds a confident identity of its own: tangerine accent, big friendly headings, large rounded cards,
thumbnail-led results. No ads, no clutter.

- **Mood words:** fast, bold, clean, friendly, trustworthy
- **Preset:** Material You (tonal surfaces, 24dp containers, pill buttons), dark mode neutral charcoal
- **Seed colour:** tangerine (#F25A1D brand; light primary #AD3600, dark primary #FFB597)
- **Font:** Plus Jakarta Sans (OFL), bundled in `res/font` (400/500/600/700/800). One family.
  Hindi UI is not added for now (owner's choice); system fallback renders Hindi titles of videos.
- **Shapes:** rounded; cards/groups 24dp, media 20dp, fields 16dp, chips/thumbs 12dp, buttons pill
- **Icons:** Material Icons Rounded; nav bar selected = filled, unselected = outlined
- **Icon & splash:** tangerine gradient tile; white glyph = two rabbit ears forming a download arrow over a tray;
  monochrome layer for themed icons; AndroidX SplashScreen with the glyph on an orange circle.

## Tokens
`ui/theme/`: `Color.kt` (light, dark, pure-black, semantic success, brand gradient, image scrim),
`Type.kt` (M3 scale, heavier headlines, overline, tabular digits), `Shape.kt`, `Dimens.kt`
(spacing 2/4/8/12/16/24/32/48, sizes), `Motion.kt` (100–400 ms, emphasized easing), `Theme.kt`
(`GrabbitTheme`: theme mode, pure black, wallpaper colours on Android 12+).
Contrast results for every pair: `contrast.md` (all pass: text ≥ 4.5:1, outlines ≥ 3:1).

## Screens
| Screen | Purpose | Key actions | States |
|---|---|---|---|
| Grab (home) | Paste a link and download | Grab video, pick Video/Audio + quality, Download | idle (hero + how it works), engine setting up, engine failed, loading (skeleton), error (Retry, Details), loaded (media card) |
| Downloads | Progress and saved files | Cancel, Retry, Other format, Play, Share, Open post, Delete, search, sort, site filter | first-time empty (illustration + Grab a video), in progress, failed, saved list, no results (Clear filters), file missing |
| Settings | Preferences | Theme, pure black, wallpaper colours, default quality (bottom sheet), subtitles, album art, accounts log in/out, engine update | updating, logged in/out |
| Login (per site) | Save cookies for private videos | Done, Back | page loading |

Navigation: bottom bar (Grab / Downloads / Settings), fade-through between tabs, Back from any tab returns to Grab,
share-sheet links open Grab with the link filled. Messages appear as snackbars (no Toasts).

## Shared components (`ui/components/Components.kt`)
BrandMark, ScreenTitle, SectionLabel, AppCard, ImageTag, VideoThumb, SkeletonBlock, ErrorCard, EmptyState
(+ theme-coloured illustration), SettingsGroup / SettingsItem / SettingsSwitch / SettingsDivider, LetterAvatar, RowButton.

## Rules for the builder
Colours only from `MaterialTheme.colorScheme` (plus `ImageScrim`/`OnImage` on thumbnails and the fixed brand
gradient), type from `MaterialTheme.typography`, spacing from `LocalSpacing`, sizes from `LocalSizes`.
One filled button per state. Touch targets ≥ 48dp. About shows name + email only.

## Mockups
`mockups/` — rendered at 360×800 from `mock.html` (light, dark, pure black). Emulator screenshots are compared
against these before delivery.
