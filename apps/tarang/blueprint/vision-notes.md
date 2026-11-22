# Tarang — vision notes

Distribution: personal

## His vision (his words)
"See, from this particular app, I wanted you to only take the inspiration, right? And build it from the very scratch. ... the mini player when transitions into the full player while the music is playing, the mini player itself is a progress bar." — "I want this thing to retain in the new app that we are going to build together."

Source of inspiration: the old AI Studio "Tarang" project (uploaded zip). Inspiration only — no code reused.

## Kept from old Tarang (ideas only)
- Mini player whose whole background is the progress fill (tinted fill grows left→right behind art, title, buttons), and which grows into the full Now Playing screen (shared transition). NEW: the fill continues smoothly into the full player's seek bar (no jump). He agreed.
- App colours follow the album art of the current song.
- Wavy seek bar.
- Real listening-time stats.

## Interview answers
- Purpose / daily use: on opening, he wants to continue what he was playing (last song, position, queue restored).
- Finding songs: "Mostly by going into the main songs folder where all the songs are there."
- Music source: only songs on the phone; fully offline.
- Name: keep "Tarang". Package com.sushant.tarang.
- Tabs: plan fresh ("Build from scratch"), don't copy old tabs.
- Library size: not answered → assumed 200–1,000 songs; must stay fast at 5,000+.
- UI language: not answered → assumed English only.
- Permissions: industry standard flow (he agreed): welcome screen → system popup on Continue → if denied, explain screen with "Allow access" → after 2nd denial "Open Settings" → re-check on return; never nag every launch; notification permission separate and optional (one small notice if refused).
- Gestures (his pick): all four — swipe down to minimise full player; swipe cover left/right for next/previous; swipe mini player left/right for next/previous; double-tap cover to like.
- Devices (his pick): phone and tablet equally — tablet layout is first-class, not an afterthought; rotation supported.
- Success (his pick): opens instantly and resumes my song; looks beautiful while playing; never stops or crashes in background; finds any song in 2 taps.
- Analytics: default (local crash log + Diagnostics screen; nothing leaves the phone). No internet permission at all.

