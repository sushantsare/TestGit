# Vision notes — Bahi Khata

**Distribution: personal** (APK, not Play Store)

## His vision (word for word)
> Okay, so I want to build another app which can help me manage my expenses and track my flow of money or income of money or whatever it is. And in that I can also take note or attach something like this. Like cards, like from which it has been deducted, credit card, and if it is credit card then which one, like, and if it is debit card or whatever, like that, you understand. So help me clear it.

## Q&A
### Purpose / relation to PaisaBook
- New separate app (PaisaBook stays) that also includes an udhar ledger.
- Name: **Bahi Khata** (his own pick, after 20 suggestions).

### Money sources
- Credit cards (each named), debit cards / bank accounts, UPI apps (linked to a bank account), cash + wallets.
- Track balances and card bills (limit, current dues, bill date, due date).
- Starting balances: first-launch setup with opening balance per account; credit card = limit + current outstanding + bill/due dates; existing udhar per person as opening balance; opening balance is not income; "Adjust balance" entry to match real balance; setup can be skipped. **Approved.**
- Transfers: no general Transfer type; **only a "Pay card bill" button** (bank → card, not counted as expense) **and a "Withdraw cash" button** (bank → cash, not counted as expense).
- Summary shown and confirmed.

### Adding entries
- Manual quick-add; auto-read bank SMS (entries need his OK first); recurring entries; scan receipt with camera.
- Attachments: photos (camera/gallery), PDF/any file, multiple per entry; tags (#food, #trip).
- Merchant memory (merchant → usual category + card).

### Reports & planning
- Monthly charts by category; per-card/per-account report; budgets with alerts; card bill due reminders; calendar view; search + filters; savings goals; monthly report PDF; credit card cycle insight (which card gives the longest interest-free period today).

### Udhar
- Person-wise balance; share reminder via WhatsApp.
- Split a bill with friends → shares go into udhar.

### Safety & data
- App lock (fingerprint/PIN); backup/restore to file; export Excel/CSV/PDF; Google Drive auto-backup **with an on/off switch**.
- 100% offline, no ads, no login (Drive backup is the only internet use, sign-in only for Drive).
- Hide amounts toggle.

### Look & feel
- Home: this month summary (in/out/balance) first, then recent entries.
- English UI, editable ready-made categories.
- Light + dark, follows phone, with a Settings switch.
- Reference: Google Pay (clean, simple).

### Android extras
- Daily reminder to log; long-press app icon shortcuts (add expense / add income).
- Not picked: home-screen widget, Quick Settings tile.

### Devices
- Phone mainly, rotation allowed; tablet layout still built in.

### Success
- Add an entry in under 5 seconds; balances always match bank; never miss a card due date; opens instantly and smooth.

### Analytics
- Default: local crash log + local usage stats, Diagnostics screen; nothing leaves the phone.

## Not picked / rejected
- General Transfer type (only Pay card bill), widget, Quick Settings tile.
- Names not chosen: the other 20 suggestions (Hisaab, Kharcha, PaisaFlow, …).

## Design picks
- Style: **2 · Warm ledger** (cream paper, deep indigo ink #2C3E8C, saffron #E68A1E FAB/accents, Manrope, 16dp corners). Not picked: 1 Clean & bright, 3 Dark & premium.
- App icon: mix of #2 (rupee page) + #5 (wallet) → variation **C "Wallet with rupee clasp"**, then revised per his feedback: ₹ moved onto the wallet body (clasp plain saffron with dot), wallet made bigger. Locked as design/icon/app-icon.svg. Rejected: icons 1, 3, 4, 6 (single), mixes A and B.
- Layouts: Home = **A · Month summary first** (net/received/spent card, SMS alert, card bills due, recent). Add entry = **C · Step by step** (amount → category grid → account & details). Rejected: Home B, Home C, Add A, Add B.
  - Design decision to keep the <5 s goal with step-by-step: tapping a category moves to the next step instantly (no Next tap); if the merchant is remembered, steps 2–3 are pre-filled and the flow jumps to the final confirm step.

- Mockups: approved all screens. Extra requirement (his words): "a very responsive system which detects SMS and collects useful data out of that... so good that it doesn't fail every single time whenever there is a financial message". → SMS engine spec added (app-spec §11).
- **Approved** by him on 21 Nov 2026 (prototype + mockups + spec). Skipped as not applicable (personal app): target-user survey, real-user prototype test.
