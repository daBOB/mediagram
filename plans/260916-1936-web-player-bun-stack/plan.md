---
title: "Web player on a Bun stack"
status: in-progress
created: 2026-09-16
source: plans/reports/brainstorm-to-planner-260916-1936-web-player-bun-stack-report.md
blockedBy: []
blocks: []
---

# Web player on a Bun stack

A browser plays the library, from a player that runs anywhere — not only on
the machine that uploaded it. The Bun app speaks MTProto itself through
`teleproto`, gets its catalog from the published encrypted package, and serves
a set's concatenated parts as one virtual file over HTTP Range. ffmpeg
transcodes what browsers refuse.

```
                     {base}/latest.json ──> package ──> library.db + posters
                                                             │
browser ──HLS / direct play──> Bun ──────────────────────────┤
                                 │ spawns                    │
                                 ▼                           │
                              ffmpeg ──Range──> /api/sets/:id/stream
                                                     │
                                          range plan │ teleproto
                                          disk cache │
                                                     ▼
                                                 Telegram
```

"The player" is two things: a **backend** that speaks MTProto and serves
bytes, and a **web UI** in the browser that speaks only to that backend. Only
the backend holds credentials.

The uploader stays in Rust and keeps `add`, `verify`, `export-package` and the
rest. `mediagram serve` (phase 1) remains as the reference implementation the
TypeScript port is checked against, byte for byte.

## Phases

| # | Phase | Status | Priority | Effort | Depends on |
|---|-------|--------|----------|--------|------------|
| 1 | [Range server over a set (Rust)](phase-01-range-server-over-a-set.md) | complete | P1 | 1.5d | - |
| 2 | [Bun Telegram client and Range server](phase-02-bun-telegram-range-server.md) | complete | P1 | 2d | 1 |
| 3 | [Catalog anywhere: the package reader](phase-03-package-reader.md) | pending | P1 | 1d | - |
| 4 | [Minimal web UI, direct play](phase-04-web-ui-direct-play.md) | complete | P1 | 1d | 2,3 |
| 5 | [Disk cache](phase-05-disk-cache.md) | complete | P1 | 1d | 2 |
| 6 | [Transcoding to HLS](phase-06-transcoding-to-hls.md) | complete | P2 | 2d | 4,5 |
| 7 | [Remote access, auth and TLS](phase-07-remote-access.md) | complete | P2 | 0.5d | 4 |
| 8 | [Docs and operating notes](phase-08-docs.md) | pending | P3 | 0.5d | 7 |

Phases 2 through 4 are the first useful thing: the 162 course lessons play in
a browser with no transcoding, no cache and no auth, from a player that does
not need to be on the uploader's machine. Each later phase is independently
shippable.

## Verified facts this plan rests on

Checked in the codebase and against the live channel, not assumed:

| Fact | Evidence |
|---|---|
| Seeking to an arbitrary offset is possible | `DownloadIter::skip_chunks(n)` advances by `n × 512 KiB` (grammers-client 0.10 `files.rs:68`). A byte offset costs at most one discarded chunk |
| Download throughput is ~5-10 MB/s | `verify --full` pulled 6.53 GiB back and re-hashed it; measured again at 5.1-5.3 MB/s through `serve`, enough for a 13.9 Mbit/s source to direct-play |
| A seek costs one round trip wherever it lands | `serve` returned 64 KiB from offsets up to 6.9 GB in 0.15-0.21 s |
| Parts concatenate to the exact file | Independently re-hashed byte ranges of a 6.53 GiB film against the index; both parts and the set hash matched |
| What is playable is already defined | `mlib_spec::schema::PLAYABLE_SQL` |
| Library codec profiles | `mp4/h264/aac` (162 lessons) and `mkv/hevc/ac3` (films), from the live index |
| Browsers refuse AC3/E-AC3 and Matroska | See the brainstorm report's sources |
| Uplink ≈ 25 Mbit/s | Measured during the phase-1 upload gate |
| `teleproto` runs on Bun and reads arbitrary byte ranges correctly | Five reads of the live 7,011,563,463-byte film under Bun 1.4.2, all byte-identical to the local source file, seeks 46-152 ms |
| The player needs no Telegram login of its own | A `StringSession` built from the uploader's `session.sqlite` auth key connected and authorized |
| `upload.getFile` demands 4 KiB alignment | `OFFSET_INVALID` and `LIMIT_INVALID` from the live API, contradicting teleproto's own type documentation |
| The TypeScript port returns the same bytes as the Rust server | 8/8 ranges of the live 7 GB film agree three ways: player, `mediagram serve`, and the local source file |
| One auth key cannot serve two MTProto clients | One player process: 3/3 range requests alone, 0/3 once `mediagram serve` started, 0/3 after it stopped. It does not recover |
| `Bun.serve` cannot send `Content-Length` on a streamed body | Replaced by `Transfer-Encoding: chunked` at every stream shape tried on Bun 1.4.2; `node:http` sends what it is given |

