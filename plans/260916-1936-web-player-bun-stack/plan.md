---
title: "Web player on a Bun stack"
status: pending
created: 2026-09-16
source: plans/reports/brainstorm-to-planner-260916-1936-web-player-bun-stack-report.md
blockedBy: []
blocks: []
---

# Web player on a Bun stack

A browser plays the library. Rust keeps the only Telegram implementation and
grows a `serve` command exposing HTTP Range over a set's concatenated parts.
Bun serves the UI and never speaks MTProto. ffmpeg transcodes what browsers
refuse.

```
browser ──HLS / direct play──> Bun (UI, session control)
                                 │ spawns
                                 ▼
                              ffmpeg ──Range──┐
                                              ▼
browser ──direct play───────────────> mediagram serve (Rust)
                                        grammers session
                                        parts -> virtual file
                                        disk cache
                                              │
                                              ▼
                                         Telegram
```

## Phases

| # | Phase | Status | Priority | Effort | Depends on |
|---|-------|--------|----------|--------|------------|
| 1 | [Range server over a set](phase-01-range-server-over-a-set.md) | pending | P1 | 1.5d | - |
| 2 | [Minimal web UI, direct play](phase-02-web-ui-direct-play.md) | pending | P1 | 1d | 1 |
| 3 | [Disk cache](phase-03-disk-cache.md) | pending | P1 | 1d | 1 |
| 4 | [Transcoding to HLS](phase-04-transcoding-to-hls.md) | pending | P2 | 2d | 2,3 |
| 5 | [Remote access, auth and TLS](phase-05-remote-access.md) | pending | P2 | 0.5d | 2 |
| 6 | [Docs and operating notes](phase-06-docs.md) | pending | P3 | 0.5d | 5 |

Phases 1 through 2 are the first useful thing: the 162 course lessons play on
the LAN with no transcoding, no cache and no auth. Each later phase is
independently shippable.

## Verified facts this plan rests on

Checked in the codebase and against the live channel, not assumed:

| Fact | Evidence |
|---|---|
| Seeking to an arbitrary offset is possible | `DownloadIter::skip_chunks(n)` advances by `n × 512 KiB` (grammers-client 0.10 `files.rs:68`). A byte offset costs at most one discarded chunk |
| Download throughput is ~5-10 MB/s | `verify --full` pulled 6.53 GiB back and re-hashed it; enough for a 13.9 Mbit/s source to direct-play |
| Parts concatenate to the exact file | Independently re-hashed byte ranges of a 6.53 GiB film against the index; both parts and the set hash matched |
| What is playable is already defined | `mlib_spec::schema::PLAYABLE_SQL` |
| Library codec profiles | `mp4/h264/aac` (162 lessons) and `mkv/hevc/ac3` (films), from the live index |
| Browsers refuse AC3/E-AC3 and Matroska | See the brainstorm report's sources |
| Uplink ≈ 25 Mbit/s | Measured during the phase-1 upload gate |

## Decisions

- **Rust owns Telegram.** Bun never speaks MTProto, which also sidesteps
  GramJS being archived in July 2026 and continued as a fork.
- **The player reads `library.db` from disk.** It runs beside the uploader, so
  the encrypted package is not on its path. The package remains for clients
  that are not on this host.
- **Transcoding is for codecs and for bitrate.** A 13.9 Mbit/s source against
  a 25 Mbit/s uplink leaves no headroom, so remote viewing transcodes even
  when the codecs would have played.
- **Auth and TLS are delegated** to Cloudflare Tunnel or Caddy rather than
  built into the app.
- **One viewer.** No multi-user accounts, no watch history, no concurrent
  session management in v1.

## Scope note

The user chose to plan all four slices at once, over a recommendation to plan
only the first. The phase order preserves the intent: phases 1 and 2 are
shippable alone, and nothing in them presumes the transcoding design in phase
4. If phase 4's shape changes once phases 1-3 are real, only phase 4 is
rewritten.

## Cross-plan relationships

- `260914-1954-telegram-linux-uploader-mlib-spec-v2` (in progress, live gate
  outstanding) owns the Telegram code this plan reuses. No changes to it are
  required; `serve` is additive.
- `260915-1956-prebuilt-metadata-package-for-player` (complete) is not on this
  player's path. The package stays the delivery mechanism for off-host
  clients, and the format's reader algorithm still describes them.

## Success (whole plan)

- A course lesson plays in a browser on the LAN, seekable, with no
  transcoding.
- A film plays remotely through a transcode that stays under the uplink.
- The catalog lists exactly what `PLAYABLE_SQL` considers playable.
- A second play of the same title does not refetch it from Telegram.
- The service exposes nothing without authentication when reached externally.
- `cargo test` green; every file under `src/` within the 200-line rule; the
  Bun app has its own tests for the parts that are not glue.

## Open questions

Recorded per phase. Nothing blocking at plan level.
