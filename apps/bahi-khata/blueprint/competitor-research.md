# Competitor research — Bahi Khata (personal money manager + udhar)

Researched 21 Nov 2026.

## Leading apps
| App | Strength | Common complaints |
|---|---|---|
| Money Manager (Realbyte) | 50M+ installs; manual, offline-first, double-entry accounts — balances, card settlement dates and transfers reconcile | Ad-supported free tier; dated UI; manual entry is slow |
| Walnut / Money View / Trackk / Vrid | Read bank/UPI/card SMS and log expenses automatically | Privacy worries over SMS access; push loans/credit products; mis-categorised SMS |
| CRED | All credit cards in one place: due dates, minimum due, utilisation, per-card spend | Not a full expense tracker; reward-focused |
| Wallet (BudgetBakers) | Clean, personalisable; scheduled transactions, many accounts | Bank sync behind paywall |
| Khatabook / OkCredit / Ok Khata | Person-wise udhar balances, one-tap WhatsApp/SMS reminders, PDF statements | Business-focused, loans upsell, WhatsApp credits paid |
| Monefy / Pocket Clear | Very fast manual entry, private (no SMS/bank linking) | Few reports, weak account/card handling |

## Patterns to copy
- Accounts-first model: every entry moves money from/to an account (bank, card, cash, wallet); transfers between own accounts are not expenses; credit card payment = transfer bank → card.
- Credit card view: limit, used, available, statement date, due date, minimum due.
- SMS parsing gives a *suggestion* the user confirms (avoid silent wrong entries).
- Udhar: red/green "you gave / you got" per person with running balance; WhatsApp reminder with amount.
- Home: month in/out/balance summary card then recent list; FAB for quick add.
- Google Pay-like look: white/light surfaces, big rounded cards, generous spacing, one accent colour.

## Pitfalls to avoid
- Ads, upsells, cloud login required.
- Counting card bill payments as expenses twice.
- SMS duplicates (same transaction from bank + UPI app SMS).
- Data loss on phone change — backup must be easy.
