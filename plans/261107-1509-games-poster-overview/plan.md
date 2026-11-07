# Games poster overview

User, 2026-11-07 15:09: "lets add also a poster overview in the games section and then load the game"; art made by
the user with GPT Image (prompts: `image-prompts.md`), delivered 16:35 in `game_poster/` (untracked, originals).

## Built (0.155.0, uncommitted; renumbered from 0.154.0 above the Bauherr session's local 0.154.1 sideload)
- Games opens a poster shelf inside the full-screen game frame (`TvGamesShelf.kt`): one row of posters at the film
  rows' plate width and gap, the focused game's backdrop behind (Coil, crossfade), name + seats on the calm left.
  My call: inside the game frame, not a chrome page, so the host starts behind the shelf and its ~12 s start is
  mostly over by the time a poster is picked.
- OK on a poster → lobby page `/tv?game=<kind>`; the TV page sends `configure` once per load (`TvLobby.tsx`); the
  host now lets the TV configure as the leader may (`room.rs`, test `the_tv_picks_the_game_but_keeps_the_kids_lock`).
- Back: game → lobby → shelf (host keeps running, phones stay joined) → out (host stops). A failed start retries
  on the next pick.
- Art: `android/ui-tv/src/main/res/drawable-nodpi/ui_tv_game_{poster,backdrop}_<kind>.webp`, posters 480×720 q85,
  backdrops 1920×1080 q80, ~3 MB total.

## Open
- Heimlauf: no art yet → name tile, no backdrop.
- Bauherr: the delivered poster's title reads "Hotel", the branded game's name → not shipped; shelf shows the
  backdrop crop with "Bauherr" until a poster titled "Bauherr" arrives. Backdrop (textless) shipped.
- Box walk: 0.154.0 installed 16:49 was displaced 16:50 by the Bauherr session's local 0.154.1. One shared 0.155.0
  (all four ABIs, release key) built and sideloaded by mediagram-e2 ~18:00; it saw Games → shelf → Bauherr → lobby
  "Bauherr · 2–4 Spieler" work.
- My walk 18:20–18:30 (0.155.0 reinstalled): first shelf 30 % janky, median 44 ms (GPU 24 ms: two drawn full-screen
  gradients + Crossfade's offscreen layers). Shading baked into the backdrop WebPs, fade by draw alpha → 5.9 % janky,
  median 16, p99 25 ms, 0 missed vsync. Durak → lobby "Durak · 2–6 Spieler" → Back → shelf on Durak → Back →
  library with Games lit, host stopped. Passed. (Those numbers ran uncompiled: I had installed the minApi-28 .dm,
  which ART ignores on the API-37 box; pick the minApi-31 one from output-metadata.json.)
- Compiled (speed-profile, install-dm, 18:35): two walks 1.96 % / 1.47 % janky, median 11–13 ms, p99 16–17 ms,
  0 missed vsync, 0 slow UI thread.
- Tests: `TvGamesRailTest` rewritten for the shelf flow (5/5 pass); check.sh otherwise green except the flaky core
  port test `start_twice_replaces_the_first` (not this diff).
- Leader's phone can still start a game while the TV shows the shelf; the TV then opens on the running game.
- Release builds need all four ABIs rebuilt (`scripts/build-android-core.sh`); only armeabi-v7a was rebuilt.
