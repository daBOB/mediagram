# Game night: avatars in the lobby, TV settings for sound and figures

User, 2026-11-06 (after testing Heimlauf with sound on the box): "we need to choose an avatar in the lobby and instead the name initial show the head of the avatar. add also settings for sound options. mute, sound level and game figure as default figures or the 2d sprites of the avatar". Brainstormed in chat; description approved ("looks fine, start building").

## Decided by the user
- Sound settings are worked with the **TV remote**, in a panel on the TV. **Both places**: during a game (most important) and in the lobby.
- **Music and effects have separate levels**, plus mute. The remote's own volume keys stay the TV's loudness.
- Avatars are picked on the phone in the lobby; **first come, first served** (a taken avatar cannot be picked by another player).
- The avatar's **head replaces the name initial**. A setting chooses the board figures: **default pawns or the avatar as 2D sprite**.

## My calls (described to the user, not objected to)
- Panel rows: Ton (an/aus), Musik (0–10), Effekte (0–10), Spielfiguren (Holzfiguren / Avatare). ▲▼ rows, ◀▶ value, Back closes. "Ton aus" keeps the levels.
- Settings belong to the TV, saved in the TV page's localStorage (key `mediagram.game.tv-settings`), so they survive the next game night. Defaults: Ton an, Musik 4, Effekte 8, Holzfiguren. Level mapping keeps today's mix at the defaults: music volume = 0.35 × level / 4; effects master gain = level / 8.
- In the lobby an "Einstellungen" button next to the start button; during Heimlauf, OK opens the panel over the board (the game runs on). Quiz and poster games have no sound yet, so no panel there.
- Back closes the open panel first; otherwise Back works as now (a running game steps back to the lobby). The TV app asks the page (`window.tvBack()`) before its own Back.
- Changing Spielfiguren mid-game swaps the figures at once.
- A player who does not pick gets a random free avatar on joining; tapping a free head swaps. The phone remembers its avatar (localStorage) and asks for it again on the next join; taken → random free one.
- Heads everywhere the initial was (one `Token` component: TV lobby, scoreboards, ranking, poster, phone) in a circle of the player's colour; on the race board's canvas tags too.
- Avatar figures on the board: each piece is the player's avatar as a flat standing sprite (like the race look test, measured 59.8 fps with 16 sprites on the box) on a small base in the player's colour; move markers unchanged.
- Images rendered once, offline, from the 22 `assets/game-night/avatars/<name>/<name>-tv.glb` (matte, idle pose): `client/public/avatars/<name>-head.webp` (256²) and `<name>-figure.webp` (256×384, feet at bottom centre), transparent. The render tool is committed (`crates/mediagram-game/tools/render-avatar-images.mjs`); `assets/` is never staged.
- Avatar catalogue (ids = folder names): android, armour-hero, bear, bodybuilder, bunny, cat, dark-knight, fashion-doll, fox, frog, ghost, granny, hedgehog, mummy, panda, piglet, princess, raccoon, samurai, vampire, witch, yeti. The host owns the list.
- 3D avatars on win/reaction screens: later, not here.

## Phases (A, B, C in parallel; D after them)
- [ ] A Avatar images: render tool + 44 webp files + contact sheet check
- [x] B Host + phone: `PlayerInfo.avatar`, hello `avatar?` preference, `Action::PickAvatar { id }` (lobby only, NotAllowed if unknown/taken), `PhoneScreen::Lobby.avatars: Vec<AvatarSlot { id, taken }>`, random free on join, reconnect keeps, leaving frees; phone `AvatarPicker.tsx` (4 columns, remembers `spieltisch.avatar`); `Token` shows the head in a colour ring, initial fallback. 119 host tests (8 new), clippy, tsc, build ✓
- [x] C TV settings: `tv/settings/tv-settings.ts` (store, sanitize, musicVolume/effectsGain, localStorage), `TvSettingsPanel.tsx` (keys on window in capture phase, sets `window.tvBack`), lobby "Einstellungen" button (←→ focus), OK on the board opens it, effects master gain + music volume live in `race-sound.ts`, `TvGamesFrame.kt` asks `window.tvBack()` before its own Back. 5 store tests; `:ui-tv:compileReleaseKotlin` ✓
- [x] D Board: `Hud` draws the head in the colour circle (initial fallback); each piece holds both a pawn and an avatar sprite on a colour disc (`showFigures`), all pictures loaded and both styles compiled before the first frame, the setting swaps them live. tsc, 10 client tests, build ✓
- [x] A Avatar images: `tools/render-avatar-images.mjs` (headless Chrome + three.js 0.170, `Idle` frame at 0.5 s, camera on the `headfront` bone for heads, matte Lambert), 44 WebP, 458 KB. Raccoon figure ~80 % height (its tail), dark knight very dark at 32 px.
- [x] E Version 0.146.0, changelog, architecture ✓; verify: tests/clippy/typecheck/build ✓, dev host screenshots ✓ (phone grid with taken greyed, TV lobby heads, panel over the board, live swap avatars → pawns, saved). Box 20:00, release sideload: lobby OK on Einstellungen opens the panel, Back closes only the panel (Kotlin hook works); Avatare + 4 bots: figures and heads right, `gfxinfo` 1800 frames, 7 janky (0.39 %), 0 missed vsync; OK during the game opens the panel, Back closes it, the game runs on; Spielfiguren set back to Holzfiguren afterwards. Seen: Musik stood at 1 in the game panel though it was 4 in the lobby just before; not reproduced, only the panel writes the setting (◀▶/OK on that row) — probably a real remote press; asked the user.

