# Grabbit permissions

| Permission | Why it's needed | When it's asked | If denied |
|---|---|---|---|
| INTERNET | Download videos | Never (install-time) | — |
| ACCESS_NETWORK_STATE | Wait for a connection before downloading | Never (install-time) | — |
| POST_NOTIFICATIONS (Android 13+) | Show download progress and "finished" alerts | Once, at the first Download, with an explanation | Downloads still work; no alerts |
| FOREGROUND_SERVICE + FOREGROUND_SERVICE_DATA_SYNC | Keep long downloads running in the background (Android 15 limits this to about 6 h a day) | Never (install-time) | — |
| WAKE_LOCK | Added by WorkManager; keeps the CPU awake during a download | Never | — |
| RECEIVE_BOOT_COMPLETED | Added by WorkManager; resumes queued downloads after a restart | Never | — |
| WRITE_EXTERNAL_STORAGE (Android 8–9 only, maxSdk 28) | Save to Movies/Grabbit and Music/Grabbit on old Android | At the first Download, with an explanation | Download cancelled; "Open Settings" when permanently denied |

Android 10+ saves through MediaStore and needs no storage permission.
Backups include only settings — login cookies and the download list never leave the phone.
