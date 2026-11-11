---
title: Never start the real Bun player to check UI work
author: Andre
date: 2026-11-08
id: l-ee7f7c4c
scope: repo
status: active
touches:
  - web/scripts/preview.ts
---

## Decided
To look at `web/public` changes, do **not** run the real player (`cd web && bun
run serve` or similar). Write a throwaway Bun server in the scratchpad that
serves `web/public` statically and stubs `/api/sets`, `/api/player`,
`/api/search`, `/api/posters/<key>.jpg` and `/api/sets/<id>/summary`. Real
posters for fixtures live in `~/.local/share/mediagram/posters/`.

**Why:** the player process holds the account's MTProto auth key, and one auth
key cannot serve two clients — a second connection breaks both until restart.
Uploads in this project run for hours (a 125 GB batch was in flight when this
was written), so starting the player to check a stylesheet can kill a long
transfer. The stub exercises the real `app.js`, `library.js` grouping and every
view; only Telegram is faked.

## Watch out
fixture rows must match `CatalogSet` plus the three fields
`routes.ts` adds (`poster`, `hasSummary`, `subtitles`). Give one set an
`mkv`/`hevc` pair so the transcode badge and the jump bar render. Screenshot
with the gstack `/browse` skill — the global rule forbids the Chrome MCP tools.
Full-page screenshots miss the `<dialog>`; use `screenshot --viewport` for it,
and reload rather than only changing the hash, or dialog state carries over.

A better stub than a hand-written static server: import the **real**
`startServer` from `web/src/server` with a fake `ByteSource`
…

_From Claude Code's memory of this repo, last updated Sep 20._
