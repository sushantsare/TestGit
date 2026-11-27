# Changelog

## 1.0.0 · 21 Nov 2026
First version: money in and out per bank account, credit card, cash and wallet with opening balances; 3-step add entry with calculator, remembered shops, tags, notes, photo/PDF attachments, splits and repeats; card bills, due dates and best-card-today; pay card bill and withdraw cash (not spending); bank SMS turned into one-tap suggestions with a "Needs a look" safety net and learning; udhar per person with WhatsApp reminders; budgets, savings goals, reports, calendar, search; CSV / Excel / PDF export; encrypted backup file and Google Drive backup; app lock, hide amounts, light and dark.

### 1.0.0 audit rounds (22 Nov 2026)
- Three independent audits (D 52% → C 77% → C 84.8%); fixed: split edits, safe routes from other apps, encrypted-only storage, restore safety, discard prompts, saved dialog state on rotation/kill, lock-screen privacy, accessibility (ATF clean on 14 screens), keyboard shortcuts and typing, landscape/large-font layouts, budget start day, custom report period, editable minimum due, reminder days, category order, SMS swipe, battery guide, SMS never lost if the app is killed mid-way, cold start under 1.2 s.

## 1.0.1 — 22 Nov 2026
- New back and open motion that follows Android's standard: going back (button or swipe) shrinks the current screen toward your finger while the previous one settles in; the swipe follows your finger and can be cancelled. Opening a screen slides it in from the right; switching bottom tabs is a quick fade.

## 1.0.2 — 22 Nov 2026
- Back swipe now really follows your finger on Android 14+ (the navigation library is back on the version proven in your other apps).
- Lock screen: "Bahi Khata is locked" is light text in dark mode (it was black on dark).

## 1.0.3 — 22 Nov 2026
- Back now looks the same on every screen: tabs (Udhar → Home), Settings and every page inside Settings all shrink into a card over the previous screen, like Android's own back-to-home.
- The bottom tab bar moves with its screen, so nothing jumps when you swipe back from Settings or any other page to a tab. On tablets the side rail stays in place on every screen.

## 1.0.4 — 22 Nov 2026
- Back rebuilt from scratch: the back button, the back swipe, tapping Home and switching tabs all use the same quick fade the bottom tabs use. Opening a screen uses it too, so every move in the app looks the same.

## 1.1.0 — 26 Nov 2026
- Hide amounts: its own page with a switch for each area (Home, Entries, Accounts, Udhar, Reports, Notifications). Hiding is one tap; showing again, or turning hiding off, needs your fingerprint. The app remembers your last choice.
- Name your UPI IDs: a new UPI ID asks "Name this?" (shop or person, plus a category) once, then fills itself in. Saved names have their own page in Settings (rename, link a person, merge, delete).
- "What did you buy?" after a payment, from the notification or in the app, with a price per item and how much is still not given to an item. Items are remembered per shop, with price history.
- A notification goes away as soon as you save, ignore or delete that payment.
- From your SMS: balance check against the bank's balance, failed and reversed payments cancel by themselves, refunds link to the purchase, your own transfers, ATM cash, subscriptions, recharge/bill/EMI/AutoPay reminders, credit card statement, due and limit used, unusual payment alerts, bank charges, salary and payday view, cashback and interest as income, investments and insurance, FASTag and wallet balances, people summary, yearly summary, and a one-time read of older bank SMS.
- Pure black option for dark mode.
- App lock also locks when the screen turns off, and never after the app's own pickers or share screens.
- New opening animation and a 9-step setup guide on the first opening.
- Search opens smoothly out of the search button and shrinks back.
- Undo after deleting an account; tablet Add entry opens as a side panel; faster start-up.

## 1.1.1 — 27 Nov 2026
- Items now have a quantity (pcs, kg, g, L, ml, pack, dozen) and the total paid; the app shows the price for one (or per kg / per litre). Price history compares the price for one. The notification reply understands "milk 2L 60, 3 eggs 21".
- Block screenshots: turning it off now really allows screenshots. App lock no longer blocks screenshots; it only hides the app's preview in recent apps.
- Entry details: proper gap between the items card and the details card.
- Google Drive backup: the Google project is now set up, and any failure shows Google's real reason instead of "isn't available on this phone".

## 1.1.2 — 27 Nov 2026
- Add entry: items go in right on the first screen — tap "Items", or tap a "Usual here" chip for the shop (last quantity and price). With no amount typed, the amount becomes the items' total.
- Add entry: type "Ravi Kirana: milk 2L 60, bread 40" in the shop box and Next fills the shop, items and amount.
- Add entry: when the phone keyboard opens, the number pad steps aside so the shop name stays in view; tap the amount or close the keyboard to get it back.
- Entry page: the "What did you buy?" strip shows only for SMS entries; manual ones have a quiet "Add items" row.
- Price history reads "per L" / "per kg".
