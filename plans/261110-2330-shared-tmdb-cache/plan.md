# Shared Poster and Title-Data Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The home cache server holds TMDB answers and images, so one keyed device per house fills it and every other player (TV app, web player via `mediagram posters`) reads posters, backdrops and title data from the LAN, with or without a key.

**Architecture:** The cache server gains a small blob store (own 8 GiB budget) with signed-write routes for TMDB JSON and images. The shared crate `mediagram-tmdb` gains a `HomeCache` client that `DiskCachedApi` and the poster downloads consult between the device's disk and TMDB. The Android core receives the server from Kotlin via `set_home_cache`; the CLI reads it from its config.

**Tech Stack:** Rust (axum server, reqwest client, tokio tests), Kotlin (Android data layer, UniFFI bindings), bash (funstation script).

**Spec:** `docs/superpowers/specs/2026-11-10-shared-tmdb-cache-design.md`

## Global Constraints

- Lookup order in a player: its own disk cache, then the cache server, then TMDB (only with a key).
- A TMDB key never goes to the cache server, into a cached answer, or into a cache key.
- Reads are open; every write is signed: `Authorization: MGC1 <hex>`, HMAC-SHA256 keyed on the token's 64 ASCII hex characters over `"{method}\n{path}\n{len}\n{sha256_hex(body)}"`, no trailing newline.
- Blob budget is separate from chunks: `MEDIAGRAM_CACHE_BLOB_BUDGET`, default 8 GiB (`8 * 1024 * 1024 * 1024`); chunk eviction never removes a blob and vice versa.
- An unreachable server is skipped: GET timeout 2 s, then no server request for 30 s; the player continues exactly as today.
- JSON entries: newer replaces older. Images (keyed by TMDB path + size): first write stands.
- Empty search pages are never stored on the server (same rule as `DiskCachedApi`).
- `de` and `de-DE` are one server key: the language query value is reduced to its lowercase primary subtag in the server key only; the device's disk keys stay as they are.
- Code files stay under 200 lines (`web/test/code-standards.test.ts`, `scripts/check.sh` line limits); comments say why, never plan/task numbers.

## Review Focus

- The server answers 200 with a body that is not valid JSON (truncated, or another program on port 7788) → treated as a miss, never written to the device's disk. Test in Task 3.
- Two devices upload the same image at once → both succeed, one file, never a half-written image served. Test in Task 2.
- A blob larger than the whole blob budget → refused (413), nothing evicted. Test in Task 2.
- A keyless Android device with no server reachable (away from home) → same report and UI as today ("No TMDB key is stored."). Test in Task 6.
- A server that knows a title's JSON but not its image at this device's backdrop width → fetched keyless from TMDB's image CDN at that width and uploaded. Test in Task 4.

## Tasks

| # | Task | File |
|---|------|------|
| 1 | Shared MGC1 signer in `mlib-spec` | [phase-01-signing-and-server-blobs.md](phase-01-signing-and-server-blobs.md) |
| 2 | Cache server blob store and routes | [phase-01-signing-and-server-blobs.md](phase-01-signing-and-server-blobs.md) |
| 3 | `HomeCache` client and `DiskCachedApi` through the server | [phase-02-tmdb-crate-and-cli.md](phase-02-tmdb-crate-and-cli.md) |
| 4 | Images through the server in `poster_files` | [phase-02-tmdb-crate-and-cli.md](phase-02-tmdb-crate-and-cli.md) |
| 5 | CLI `mediagram posters` reads the server from its config | [phase-02-tmdb-crate-and-cli.md](phase-02-tmdb-crate-and-cli.md) |
| 6 | Android core and app: `set_home_cache`, keyless enrichment | [phase-03-android-and-funstation.md](phase-03-android-and-funstation.md) |
| 7 | Funstation script, docs, end-to-end check on the box | [phase-03-android-and-funstation.md](phase-03-android-and-funstation.md) |

Status: not started. Order matters: 1 → 2 → 3 → 4 → 5 → 6 → 7 (each consumes the previous task's interfaces).
