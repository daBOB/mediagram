# Heimlauf sound — cozy game-evening atmosphere

User, 2026-11-06, testing Heimlauf on the box: "we need more atmosphere and one thing is to add sound effects", "some background music, like a cozy game evening". Description played back and approved ("looks fine"): music + board-game noises, TV only, Heimlauf first.

## What plays (TV only; phones stay silent)
- Background music, quiet, two tracks alternating (~5.5 min before a repeat), from the board coming up until the board goes; ducks under the winner fanfare.
- Die: a clatter timed to the die touching the table (~0.69 s into the 1.15 s flight) and ending as it lands.
- Pawn: a light wood knock per hop landing.
- Thrown out: a wooden thunk + a falling pizzicato at the hit; a soft knock when the piece lands back home.
- Piece reaches its goal: a rising pizzicato.
- Winner: a sax jingle.

## My calls
- Sources, all CC0: Kenney Casino Audio (`die-throw-3`), Impact Sounds (`impactWood_light_000–004`, `impactWood_medium_000`), Music Jingles (`PIZZI01` falling, `PIZZI02` rising, `SAX07`); music "Apple Cider" (Zane Little Music) and "Cozy Puzzle In-Game 2" (MintoDog), OpenGameArt, CC0. Picked by length and pitch direction, not by ear: the user judges on the TV.
- Music normalised to -18 LUFS, Opus 56 kbps in Ogg (~2.2 MB); effects stay Kenney's Ogg. Embedded with the client in the core, so the release APK grows ~2× that (two ABIs).
- Effects through Web Audio, scheduled on the audio clock from the host's times once per stage (only future ones, so a reloaded TV never replays a backlog); music through an `<audio>` element fed a blob URL (decoding 3 min into Web Audio would take ~77 MB on the box).
- TV WebView: `mediaPlaybackRequiresUserGesture = false`, nobody taps a TV.

## Phases
- [x] 1 Assets into `client/public/race/sound/` (+ provenance in docs)
- [x] 2 `race-sound-cues.ts` (pure: stage → timed cues) + `bun test`
- [x] 3 `race-sound.ts` (load, schedule, music, dispose) wired into `race-stage.ts`
- [x] 4 Android WebView setting
- [x] 5 Version 0.145.0, changelog, architecture
- [x] 6 Verify: typecheck/build/clippy/tests; dev host; box (sound plays, fps still ~60)
- [x] 7 User on the box: "the figure jumping sound is a bit async" → 0.145.1 schedules on `getOutputTimestamp()` (the speaker's clock, output latency included) + ~2 frames screen delay. Box WebView logs `audioHardwareBufferSize = 6160` (≈128 ms at 48 kHz): the knocks were late by at least that.

- [x] 8 User: "the knocks are still async. the audio is behind" → measured in the box's WebView (spike, CDP): baseLatency 128 ms, outputLatency 312 ms, start-to-speaker 322–433 ms by `getOutputTimestamp`. The first knock (280 ms after the TV hears of the move) could never be on time → host `RaceConfig::lead_ms` 200 (move starts 200 ms after it is decided; test timings updated) + lag smoothed every frame (EMA 0.05). Box re-check (dev /tv in the spike WebView, probe on `start`): knocks started 132–139 ms ahead, die 260–275 ms ahead, none late or dropped. 0.145.2 sideloaded 18:30. User 18:35: "sound jump matches are now in sync".
- [x] 9 User: "what about the wilhelms schrei", "this is a personal project so we can use every sound" → the capture's falling pizzicato replaced by the Wilhelm scream (Wikimedia Commons `Wilhelm_Scream.ogg`, trimmed to 1.15 s with a fade, -16 LUFS, Vorbis), cue renamed `scream`. 0.145.3. Simulated 4-bot round on the box (bots throw out whenever they can); user: "yes wilhelms schrei matches".

## Results
- `bun test` 5 cue tests (new; `scripts/check.sh` runs them), tsc, vite build, clippy, `cargo test -p mediagram-game` 110 ✓.
- Dev host + headless Chrome (autoplay allowed, probe on `AudioBufferSourceNode.start` / `play()`), 2 bots, 40 s: music started once the board was up; 13 die clatters each scheduled ~0.6 s ahead; 14 knocks, one per hop, 0.28 s apart. No capture or goal entry in that window (unit-tested).
- Box 0.145.0 (release sideload, box only), real app, 2 bots: two AAudio players `state:started`, stereo 48 kHz, not muted (effects + music); `gfxinfo` 2010 frames, 13 janky (0.65 %, was 0.18 % without sound; sound loading overlaps the board build in that window), 0 missed vsync.
- 0.145.1 installed 18:03 with the sync fix; the console line with the measured lag does not reach logcat in the release WebView, so the number is not confirmed. The user's ear decides.
- Seen in passing: with 1 player the lobby button reads "Los geht's" while the line above says Heimlauf needs 2 (pre-existing in 0.144.0).
