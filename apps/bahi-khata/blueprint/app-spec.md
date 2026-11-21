# Bahi Khata — App Spec

**Distribution: personal** · Package: `com.sushant.bahikhata` · Security level: **MASVS L2** (money + records about other people) · Dependency wiring: **Hilt** (>10 screens, several data sources)

## 1. Vision brief
- **Name:** Bahi Khata
- **Pitch:** Your personal money book — every rupee in and out, from which card or account, with receipts, udhar and card bills in one offline app.
- **User:** Sushant (personal use).
- **Success goals:** add an entry in < 5 s; balances always match the bank; never miss a card due date; cold start < 1.5 s, smooth scrolling.

## 2. Features
### v1
1. **Accounts:** Bank account (with debit card name/last-4 optional), Credit card (limit, outstanding, statement day, due day, colour), Cash, Wallet. UPI app names (GPay, PhonePe, Paytm, BHIM, other) are a "paid via" label linked to a bank account. Opening balance per account (not income). "Adjust balance" creates an adjustment entry. Archive account.
2. **First-launch setup:** welcome → add accounts with today's balances (skippable) → add existing udhar (skippable) → app lock offer → done.
3. **Entries:** type Expense / Income; amount (calculator keypad), category, account (+ UPI app label), date & time, merchant/payee, note, tags (#), multiple attachments (camera, gallery, PDF/any file). Edit, duplicate, delete with undo.
4. **Pay card bill:** bank/cash → credit card; reduces card outstanding and bank balance; not counted as spending.
5. **Withdraw cash:** bank → Cash; not counted as spending.
6. **Quick add:** FAB → entry sheet opens with keypad focused; last-used account & merchant memory pre-fill; save in ≤ 3 taps.
7. **Merchant memory:** merchant name → remembered category + account; suggestions while typing.
8. **Recurring entries:** daily/weekly/monthly/yearly; auto-add on date (WorkManager) with notification; pause/edit.
9. **SMS engine (core feature — must not miss financial SMS):** see Section 11 for the full spec. Summary: instant detection on arrival, catch-up scans so nothing is missed, smart classification (transaction / bill / due / refund / failed / OTP / promo), rich data extraction, duplicate merging, learning from his corrections, and a safety net that shows every unparsed money SMS for review instead of dropping it. Entries are never saved without his OK (one-tap OK from the notification).
10. **Categories:** ready set (Food, Groceries, Travel, Fuel, Rent, Bills & Recharge, Shopping, Health, Education, Entertainment, EMI, Gifts, Others; income: Salary, Freelance, Interest, Refund, Gift, Others) with icon + colour; add/edit/reorder/merge.
11. **Credit cards:** per-card screen — limit, used, available (bar), statement date, due date, days left, current bill amount, minimum due (5% estimate, editable); "Pay card bill" button; transactions this cycle.
12. **Card cycle insight:** on Accounts and card picker — "Best card today: X — up to N interest-free days" (days from today to the due date after the next statement).
13. **Due reminders:** notifications 5 days, 2 days and on the due day (configurable) per card.
14. **Udhar (People):** person list with net balance (green "they owe you" / red "you owe"), opening balance, entries "You gave" / "You got" linked optionally to an account, running balance, settle up; WhatsApp reminder (opens WhatsApp with a prefilled polite message in Hindi or English + amount; phone number optional, picked from contacts or typed).
15. **Split a bill:** on an expense, split equally or by amounts among people; each person's share goes to their udhar.
16. **Budgets:** monthly per category + overall; progress bars; alerts at 80% and 100%.
17. **Savings goals:** name, target, target date, add money manually; progress.
18. **Reports:** month/custom period; category donut; income vs expense bars by month; per-account/per-card spending; top merchants; tag report; monthly report PDF (share).
19. **Calendar view:** month grid with daily spend; tap day → entries.
20. **Search + filters:** text (merchant, note, tag), date range, type, category, account/card, amount range; saved results open entry detail.
21. **Hide amounts:** eye icon in top bar, masks all amounts (₹ ••••); remembered.
22. **App lock:** biometric / device PIN (BiometricPrompt), lock after 1 min in background (configurable); FLAG_SECURE on all screens (toggle in Settings, default on).
23. **Backup & export:** backup to encrypted file (DB + attachments zip, password) & restore; export CSV and Excel (.xlsx) and PDF for any period; **Google Drive auto-backup** with on/off switch (default off), daily/weekly, Wi-Fi only option, last backup time, restore from Drive.
24. **Daily reminder:** evening notification if nothing logged today (time configurable, on/off).
25. **App shortcuts:** long-press icon → Add expense, Add income, Udhar.
26. **Settings:** theme (system/light/dark), currency ₹ fixed, first day of month for the budget cycle, categories, accounts, notifications, security, backup, SMS, Diagnostics, About.
27. **Diagnostics:** local crash log + usage counts; export/share log file.
28. **About:** Sushant Kumar · sushant.sare@gmail.com; version.

### Later
- Home-screen widget, Quick Settings tile, Hindi UI language, general Transfer type, OCR to read amount from receipts, multi-currency.

### Out of scope
- Ads, accounts/login (except Google sign-in only for Drive backup), bank linking, cloud servers, clipboard features.

## 3. Screen map
```
Lock screen (if on)
First-launch setup (Welcome → Accounts → Udhar → Lock → Done)
Bottom nav:
 ├─ Home (month summary, budgets glance, due cards, SMS inbox chip, recent) → Entry detail → Edit
 ├─ Entries (list by day, search, filters, calendar toggle) → Entry detail
 ├─ Accounts (net worth, banks, cards, cash, wallets, best card today) → Account/Card detail → Pay card bill / Withdraw cash / Adjust balance
 ├─ Udhar (people list) → Person detail → Add gave/got, WhatsApp reminder
 └─ Reports (charts, budgets, goals, export/PDF)
FAB (Home/Entries) → Add entry sheet (Expense | Income) → Category picker, Account picker, Attachments, Split
SMS Inbox → Review suggestion
Top bar: Search, Hide amounts, Settings
Settings → Categories, Budgets, Goals, Recurring, Security, Backup & Drive, Notifications, SMS, Diagnostics, About
```

## 4. Screen specs (summary; mockups are the visual source)
- **Home:** top bar (title "Bahi Khata", eye, search, settings) → month switcher → summary card (Spent / Received / Net, vs last month) → SMS inbox chip ("3 entries need your OK") when any → "Cards due soon" row → budget glance (top 3) → Recent entries (8) → "See all". Empty: friendly illustration + "Add your first entry" + "Set up accounts". FAB bottom-right "+".
- **Add entry sheet:** segmented Expense/Income at top → large amount with keypad (calculator ops) → chips row: category (recent 6 + More), account (last used + More), date (Today) → merchant field with suggestions → expand "More": note, tags, attachments strip, split, recurring → Save (bottom, full width). Expense/income/card-bill/withdraw also reachable.
- **Entries:** search field → filter chips → list grouped by day with day totals → list/calendar toggle. Empty search state.
- **Entry detail:** amount, type, category, account, date, merchant, note, tags, attachments grid (tap → full-screen viewer/PDF open), split info; edit/duplicate/delete.
- **Accounts:** net worth card → "Best card today" insight → sections Banks, Credit cards (utilisation bar, due in N days), Cash, Wallets → "+ Add account".
- **Card detail:** card visual in its colour → limit/used/available → bill & due dates → Pay card bill button → this-cycle entries.
- **Udhar:** totals (You'll get / You'll give) → people list with coloured balance → "+ Add person". Person detail: balance card, WhatsApp reminder button, entry list, You gave / You got buttons at bottom.
- **Reports:** period switcher → donut by category → income vs expense bars → per account/card → top merchants → Budgets → Goals → Export (PDF report, CSV, Excel).
- **SMS inbox:** cards with parsed amount, account match, merchant, original SMS text (expandable), Accept / Edit / Ignore.
- States for every list screen: empty, loading (skeleton), error (retry). Permission denied states: SMS (explain + "Allow" + "Not now"), camera, notifications, contacts.

## 5. Design system
Filled in by android-ui-designer → see `design/DESIGN_SPEC.md`. Direction: Google Pay-like — clean, light surfaces, large rounded cards, one strong accent, green for money in, red for money out.

## 6. Adaptive layout
- Compact: bottom nav (5), single pane, FAB.
- Medium: navigation rail, two-column cards on Home/Accounts/Reports.
- Expanded: navigation rail, list-detail two-pane for Entries, Accounts, Udhar; add-entry as side sheet/dialog; content max width 840dp for forms.
- Rotation keeps state (ViewModel + SavedStateHandle); split-screen and free-form windows; keyboard shortcuts on large screens (Ctrl+N new entry, Ctrl+F search, Esc back), hover and focus states.

## 7. Quality standards
- Accessibility: content descriptions on all icons/images; contrast ≥ 4.5:1; touch targets ≥ 48dp; 200% font works; amounts read as "rupees".
- Localization: all text in string resources; English UI (Hindi strings prepared later); WhatsApp reminder text Hindi/English choice.
- Privacy & security (MASVS L2): Room DB encrypted with SQLCipher, key in Android Keystore; attachments stored in app-private storage (encrypted); backups encrypted with user password; `allowBackup=false`, data-extraction rules exclude data; FLAG_SECURE; no API keys in APK; SMS content processed on device only and only bank-like senders kept.
- Performance: cold start < 1.5 s (baseline profiles), Paging for long lists, images thumbnailed.
- Reliability: plain-word errors with retry; Room migrations from v1 with exported schemas; balances computed from entries (single source of truth) to always reconcile.
- Testing: unit tests for balance maths, card cycle maths, SMS parser (sample SMS from SBI, HDFC, ICICI, Axis, Kotak, PNB, BOB, Paytm, GPay/PhonePe-style), recurring scheduler; UI tests for add entry, pay card bill, udhar; phone, tablet and landscape screenshots.
- Release: versionName 1.0.0 / versionCode 1; targetSdk 36; minSdk 26; signing keys in GitHub secrets.

## 8. Analytics & crash reporting
Local only: crash handler → local log; local usage counts; Diagnostics screen with export/share. No Firebase. No personal content logged.

## 9. Technical plan
- Kotlin, Jetpack Compose, Material 3, MVVM + Hilt, Navigation Compose (type-safe), Room + SQLCipher, DataStore, WorkManager, BiometricPrompt, CameraX (receipt capture), Photo Picker + SAF (files), Vico (charts), Apache POI-lite / FastExcel for .xlsx, Android PdfDocument for PDFs, Google Sign-In (Credential Manager + Authorization API) + Drive REST (appDataFolder scope) for backup, kotlinx.serialization, Coil.
- **Room tables:** `accounts(id, type[BANK|CREDIT_CARD|CASH|WALLET], name, bank, last4, colour, icon, openingBalance, creditLimit, statementDay, dueDay, archived, sort)`; `categories(id, name, kind[EXPENSE|INCOME], icon, colour, sort, archived)`; `entries(id, type[EXPENSE|INCOME|CARD_PAYMENT|CASH_WITHDRAWAL|ADJUSTMENT|OPENING], amountPaise, accountId, toAccountId?, categoryId?, upiApp?, merchant?, note?, dateTime, recurringId?, smsId?, splitGroupId?, createdAt, updatedAt)`; `tags(id,name)`, `entry_tags(entryId, tagId)`; `attachments(id, entryId, uri, mime, name, size, createdAt)`; `merchants(name PK, categoryId, accountId, useCount, lastUsed)`; `recurring(id, template json, rule, nextDate, active)`; `people(id, name, phone?, openingBalancePaise, note)`; `udhar_entries(id, personId, direction[GAVE|GOT], amountPaise, accountId?, entryId?, note, dateTime)`; `budgets(id, categoryId?, monthlyLimitPaise)`; `goals(id, name, targetPaise, targetDate?, savedPaise, colour)`; `goal_deposits(id, goalId, amountPaise, date)`; `sms_suggestions(id, sender, body, receivedAt, parsed json, status[PENDING|ACCEPTED|IGNORED], entryId?)`.
- Money stored as Long paise.
- **Permissions (asked in context, reason shown first):** RECEIVE_SMS + READ_SMS (when enabling SMS suggestions; denied → manual only); POST_NOTIFICATIONS (first reminder setup); CAMERA (when taking receipt photo; gallery/files need none); READ_CONTACTS optional (pick person; denied → type name/number); USE_BIOMETRIC; INTERNET (Drive backup only); SCHEDULE_EXACT_ALARM not needed (WorkManager / inexact alarms).
- Background: WorkManager for recurring, due reminders, daily reminder, Drive backup.

## 10. Fixed rules
Full-fledged app; About shows Sushant Kumar · sushant.sare@gmail.com only; no clipboard features.

## 11. SMS engine — detailed spec
Goal: every financial SMS on the phone becomes either a correct suggestion, a bill/due update, or (if unsure) an item in "Needs a look". Nothing financial is silently dropped.

**A. Never miss an SMS (3 layers)**
1. *Instant:* manifest `SMS_RECEIVED` BroadcastReceiver (high priority) → hands the message to an expedited WorkManager job (works even if the app is closed; < 2 s to notification).
2. *Live observer:* `ContentObserver` on the SMS inbox while the app process is alive (catches messages some OEM/RCS setups don't broadcast).
3. *Catch-up scan:* on every app open, on boot (`BOOT_COMPLETED`) and every 6 h (periodic WorkManager), read the inbox from the last processed SMS id/date. On first enable, scan the last 90 days.
- Every processed SMS id is stored (`sms_log`) so nothing is handled twice and nothing is skipped.
- Battery: receiver does no parsing on the main thread; OEM battery-optimisation guide screen (Xiaomi/Vivo/Oppo/Samsung) offered once if the catch-up scan finds SMS the receiver missed.

**B. Sender filter**
- Indian DLT headers like `VM-HDFCBK-T`: since May 2025 a suffix shows type — P promotional, S service, T transactional, G government. Accept `-T`, `-S` and old/unsuffixed headers; skip `-P` unless the body clearly looks like a transaction (money amount + debit/credit word).
- Built-in sender dictionary (~150 entries): all major public/private/small-finance banks, card issuers, UPI apps (GPay, PhonePe, Paytm, BHIM, CRED, Amazon Pay, Navi, super.money), wallets, NBFC/EMI lenders; maps header → bank name. Unknown senders with transaction-like bodies still go through the parser.

**C. Classification (before extraction)**
Debit/spend · Credit/receive · Refund/reversal (→ income) · Failed/declined (ignored, but if a matching debit exists it's flagged) · ATM withdrawal (→ Withdraw cash suggestion) · Card bill generated (→ updates card bill amount, min due, due date) · Payment due reminder · Card payment received (→ Pay card bill suggestion) · Autopay/mandate/EMI · Balance alert · OTP / login / promo / KYC (ignored, never shown).

**D. Extraction (per SMS)**
Amount (Rs, Rs., INR, ₹, with commas/decimals, "1.2K" never assumed) · direction · account/card last 4 (XX1234, *1234, x1234, "ending 1234", A/c no. XXXX1234) · merchant / payee / UPI VPA (at…, to…, VPA name@bank, Info:, towards…) · UPI ref / txn id · date & time (SMS body date preferred, else received time) · available balance ("Avl Bal", "Bal") · available credit limit · bill amount, minimum due, due date.
- Implementation: ordered regex template library (bank-specific templates first, then generic templates), tolerant to line breaks, case, extra spaces and Hindi/Hinglish bank SMS.
- Confidence score per field; low-confidence fields shown highlighted for him to confirm.

**E. Matching & smart fill**
- Card/account matched by last-4 → his account; unknown last-4 → one-time question "Which account is ••4417?" (remembered).
- UPI app detected from sender/body (GPay/PhonePe/Paytm) → "Paid via" label.
- Merchant memory and learned VPA → category; cleaned merchant names (e.g. "BUNDL TECHNOLOGIES"/"SWIGGY*" → Swiggy).
- **Balance check:** if the SMS shows Avl Bal and it differs from the app's balance for that account, show "SBI balance differs by ₹230 — adjust?" (helps "balances always match my bank").

**F. Duplicates**
Same amount + same account within 15 min, or same UPI ref/txn id, across bank SMS + UPI app SMS + card SMS → merged into one suggestion. Manual entry already added for the same amount/account within 30 min → suggestion marked "Already added?" with Merge.

**G. Safety net (the "never fails" part)**
- Any SMS from a financial sender, or any body with an amount + money keyword, that no template fully parses → goes to **"Needs a look"** tab in the SMS inbox with best-guess fields, never dropped.
- "Teach it": when he fixes a Needs-a-look item, the app saves a learned template (sender + text pattern → fields) so the same format parses automatically next time.
- "Missed something?" button: lists recent financial-looking SMS that were ignored, so he can bring any back in one tap.

**H. Speed of confirming**
- Notification per suggestion with actions **OK, save** / **Edit** (grouped when several arrive); OK from the notification saves without opening the app.
- In-app inbox: swipe right = OK, swipe left = Ignore; "OK all" for high-confidence items.

**I. Quality bar & tests**
- Test corpus of 600+ realistic SMS formats across 40+ banks/issuers/UPI apps (debit, credit, refund, failed, ATM, bill, due, autopay, OTP, promo) checked in unit tests.
- Targets: 0 missed financial SMS (every one lands in Suggestions or Needs a look); ≥ 97% fully-correct auto-parse on the corpus; 0 OTP/promo shown as transactions.
- Parser is a pure Kotlin module with no Android dependency so it is fully unit-tested; Diagnostics shows parse stats (parsed / needs a look / ignored).
- Privacy: SMS bodies stay on the phone; only financial SMS are stored (encrypted), others are never saved.
