# Phase 1 — Web page: shelf + table panel

Context: [plan](plan.md), mockups `visuals/lobby-a-empty.png`, `visuals/lobby-a-full.png`.
Priority high · Status done

## Requirements
- `TvLobby` draws layout A: backdrop of the focused game (fade, old one held underneath), kicker
  "Spieleabend", game name, "min–max Spieler" (quiz: its question mode), status line, poster row;
  panel top right: QR card (big at 0 players, smaller after), join URL, seats, Einstellungen.
- Remote: Left/Right move along the row (row scrolls), Up to Einstellungen, Down back to the row,
  OK on a poster starts it (configure first when the host has not heard the pick yet).
- Focus follows the host's `settings.game` when the leader's phone changes it; the TV sends its
  pick debounced (~250 ms), so holding Right does not flood the room or jump back on echoes.
- Status line: nobody seated → scan the code; focused pick not yet echoed → plain; blocked → reason;
  else "OK startet <Spiel>" (+ "<Leader> kann auch am Handy starten").
- Art from `/game-art/poster-<kind>.webp` / `backdrop-<kind>.webp`; on error a name tile / felt.
- `?films=0` adds the note that the library's films are not reachable.
- The `?game=` parameter goes (nothing opens the page with it any more).

## Files
- modify `client/src/tv/TvLobby.tsx`, `client/src/lobby.css` (tv-lobby rules)
- create `client/src/tv/lobby/` helpers if TvLobby passes 200 lines (focus/debounce hook, backdrop)
- tests: `client/src/tv/lobby/*.test.ts` for the pure parts (status line, next index)

## Todo
- [x] layout + CSS
- [x] remote keys, focus sync, debounce, OK starts
- [x] backdrop fade, art fallbacks
- [x] status line + tests
- [x] dev host screenshots (0 and 3 players)
