# Saveo — Vision notes

## His original vision (word-for-word)
> "Okay, so now I want to design an app which can save the WhatsApp status, whether be it in video or in the images. Like sometimes the images are with music, right? So that comes out as video, I think, right? I'm not sure, but confirm it."

> On saving workflow: "I want the app to have like two tabs. One should show the status and other should show the saved one, right? So on the two sides, like it will be a two tabs type of thing, what I am imagining. And here it will show that whatever the statuses are available inside the WhatsApp, and like how will be the UI, I don't know, but like on the bottom right corner or something like that of that particular thumbnail of that status in this app will be a download button or something similar to that, and when it will be clicked it becomes a tick mark, but the image or the thumbnail doesn't disappears from here or from the WhatsApp, right? But it gets stored here inside the saved one, and it should be permanently there unless and until a user deletes it. So like you decide how will this workflow work."

Confirmed to him: photo + music statuses arrive as MP4 video; app detects the real file type.

## Interview answers
- **Users:** just him (personal, sideloaded APK).
- **Sources:** WhatsApp, WhatsApp Business, dual/clone WhatsApp (best-effort; depends on phone).
- **Structure:** two tabs — Statuses | Saved (swipe between).
- **Save flow:** download button at bottom-right of each thumbnail → becomes a tick after saving; tile stays in Statuses; copy kept permanently in Saved until he deletes it. Claude decides the details.
- **Save location:** gallery album "Saveo" (Pictures/Saveo, Movies/Saveo).
- **Viewer:** full-screen, swipe left/right, photos zoomable, videos with sound; save button inside.
- **Actions:** share to any app, repost to my WhatsApp status, delete from Saved (with undo), long-press multi-select for save/share/delete.
- **Android extras:** notification for new statuses; app lock (fingerprint).
- **Not wanted (v1):** home-screen widget.
- **Look:** follows phone light/dark, green accent (not a WhatsApp copy).
- **Name:** Saveo.
- **Language:** English.
- **Devices:** phone, rotation allowed (tablet layout still built in).
- **Diagnostics:** kept on phone only.

## Features picked (v1)
- Standard: filter chips All·Photos·Videos; "Open WhatsApp" button; select all + bulk save; video badge + duration.
- Extras: pull to refresh; sort Saved by date + date search; video controls (seek, mute, loop, speed); recycle bin (30 days).
- Stand out: no ads/trackers, fully offline; smart folder setup + auto-fix; no duplicate saves; group by time (Today / Earlier) + Business badge.

## Rejected
- Home-screen widget (not picked).
- Only-manual saving / auto-save-everything as the only modes.

## Design picks
- Style: **Style 1 – Clean & Fresh** (white, airy, green #1B8A5A, mint #CDEEDB, Roboto, 14dp corners).
- Rejected styles: Style 2 Bold & Modern (forest + lime header), Style 3 Soft & Friendly (very round, mint tint).

- Icon batch 1 rejected ("more"): 1 Arrow into tray, 2 Status ring + arrow, 3 Bookmark with play, 4 Bold letter S, 5 Photos with tick, 6 Dark vault.
- Icon batch 2 rejected ("more"): 7 Treasure box, 8 Photo + video split, 9 Folder with star, 10 Magnet catching, 11 Heart in a frame, 12 Lowercase s.
- His steer: wants a 3D / glossy look — "a mix of One UI, iOS as well as Pixel OS, basically Google" (he says he has said this before).
- Icon batch 3 (glossy): he liked **14 Frosted glass cards** but wanted it "more expressive that it is capable of saving, like photos and videos". Rejected: 13, 15, 16, 17, 18.
- Variations of 14: picked **14a · Photo + video + save badge** (photo card behind a frosted-glass video card with a play sign, white glossy save badge, green gradient background). Rejected 14b (into tray), 14c (media card). Saved as design/icon/app-icon.svg (+ app-icon-mono.svg). Preview shown on light/dark/themed (pending his OK).
- Icon 14a locked (he said "Ok" to the preview).
- Layout picks: Home = **B · Big photos, 2 per row** (Pixel style, top title + WhatsApp source chip, underline tabs Statuses/Saved, filter chips, 2-column large rounded cards, extended "Save all" button). Rejected home A (big One UI title + pill tabs) and C (edge grid + floating glass bar).
- Viewer = **C · Card on blurred background** (media in rounded card, blurred colour background, round glass back/more buttons, big white "Save to gallery" pill + round Share / Repost). Rejected viewer A (floating glass pill) and B (labelled buttons + strip).
- Overall UI direction (his words): 3D/glossy, "a mix of One UI, iOS as well as Pixel OS, basically Google" — apply to the screens on top of the Clean & Fresh style.

## Confirmed after summary
- Summary approved. Add optional "Auto-save new statuses" switch in Settings, OFF by default.

## Approval (20 Nov 2026)
- He approved the prototype: "Everything was good… Rest is okay and I approve build the APK."
- Extra requirement (his words): "when the thumbnail is clicked and the media like picture or video is previewed, so swiping them brings the another next media, right? So this process should be like smooth, so I don't want here any jitters."
