# Bahi Khata — Design spec (approved 21 Nov 2026)

Source of the visuals: `design/mockups/` (25 phone screens, dark, tablet, landscape) and `blueprint/prototype.html`.
Code tokens live in `app/src/main/java/com/sushant/bahikhata/ui/theme/Theme.kt` — screens use only these.

## Identity
- Style **Warm ledger** (his pick, option 2): calm, traditional khata feel — cream paper, deep indigo ink, saffron touch.
- Mood words: trustworthy · calm · Indian · clear.
- Font: **Manrope** (bundled TTF, 400/500/600/700/800). Devanagari falls back to the system font (only the WhatsApp reminder text is Hindi).
- Shapes: cards 16dp, chips 10dp, buttons pill, FAB 18dp, sheets 28dp top.
- Icons: Material Symbols / Material Icons **Outlined** everywhere; selected nav item uses Filled.

## Colour (light / dark)
| Role | Light | Dark |
|---|---|---|
| background / surface | #FBF6EE | #16130F |
| card | #FFFDF8 (border #E6DACA) | #211D18 (border #3A342C) |
| primary (ink) | #2C3E8C | #B9C4FF |
| secondaryContainer (selected, alerts) | #FCE3C2 / on #5A3300 | #5A3A12 / on #FFDDB5 |
| saffron (FAB, card colour picker ring) | #E68A1E on #1E1B16 | same |
| income | #1F7A45 | #7DD9A0 |
| expense | #B3261E | #FFB4AB |
| info banner | #E3E7F7 / #2C3E8C | #26304F / #C7D0FF |
Money is never colour-only: amounts carry + / − and labels ("they owe", "you owe").
10 category colour pairs (container/content) for both themes are in `BkColors.cat`.

## Icon
Wallet with ₹ on the body, saffron clasp, receipt peeking out, on indigo (`design/icon/app-icon.svg`); adaptive foreground/background + monochrome layer in `res/drawable/ic_launcher_*`. Splash = foreground on cream.

## Layout decisions (his picks)
- Home **A · Month summary first**: month switcher → Net card (received / spent, vs last month) → SMS alert chip → Card bills due → Budgets (top 3) → Recent (8). FAB bottom-right.
- Add entry **C · Step by step**: 1 amount (calculator keypad, shop field with remembered suggestions, Pay card bill / Withdraw cash chips) → 2 category grid (tap = next) → 3 account grid + UPI app chips + details (date, note, tags, split, repeat, attachments) → Save. Remembered shop jumps straight to step 3.
- Bottom nav (5): Home · Entries · Accounts · Udhar · Reports. ≥600dp: navigation rail with Add on top. ≥840dp: Entries / Accounts / Udhar become list + detail panes; Home splits into two columns.

## States
Every list has empty states (Home, Entries, Udhar, Accounts, SMS tabs, Search no-results), loading via splash until the first DB read, permission rationale screen for SMS, notification "blocked · Allow" row, confirm dialogs for deletes/reset/restore and Undo snackbars.

## Copy
Sentence case, plain words, ₹ Indian grouping (1,00,000), dates "21 Nov", times 12-hour. About: "Developed by Sushant Kumar" + sushant.sare@gmail.com only.
