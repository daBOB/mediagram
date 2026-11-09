# Phase 1 — Game catalogue

Priority: high (fixes a hidden bug). Status: pending. Wave 1.

## Problem

What a game is (name, seat range, builder) is spread over seven places:

- `src/protocol_settings.rs:9-24` `GameKind` (doc at :22 repeats "two to eight")
- `src/room_settings.rs:21-52` MIN/MAX constants and twelve messages, all
  `"{name} braucht mindestens {min} Spieler."` / `"{name} geht mit höchstens {max} Spielern."`;
  gate at :84-97 (exhaustive match)
- `src/factory.rs:37-82` if-chain; **an unlisted kind silently builds a Quiz** (:74-82)
- `src/room_settings_tests.rs:27-87` one seat test per game
- `client/src/ui/lobby.ts:10-18` `GAME_NAMES`, :21 `SEATS` (unchecked hand copy)
- `client/src/phone/PhoneLobby.tsx:80` the list of games and its order
- `android/ui-tv/src/main/kotlin/ui/tv/games/TvGamesShelf.kt:61-70` (English copy)

The client never gates on seats: `can_start`/`blocked` already travel
(`room_views.rs:41,53-57`). Seats are badge text only (`lobby.ts:28` → `TvLobby.tsx:44`).

## Decided

- New `src/catalogue.rs`: `GameEntry { kind, name, min, max }` (Serialize, TS),
  `CATALOGUE` in lobby order, `entry(kind)` by exhaustive match. A test proves each
  `GameKind` appears exactly once. Kopfnuss, quiz and poster take the room cap (8,
  `host.rs:42`) as max — read the cap, don't repeat the number.
- `blocked()` keeps the poster-readiness and Kopfnuss-kids rules, then one seat check
  building the message from the entry (same visible text). Returns `Option<String>`.
  Delete the twelve constants; keep `NOT_ENOUGH_POSTERS`, `KOPFNUSS_NOT_FOR_KIDS`.
  Seat tests become one table test (file stays under 200 lines).
- Factory: `match settings.game` with every arm explicit; no fallthrough.
- `PhoneScreen::Lobby` and `TvScene::Lobby` carry `games: Vec<GameEntry>` (like the
  avatar list, `protocol.rs:96-97`); a test proves the views carry `CATALOGUE`.
- Client: `PhoneLobby` maps `screen.games` (quiz keeps its mode block); `gameBadge`
  reads the games from the scene. Delete `GAME_NAMES`, `SEATS`, the hard list.
  Quiz mode labels (`QUIZ_MODES`) stay client text.
- `TvGamesShelf.kt`: one comment pointing at `crates/mediagram-game/src/catalogue.rs`.
- If ts-rs cannot export `Cow<'static, str>`, use `&'static str` in the host table
  and a `String` in the wire type.

## Acceptance

- Adding a `GameKind` variant fails to compile until its catalogue arm and factory arm exist.
- All existing lobby texts unchanged; `cargo test`, `bun run typecheck`, `bun test` green.
