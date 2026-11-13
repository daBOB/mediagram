---
title: "Shelf: every game, its description, WIP and Coming soon"
status: done
created: 2026-11-13
branch: feat/shelf-all-games
---

# Shelf: every game, its description, WIP and Coming soon

User, 2026-11-13: "write a short description to each game what we will show in the overview when
choosen a game. and use mcp budgetpixel with gpt-image-2.6 to create for all posters and backdrops.
when we started to implement a game add a badge WIP or coming soon if we havent started. and split
into minigames and real games." Then: "make it switchable from minigames to bigger games add a
reveal animation for games poster. reduce the size of the poster by 20% and show only 5 poster at
the same time. move the QR Code to the right bottom", "add a bit more space between the game poster,
tease the next poster, reduce the size of the QR Code". Approved from `reports/shelf-v3-minispiele.jpg`.

## What the shelf does now

- Two halves, one row at a time: **Minispiele** and **Spiele**, switched by tabs above the posters.
  Up from the row reaches the tabs, Left/Right switch, Up again reaches Einstellungen. A pick from a
  phone in the other half brings its half along.
- In each half: the playable games in the host's order, then **WIP**, then **Coming soon**
  (`client/src/ui/shelf-games.ts`). An unplayable game shows its seats, badge and description and
  "noch nicht spielbar"; OK does nothing and the room's pick stays where it was.
- Every game has a description under its name. The playable games' text is keyed by `GameKind`, so a
  new game does not compile without it.
- Posters 8.88vw (20 % smaller), five whole on screen plus a fading peek of the next, moving one
  poster at a time. They rise in one after another whenever a half opens.
- The table panel (code, seats, Einstellungen) sits bottom right; the QR code is about a third smaller.
- A soft shade on the left of every backdrop keeps the name and description readable.

## Decisions (mine unless marked)

- Badge texts are the user's words, "WIP" and "Coming soon", in an otherwise German UI.
- WIP = started, not playable: Kritzelei, Lebensweg. Shipped games carry no badge.
- Left off the shelf: **Denkzettel** (same original as Kopfnuss, which shipped) and **Kings** (needs
  an own name, and the card plan's question 3 on its drinking rules is open).
- The ten games that already had art keep it; Grand Allee's poster is the Hotel poster with its title
  edited, its backdrop the Hotel backdrop as it was.

## Art

20 posters and 20 backdrops made with BudgetPixel `gpt-image-2.6-sunburst`, quality medium, with the
cast sheet as reference, following `plans/261107-1509-games-poster-overview/image-prompts.md`.
16:9 fails on BudgetPixel's side, so backdrops were made 3:2 and centre-cropped. Originals in
`~/Workspace/mediagram/game_poster/` (untracked). Contact sheets: `reports/shelf-new-*.jpg`.

## Verification

`bun test` (134), `tsc`, `bun run build`, `cargo build --locked`, Android `GamePageClientTest`.
Looked at in a headless browser at 1920x1080 (`reports/`). Not run on the TV box yet: the rise-in on
the box's GPU and the smaller code's scan distance are unchecked.
