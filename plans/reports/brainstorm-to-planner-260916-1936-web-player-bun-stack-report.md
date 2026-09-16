# Web player on a Bun stack — brainstorm outcome

Date: 2026-09-16. Supersedes "Android TV first" as the next client round;
Android is not cancelled, just no longer first.

## Problem

mediagram stores a library in one private Telegram channel. There is no
client. The roadmap assumed Android TV via UniFFI. A web player is wanted
first, on a Bun stack.

## What scouting found

- Pure Rust workspace (`mlib-spec`, `mediagram`). No JS/TS anywhere today.
- Player contract already specified: `docs/mlib-package-v1.md` §5 — read
  `library.db`, stream parts by `(chat_id, message_id)`. `PLAYABLE_SQL`
  defines playable.
- Real library codecs: `mp4/h264/aac` (162 course lessons) and
  `mkv/hevc/ac3` (films).
- Verified Telegram code exists in Rust: session, ranged download, part
  maths, hashing, resume.

## Two findings that shaped the design

**1. A browser cannot play half the library.** Chrome and Firefox refuse
AC3/E-AC3 outright, do not accept Matroska, and HEVC support is patchy and
platform-dependent. The films cannot direct-play. The courses can.

**2. Bandwidth forces transcoding anyway.** Measured uplink ≈ 25 Mbit/s.
Film source bitrate 13.9 Mbit/s — nominally fits, with no headroom. For
remote viewing transcoding is the bitrate fix, not only the codec fix.

**3. GramJS was archived 2026-07-14**, continued as the `teleproto` fork.
Relevant only if Bun talks MTProto — which this design avoids.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| 1 | Homelab deployment, reachable externally | User's choice. Implies auth + TLS as real requirements |
| 2 | Play everything; transcode when needed | User's choice. NVENC/QSV available on the host |
| 3 | Rust owns Telegram; Bun owns the UI | One protocol implementation, already verified. Sidesteps the archived-library question entirely |
| 4 | Disk cache between Telegram and playback | Without it, every seek refetches at 512 KiB per RPC |
| 5 | Auth and TLS delegated to Cloudflare Tunnel or Caddy | Cheaper and safer than building login into the app |
| 6 | Build in four slices, direct-play LAN first | Four subsystems at once is four things to debug at once |

## Architecture

```
browser ──HLS/direct──> Bun (UI, session control)
                           │  spawns
                           ▼
                        ffmpeg ──HTTP Range──┐
                                             ▼
browser ──direct play─────────────> mediagram serve (Rust)
                                       grammers session
                                       parts -> virtual file
                                       disk cache
                                             │
                                             ▼
                                        Telegram
```

- **`mediagram serve`** (new Rust command): lists playable sets via
  `PLAYABLE_SQL`; serves HTTP Range over the virtual file formed by
  concatenating a set's parts in `off` order; backed by an LRU disk cache.
- **Bun**: static UI + session control. Chooses direct play vs transcode from
  `container`/`vcodec`/`acodec` in the index. Never speaks MTProto.
- **ffmpeg**: only in the transcode path. Reads ranges from the Rust service,
  emits HLS. `hevc -> h264` via NVENC, `ac3 -> aac`, bitrate capped under the
  uplink.

## Requirements captured

- **Expected output**: a web page on the homelab listing the library and
  playing a title in a browser, remotely reachable.
- **Acceptance**: a course lesson direct-plays on LAN; a film plays remotely
  via transcode within uplink; seeking works; the library list matches
  `PLAYABLE_SQL`.
- **Out of scope this round**: Android client, the encrypted package path (the
  player reads `library.db` from disk), multi-user accounts, watch history,
  subtitles rendering beyond what the browser does natively.
- **Constraints**: Rust remains the only Telegram implementation; `mlib-spec`
  stays the source of truth for schema/format; no re-encode at upload time.
- **Touchpoints**: `crates/mediagram/src/{telegram,index,verify}`,
  `mlib_spec::schema::PLAYABLE_SQL`, `docs/mlib-package-v1.md` (reader
  contract), `docs/system-architecture.md` (data flow section).

## Approaches considered

| Approach | Verdict |
|---|---|
| Bun speaks MTProto via teleproto | Rejected. Second protocol implementation; adopts a fork archived two months prior; still hits the codec wall |
| Browser speaks MTProto directly | Rejected. Session key in the browser; no transcoding possible; only the mp4 courses would ever play |
| Normalize codecs at upload (`prepare --for-web`) | Rejected as primary. Lossy re-encode of the archive to serve one client. Keep as an option for a future low-power client |
| **Rust serves ranges, Bun serves UI, ffmpeg between** | **Chosen.** One Telegram implementation, transcoding where it belongs, Bun stays a UI concern |

## Build order

1. **`mediagram serve` + minimal page, LAN, direct play only.** Proves
   Telegram → HTTP Range → browser with no transcoding. Usable for the 162
   course lessons on day one.
2. **Disk cache.** Makes seeking and replay tolerable.
3. **Transcoding.** ffmpeg sessions, HLS, NVENC, bitrate ladder.
4. **Remote access.** Tunnel/proxy, auth, TLS.

Each slice is independently useful. Slice 1 is small.

## Risks

| Risk | Mitigation |
|---|---|
| Telegram rate limits under seek-heavy playback | Cache first (slice 2 before slice 3); coalesce ranges; measure before tuning |
| ffmpeg session sprawl (one process per viewer, leaks) | Single-viewer assumption for v1; explicit session lifetime; kill on disconnect |
| Seeking into a transcode restarts ffmpeg | Accept restart-on-seek in v1; keyframe-aligned segments; revisit only if painful |
| Uplink saturation | Cap transcode bitrate below measured uplink; direct play only on LAN |
| Scope creep into Jellyfin | The build order is the guard: ship slice 1 before deciding slice 3's shape |

## Unresolved questions

1. Segment format for the transcode path: HLS (widest support, hls.js) vs
   fMP4 over MSE. Recommend HLS for v1; decide at slice 3.
2. UI framework: plain TS + hls.js vs Svelte. Decide at slice 1; either is
   small.
3. Where the cache lives and its size budget (host disk is 899 GB free).
4. Whether `serve` reuses the running session file or needs its own —
   two processes opening one libsql session store needs checking, given the
   SQLite init-order defect already found this week.