## Refinement round answers
- Queue when tapping a song: the tapped song is slipped in, then whatever was already lined up continues ("Whatever was already lined up").
- Starting a fresh list from a folder: all four — "Play all" button; "Shuffle all" button; long-press a song → "Play from here"; if the queue is empty, tapping a song plays the rest of the folder from it.
- Several queues: up to 3 saved queues, switched from the Queue screen; each remembers its own song and position.
- Headphones/Bluetooth connect: a toggle in Settings ("It should be a toggle in the settings").
- Calls/other apps: pause, resume after a call; lower volume for short sounds (navigation).
- Skip short audio: under 30 seconds (changeable in Settings).
- Sleep timer: preset minutes (15/30/45/60), custom time, stop after current song, fade volume out at the end.
- Delete: delete the file from the phone, with confirm (Android's own Allow box).
- Home screen content: "I don't know" → decide with pictures in the UI step.
- Stats "listened" rule: "you decide" → counted after 30 seconds of play.
- Edit song details: saved into the song file itself.
- Equalizer: presets + 10-band + bass boost + virtualizer (custom audio processing; many phones' system EQ has only 5 bands).
- Widget sizes: "you decide" → small (cover + play/pause), wide (cover, title, prev/play/next), big (cover, title, progress, controls); resizable.
- Lyrics sources: .lrc file next to the song; lyrics saved inside the song file; paste/type lyrics in the app; plus import a .lrc file from storage and attach it to a song. Declined: tap-to-sync maker, save typed lyrics into file, lyrics on lock screen.
- Full player background (blurred cover vs solid colour): pick from pictures later.

- Default sort inside a folder: by name A–Z (changeable).
- Notification extra buttons wanted: Like, Shuffle, Repeat (not Close).
- "End the queue" (like YouTube Music): an option in the full player's three-dot menu (top-right), not a button on the screen itself — his words: "I thought I should make you remember."
- Notification: only 2 extra slots fit on Android 13+ → he wants the 2 extra buttons user-selectable in Settings (choices: Like, Shuffle, Repeat).
- End the queue does: stop music, clear queue, hide mini player and full player.
- Android Auto: no.
- Song info sheet: yes — real format, bitrate, sample rate, size, path read from the file.

## Visual identity (his words, overrides the Warm ledger default for this app)
"the visual identity of this app, it should look totally as if it is a app built by Google, basically Material You or something like ... it should look exactly that if this app has been made by Google itself."
→ Google's current design language: Material 3 Expressive (Material You), like Pixel's own apps (YouTube Music, Recorder, Pixel Weather). Font: Google Sans is Google-only and can't be bundled; use the closest open fonts (Roboto Flex / Google Sans Flex if openly licensed at build time — check).

- Colours (his pick): app uses wallpaper colours (Material You dynamic colour); the full player (and the mini player's progress fill) follows the album cover — like YouTube Music.

## Design picks (Phase 5)
- Step 1 style: round 1 showed A Pixel Expressive, B Night Music, C Calm Google → "More". Round 2 showed D Expressive Carousel, E Tarang Waves, F Bento Tiles. His mix: "I liked the UI of style number A, but the colors of style number C."
- Mix variations: 1 Mint, 2 Lime pop, 3 Forest night → picked **1 · Mint** (style A layout: search pill + big "Good evening" heading + Continue listening hero card + Your mixes row with varied shapes + Folders list; C's green palette, seed #4C7A5A, mint hero card).
- Colour source: Tarang green by default; "Use wallpaper colours" option in Settings. Now Playing + mini-player fill still follow the cover.
- Rejected (don't show again): B Night Music, D Expressive Carousel, E Tarang Waves, F Bento Tiles, Lime pop hero card.
- Step 2 icon: batch 1 (1–6 wave ideas) → More; batch 2 (7–12) → More; batch 3 (13–18) → his steer: "Don't go for the waves. Make something out of the musical note icon"; batch 4 (19–24 note ideas) → picked **19 · Bold note** (dark green #1F4D33 eighth note on mint #B3F0C6). Saved as icons/app-icon.svg. Rejected: all wave icons (1–18), 20–24.
- Step 3 layouts:
  - Home: Mint style A layout (search pill with settings, "Good evening" heading, Continue listening card, Your mixes row, Folders list).
  - Mini player + nav bar: "take a reference for the mini player shape ... from those zip file, and the navigation panel" → exact old-Tarang dock: floating capsule, 16dp side margins; mini player 72dp, corners 24 top / 10 bottom (24 when nav hidden), round 52dp cover, round prev 40 / play 48 / next 40; nav 68dp, corners 10 top (34 when no player) / 34 bottom, surfaceContainerHigh 95%, icon pill 56×32 + 11sp label; progress fill kept behind mini player. Confirmed "Yes, exactly". Nav hides on inner screens (folder detail etc.) like old Tarang.
  - Folder screen: option 2 "Header card, compact list" (tonal header card with cover stack, name, count+duration, Play all + Shuffle; numbered rows, now-playing row shows equalizer icon).
  - Full player: "Mix" → solid colour from cover (option 2 background), big cover, Up next / Lyrics / Info tabs at the bottom that swipe up into a panel (YouTube Music-like); row of Sleep / Speed / Sound / Playlist buttons under the controls; panel Up next shows Queue 1/2/3 chips, drag to reorder. Earlier comment: colours were "too pink / reddish" (sample cover) → shown with ocean/forest/gold covers → "Good, lock it".
- Step 4 mockups (32 phone screens + dark, pure black, tablet home/player): every screen "All good". Fixes he chose for repeats I flagged: (a) Home Continue-listening card shows only on a fresh app start (mini player hidden until play); once playing, the card disappears and the mini player shows — never both; (b) Folders tab: sort icon removed, sort chips kept. I removed "Sound (equalizer)" from Settings since the player has Sound (he agreed).
- Step 5 prototype: published (artifact aca545b8-d74b-4943-bc9e-7ab0b636d30c). His feedback: "Full screen where the music is being played. When it is swiped down, it should morph into the mini player, and every other thing around that should act accordingly to get into the state." → added: drag-down follows the finger with the screen underneath showing through; release past threshold → full player shrinks (container transform) into the exact mini-player capsule, screen behind fades/scales in, nav bar slides up; short drag snaps back; tapping the mini player grows back into the full player (nav slides away). Build must implement this with SharedTransitionLayout/sharedBounds + predictive drag.
  - He said "Still not right" → picked: "It should shrink smoothly as I drag" and "Cover, title and buttons should move into the mini player". Rebuilt: the morph is driven by the finger (progress = drag distance); the player box shrinks into the mini capsule continuously, the cover, title, artist and play button each fly to their mini-player spots (cover rounds into the circle, play button reshapes and recolours), other controls fade early, the screen behind scales/fades in and the nav bar rises with the progress; released past ~28% it completes, otherwise springs back. Tap mini = same morph in reverse.
  - Further rounds: "previous and next buttons don't move into the mini player" → prev/next now fly and shrink too. "Very weird stop in the end" → removed extra slide-in after expand; smoother handover. "Play pause button feels shaky" at end of collapse → background zoom finishes by 70%, all pieces land exactly on their mini spots by 85% (0 px offset). "Slight flash of colors" at the end → no see-through blending; every colour (box, fill, title, artist, play button + icon, prev/next) interpolates to the exact mini colours and the swap happens in one frame; album part of the artist line fades out in flight. Final: "Yes, feels right". The build must reproduce this exactly (finger-driven progress, all six pieces flying, exact colour match, no fade-through at the end).
- Change after design (his words): "In the library tab, songs, albums, and playlists behave in a similar way, but as soon as I click on artist, everything like goes for one single artist." → Library has a real Artists tab: list of all artists (letter avatar, songs · albums count, chevron), "48 artists · A–Z" line; sorting via the top-bar sort icon like the other Library tabs (no extra chips, to avoid a repeat); tapping an artist opens the Artist page.
- Change (his words): titles "Good evening", "Folders", "Library", Stats not equally bold — "there is not consistency, and I don't like that" → one title style for every top-level page (Home greeting, Folders, Library, Stats, Settings): Google Sans Flex 34sp/40, weight 650, width 110%, tracking −0.5, onSurface; Library and Stats use the same large title (Stats header renamed "Stats").
- Change (his words): "pure black theme option should only occur when the dark mode is selected" → Pure black row is hidden in light mode, shown under Theme only while the app is dark.
- Font: Google Sans Flex (open licence, SIL OFL 1.1 — bundled). Icons: Material Symbols Rounded.

## Features he picked for v1
Playback: equalizer + bass boost; sleep timer; gapless + crossfade; playback speed.
Library: choose which folders to scan; skip very short audio; edit song details (tags/cover).
Lyrics & looks: synced lyrics (.lrc / embedded); pure black AMOLED mode; home-screen widget; smart mixes on Home from real listening.
Extras: Wrapped-style listening stats; several queues; no internet permission.

## Not picked (don't suggest again for v1)
- Backup and restore
- Quick Settings tile

## Fixes vs old Tarang (must not repeat)
No runtime permission request; dead settings; fake lyrics / quick picks / codec info; old MediaPlayer (use Media3); unused Firebase/Retrofit packages; delete not working on Android 11+; nothing saved after closing; duplicate Explore tab; database wiping on update.