- [x] F (installed on the box 20:45 after "deploy update"; box serves keep-awake.webm/.mp4 200) User: "keep the phone awake by playing fake video" → 0.146.1: NoSleep's fallback video was never attached to the page (checked in its source), and Chrome on Android keeps the screen on only for a playing video in the page and in view; `wake.ts` now loops `public/keep-awake.webm/.mp4` (2 s, 64 px, page colour, < 2 KB) full-screen behind the content at 1 % opacity, also on a phone's own rejoin; NoSleep removed. User: "can we add fullscreen mode on phone" → same 0.146.1: `phone/phone-fullscreen.ts` enters fullscreen on the join tap, re-enters on the next tap after a swipe out (armed on a rejoin, first tap enters); iPhones have none. tsc/tests/build ✓, core built; install waits for the running round on the box; on-phone checks (screen-on hold, fullscreen) still to do.

- [x] G User on real phones: "okay its not working. then the user need to disable the sleep mode" → 0.146.2: keep-awake clip removed (`wake.ts`, `public/keep-awake.*`, CSS), the phone lobby tells players to switch off the automatic screen lock; fullscreen stays. tsc/tests/build ✓; installed on the box 20:58 (keep-awake.mp4 now 404), lobby open.

## Review (code-reviewer, report plans/reports/code-reviewer-261106-1940-avatars-and-tv-settings-report.md)
Fixed: head sized from the parent (`padding: 8%` + invalid `max()` in box-shadow) → `.token-head` 84 %; panel reopening on the next round → closed when the ranking takes over; held OK toggling sound / opening the panel after the start → `e.repeat` ignored; sound loaded after the board was gone leaked listeners + blob URLs → `disposed` check; Back unanswered while the board builds → 400 ms fallback in `TvGamesFrame.kt`; the phone now remembers only a self-picked avatar (`phone/avatar-memory.ts`), the first assigned one only if none was stored.
Left: effects 9–10 can clip (gain > 1) on overlapping sounds; a lost pick race shows the generic "Das geht gerade nicht."; picker aria labels are the ids; the figure base geometry uploads on the first switch to Avatare (tiny); settings/avatars live per page origin (box IP/port changes reset them, pre-existing).

## File ownership while A–C run in parallel
- A: `crates/mediagram-game/tools/render-avatar-images.mjs`, `crates/mediagram-game/client/public/avatars/**`
- B: `crates/mediagram-game/src/**` (Rust), `client/src/protocol/**` (generated), `client/src/phone/**`, `client/src/ui/Token.tsx`, `client/src/styles.css`, `crates/mediagram-game/tools/smoke.mjs`
- C: `client/src/tv/settings/**` (new), `client/src/tv/TvLobby.tsx`, `client/src/tv/TvRace.tsx`, `client/src/tv/race/race-sound.ts`, `client/src/tv/race/race-stage.ts`, `android/ui-tv/src/main/kotlin/ui/tv/games/TvGamesFrame.kt`; CSS in its own file under `tv/settings/`
- D (after): `client/src/tv/race/race-hud.ts`, `race-world.ts`, `race-motion.ts`, `race-stage.ts`
- Version, docs, plan: lead only.