## Decisions

- **The player speaks MTProto itself, through `teleproto`.** Reversed
  2026-09-16, after phase 1 shipped. The original decision — Rust owns
  Telegram, Bun never speaks MTProto — rested on the player running beside the
  uploader. It does not: the player must run anywhere, and off that machine
  there is no `mediagram serve` to proxy to. GramJS being archived was the
  other half of the original reasoning; `teleproto` is its maintained fork,
  verified working on Bun before this reversal was written down.
- **`mediagram serve` is kept, as the oracle.** It is complete and verified
  byte-exact against the live channel, which makes it the cheapest way to
  prove the TypeScript port correct. Retiring it is a decision for after
  phase 2 passes its differential test, not before.
- **The player's catalog comes from the package.** `export-package` has been
  publishing them since 2026-09-15 and nothing has ever read one. Phase 3 is
  the reader the format always specified. Reading `library.db` straight from
  disk stays available when the player happens to be on the uploader's host.
- **Transcoding is for codecs and for bitrate.** A 13.9 Mbit/s source against
  a 25 Mbit/s uplink leaves no headroom, so remote viewing transcodes even
  when the codecs would have played.
- **Auth and TLS are delegated** to Cloudflare Tunnel or Caddy rather than
  built into the app.
- **One viewer.** No multi-user accounts, no watch history, no concurrent
  session management in v1.
- **The backend holds the account's auth key; the browser holds nothing.**
  MTProto authenticates every file call and has no scoped credential, so
  whichever process fetches bytes is the account. That process is the Bun
  backend. The web UI is an ordinary HTTP client of it and never receives a
  session string, the channel id, or a message id.
- **The backend gets its own Telegram login**, decided by measurement rather
  than taste: two clients sharing one auth key break each other permanently.
  A dedicated account is the better form of it, since the blast radius then
  stops at the library.

## Scope note

The user chose to plan all four slices at once, over a recommendation to plan
only the first. The phase order preserves the intent, and the 2026-09-16
reversal vindicated the caution: phase 1 shipped and is kept, while phases 2
onward were rewritten around a requirement — the player runs anywhere — that
only surfaced afterwards.

## Cross-plan relationships

- `260914-1954-telegram-linux-uploader-mlib-spec-v2` (in progress, live gate
  outstanding) owns the Telegram code this plan reuses. No changes to it are
  required; `serve` is additive.
- `260915-1956-prebuilt-metadata-package-for-player` (complete) is now
  **directly on this player's path**: phase 3 implements the reader side of
  `mlib-package-v1`, which that plan specified and published but never
  consumed. No format change is expected; if the reader finds one needed, it
  is a format-2 decision, not an edit.

## Success (whole plan)

- A course lesson plays in a browser, seekable, with no transcoding, from a
  player running on a machine that never uploaded anything.
- A film plays remotely through a transcode that stays under the uplink.
- The catalog lists exactly what `PLAYABLE_SQL` considers playable.
- A second play of the same title does not refetch it from Telegram.
- The service exposes nothing without authentication when reached externally.
- `cargo test` green; every file under `src/` within the 200-line rule; the
  Bun app has its own tests for the parts that are not glue.
- The Bun player and `mediagram serve` return identical bytes for the same
  ranges of the same set.

## Open questions

Recorded per phase. Nothing blocking at plan level.
