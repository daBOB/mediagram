# Race game ("Heimlauf") — playable in game night

User, 2026-11-06: "yes start" (turn the race board into a real game: TV board, phones roll and pick the piece), "just start, use the classic rules". Look is FINAL from the look test (wooden board on a table, die rolls on the table beside the board). Questions skipped by the user, so the calls below are mine.

## Rules (classic cross-and-circle race game, own name)
- 2–4 players, 4 pieces each, 40 track fields, 4 goal fields per player. 2 players sit in opposite corners.
- A 6 brings a piece out onto the own start field; it is forced while pieces wait at home and the start field is not blocked by an own piece. An own piece on the start field must move off first when pieces still wait at home.
- Nobody may land on an own piece. Landing on an opponent sends it home (not forced: player's choice).
- Goal: exact count, no jumping over own pieces inside the goal.
- A 6 rolls again. With no piece able to move on the track (all at home / settled in goal), up to 3 tries for a 6.
- First player with all four pieces in the goal wins; the game ends there, the others rank by pieces in the goal, then by distance travelled.

## My calls
- Name "Heimlauf". Pieces are turned wooden pawns in the player colour (avatars are not part of game night yet; animals come later).
- One legal move → moved automatically; none → shown and passed. Disconnected players are played automatically.
- Phone: untimed "Würfeln" button, then up to four move buttons (blue ●, orange ▲, yellow ■, pink ◆); the TV marks the matching pieces with the same colour and shape.
- `PhoneScreen::Choice.closes_at` becomes optional (untimed choice, no clock or meter).

## Phases
- [x] 1 Host rules: `race_rules.rs` (positions, legal moves, apply, ranking) + tests
- [x] 2 Host game: `race.rs` (turn flow, timing, auto play), `race_views.rs`, `protocol_race.rs`, `GameKind::Race`, lobby blocked (2–4 players), factory + tests
- [x] 3 Untimed choice: protocol + quiz/poster views + phone (single/paired buttons fill the grid)
- [x] 4 TV: three.js scene ported from the look test (lazy chunk), HUD in the canvas, animation from scene + server time
- [x] 5 Lobby UI: game option on phone and TV
- [x] 6 Verify: clippy, cargo test (110), typecheck, build ✓; 3 scripted phones against the dev host ✓ (TV + phone screenshots); TV page on the box ✓; a self-played 2-player game to the end (158 turns: winner banner on the board, then the ranking) ✓; independent code review ✓ (fixes below)
- [x] 7 Version 0.144.0, changelog, system-architecture "Race board game", smoke.mjs taps only existing buttons

## Independent review (code-reviewer, 2026-11-06)
No rule bugs (checked by hand + 20k simulated games, longest 908 turns). Fixed:
- Untimed choice locked its button on a tap dropped during a reconnect → turn stuck for good. Now untimed choices never lock locally (host ignores repeats).
- Absent auto-play counted from the prompt, so a blip after a long think moved at once → counts from `max(prompt, left_at)` (+ test).
- Board built after the ranking took over kept drawing; tags stuck at the pre-draw state in Final; a new game after a missed lobby got no board → late build disposed, tags set in Final, rebuild on restart.
- Marker shaders compiled at the first "welche Figur?" → `renderer.compile` at load.
- Dev StrictMode double effect shared one WebGL context → canvas per start.
- Die after a TV reload showed 6 → shows the host's last roll; ranking lists only seated players; captured player gets a wait screen (the outcome screen adds "Stand: N Punkte").
Left as is: identical labels for two goal moves (the TV marks tell them apart), an unseated leader at the end has no back button (the TV's does), final screen says "Punkten" for pieces home, kids toggle shows for Heimlauf.

## Results
- Box (real `/tv` page in the spike WebView via adb reverse, 3 scripted phones, 30 s): first build 59.9 fps, 1 % low 59.5, 3 frames > 20 ms, board up 3.4 s after start; after the review fixes (shaders compiled at load) 60.0 fps, 1 % low 59.5, 0 frames > 20 ms (max 20 ms), board up 3.5 s.
- Test scripts (scratchpad, throwaway): race-driver.mjs, race-phone.mjs, race-final.mjs, race-box.sh + race-box.mjs.
- Real TV app, 2026-11-06 17:22: 0.144.0 release sideloaded to the box only (`:app:installRelease`, native core rebuilt first). Games → lobby → 2 scripted phones over Wi-Fi started Heimlauf: board, pawns, die, banner, tags all right; `gfxinfo` 3253 frames, 6 janky (0.18 %), 1 missed vsync. With both phones gone the absent turns kept playing; Back returned to a clean lobby (Heimlauf preselected).
- Not done: a game with real people.

## Box rules carried over (benchmark-261105-1611 report)
No HTML over the canvas during play; HUD states pre-drawn + `initTexture` at load; big flat surfaces unlit with baked light; no alpha-tested cut-outs; no per-dot noise or blur filters on big canvases.
