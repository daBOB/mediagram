---
title: "Web player \"flood wait (upload.GetFile)\" came from its own duplicate fetches, not the…"
author: Andre
date: 2026-11-08
id: l-53029a36
scope: repo
status: active
touches:
  - web/src/cache/in-flight-chunks.ts
---

## Decided
2026-09-30: the `Sleeping for Ns on flood wait (Caused by upload.GetFile)` lines were not caused by the other machine's upload. That uploader was running the whole time, and a steady preload at 1 req/s drew no flood waits.

The real driver was the cache reader fetching the same chunks again and again. ffmpeg opens a new range request on every seek. Each one that landed on a chunk not yet fetched started its own fetch, with no sharing between them. One 4 MiB run went out 47 times in 45 s. It only showed once the cache went cold, after the 1 MiB chunk switch. Fixed in 0.83.1 (`web/src/cache/in-flight-chunks.ts`).

**Why:** the symptom looks like the flood limit being too strict, or like interference from the other machine. It was neither.

## Watch out
if floods come back, compare `fetchedBytes` in `curl -s localhost:8770/api/status` with the growth of files under `~/.cache/mediagram-player/<chunk>/`. If far more bytes were fetched than stored, duplicates are back. To find them, temporarily log `setId`, `partIdx` and the run range from `fillRun` to a scratch file, then replay the transcoder's ffmpeg command against an uncached `-ss`. See telegram-pins-are-flood-limited.

_From Claude Code's memory of this repo, last updated Sep 30._
