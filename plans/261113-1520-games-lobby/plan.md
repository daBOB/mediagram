# Games overview and lobby in one screen

Status: built and on the funstation, awaiting a night with real phones · Branch `feat/games-lobby` (worktree `spieltisch/mediagram-lobby`) · Spieltisch line

Today Spieltisch opens on a native poster shelf; a poster opens the web lobby (QR code, players,
Los geht's), and every other game goes back through the shelf and that same lobby. The user asked
for one screen: games overview and lobby combined (2026-11-13).

## User decisions (2026-11-13)

- Layout **A** (`visuals/lobby-a-*.png`): today's shelf (focused game's backdrop, title, poster row)
  plus a panel top right with the QR code, who is at the table, and Einstellungen. The code is big
  while nobody has joined and smaller once someone has.
- **OK on a poster starts that game** when it can start; otherwise the line under the title says
  why. Moving over a poster picks it, so phones see the choice; the leader's phone can still start.

## Decided here

- The combined screen is the **web TV page** (`TvLobby`), not the native shelf: the page already has
  the live players, the code, the settings panel and each game's blocked reason. The native shelf
  (`TvGamesShelf.kt`, `ShelfGames.kt`) goes; the app opens the page as soon as the host runs.
- Art stays in the APK, as the ceremony avatars do, so it is not embedded once per ABI in the
  native core: `assets/game-art/{poster,backdrop}-<kind>.webp`, answered by `GamePageClient` at
  `/game-art/`. A game without art (or the dev host, which has none) shows its name on a felt tile.
- Back: page first (settings panel), then a running game back to this screen, then leave.
- "The library's films aren't available right now" moves onto the page (`?films=0`).

## Phases

| # | Phase | Status |
|---|-------|--------|
| 1 | [Web page: shelf + table panel](phase-01-web-shelf-lobby.md) | done |
| 2 | [App: open the page directly, serve the art](phase-02-app-frame-and-art.md) | done |
| 3 | Verify on the funstation, versions, changelog, merge when asked | screens and remote checked on the box (`reports/funstation-*.png`); phones, commit and merge open |

## Verification (2026-11-13)

- Client: `tsc` clean, `bun test` 129 pass (new `tests/tv-shelf.test.ts`).
- Dev host + Playwright (`reports/dev-*.png`): empty table, 3 and 8 players (two columns, code smaller),
  blocked reasons (Preisfrage without articles, Durak over 6), Up to Einstellungen, row scrolls to Durak;
  OK straight after moving sent `configure` + `start_game` and Kopfnuss started.
- Android: `TvGamesFrameTest` 9, `GamePageClientTest` 5 pass; lint clean; release APK signed.
- Funstation: Spieltisch 0.179.0 installed; page opens directly with the APK's art; D-pad Right ×4 moved
  to Preisfrage with its backdrop (`reports/funstation-1-shelf.png`, `funstation-2-preisfrage.png`).
- Seen on the box: with the Waydroid display asleep the activity is stopped at once and the night ends
  (by design: going to the background ends the night).
