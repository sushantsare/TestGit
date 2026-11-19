# Lekhak — notes

Floating AI writing assistant for WhatsApp / WhatsApp Business (`com.sushant.lekhak`).

- Built-in Gemini key: set the `GEMINI_API_KEY` repository secret (or a local `.env`). If empty, the app asks for a key in Settings.
- Default Gemini model: `gemini-flash-latest` (editable in Settings).
- No clipboard: text goes in with ACTION_SET_TEXT only.
- API keys live in `lekhak_secrets` prefs, excluded from backup.

| Permission | Why | When asked | If denied |
|---|---|---|---|
| Accessibility service | Read the open WhatsApp chat + draft, write the chosen reply | Onboarding step 2 / Settings card | Bubble never appears; Settings shows "Not Granted" |
| SYSTEM_ALERT_WINDOW | Draw the bubble and the sheet over WhatsApp | Onboarding step 1 / Settings card | No bubble; red banner in Settings |
| INTERNET | Call Gemini / OpenAI | Install-time | — |
| FOREGROUND_SERVICE + _SPECIAL_USE | Keep the bubble alive while you use WhatsApp | Install-time | — |
