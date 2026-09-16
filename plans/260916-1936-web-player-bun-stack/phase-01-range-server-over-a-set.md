---
phase: 1
title: "Range server over a set"
status: completed
priority: P1
effort: "1.5d"
dependencies: []
---

# Phase 1: Range server over a set

## Overview
`mediagram serve` exposes a local HTTP API: what is playable, and the bytes of
a set as one virtual file, answering Range requests. Everything downstream —
the browser, ffmpeg — is a client of this and nothing else talks to Telegram.

## Key insight
A set's parts are raw byte-ranges of one original file, so the virtual file is
their concatenation in `off` order and needs no container awareness at all. A
Range request maps to: find the part containing `start`, skip
`floor(offset_in_part / 512 KiB)` chunks, discard the remainder of the first
chunk, then stream across part boundaries until the range is satisfied.

`DownloadIter::skip_chunks(n)` makes that cheap: at most one discarded 512 KiB
chunk per seek (grammers-client 0.10, `files.rs:68`). Without it, seeking
would mean downloading from zero every time, and the whole design would fail.

## Requirements
- Functional: `GET /sets` lists playable sets; `GET /sets/{id}/stream` serves
  the virtual file with Range support; `HEAD` reports size and
  `Accept-Ranges: bytes`.
- **Every response carries `Content-Length`**, including 206s. This is not
  cosmetic: ffmpeg cannot seek an HTTP source without it (phase 4), and
  without seeking it buffers from byte zero. Safari is also stricter than
  Chrome about a well-formed `Content-Range`.
- Non-functional: no part is ever fully buffered in memory; the process holds
  one Telegram session; the index is opened read-only.

## Architecture
```
GET /sets/{id}/stream
  Range: bytes=4000000000-4000999999
        │
        ├─ resolve: which parts overlap [start, end]
        ├─ per part: skip_chunks(offset / 512KiB), drop head remainder
        └─ stream chunks, stop at end
  206 Partial Content
  Content-Range: bytes 4000000000-4000999999/7011563463
```

Range maths is pure and testable without Telegram: given part offsets and
lengths, produce the list of `(part, skip_chunks, head_drop, take)` steps.

## Related Code Files
- Create: `crates/mediagram/src/serve/mod.rs`,
  `serve/range.rs` (pure: Range header parsing and the part walk),
  `serve/stream.rs` (Telegram-facing: chunk iteration across parts),
  `serve/routes.rs` (handlers), `crates/mediagram/src/commands/serve.rs`
- Modify: `crates/mediagram/src/main.rs`, `Cargo.toml` (HTTP server crate),
  `config.example.toml` (`serve_addr`)

## Implementation Steps
1. Choose the HTTP crate against the workspace's tokio 1.53 (see research
   report). Prefer the smallest thing that streams bodies and lets us write
   `Content-Range` by hand; a file-serving helper is no use here because the
   body is not a file.
2. `serve/range.rs`, pure: parse `Range: bytes=a-b`, `bytes=a-`, reject
   multi-range with 416, clamp to size, and walk the parts to produce the
   step list. Test with the real film's two-part geometry.
3. `serve/stream.rs`: turn one step into a chunk stream via `iter_download`
   plus `skip_chunks`, dropping the head remainder and truncating the tail.
4. `GET /sets`: query the index with `PLAYABLE_SQL`, returning id, title,
   kind, container, codecs, duration and total size. That codec information
   is what lets phase 2 decide direct play versus transcode.
5. `GET /sets/{id}/stream`: 200 with full body when no Range, 206 with
   `Content-Range` when ranged, 416 on an unsatisfiable range.
6. Bind to localhost by default. Exposure is phase 5's problem, and binding
   wide by default would make every later phase optional security.
7. Reuse `preinit_session_store` ordering: the session store must configure
   SQLite before the index is opened, as `main` already does.

## Success Criteria
- [x] `GET /sets` lists exactly what `PLAYABLE_SQL` matches
- [x] A full `GET` streams a whole set and the bytes match the source file
- [x] `bytes=0-` returns 206 with the whole remainder
- [x] A mid-file range returns exactly the requested bytes, verified against
      the same range read from the local source file
- [x] A range spanning the boundary between part 0 and part 1 is correct
- [x] An unsatisfiable range returns 416, a malformed one 400
- [x] Every response carries `Content-Length`; a 206 carries a well-formed
      `Content-Range`. Asserted in a test, because phase 4 silently degrades
      to buffering-from-zero when it is missing
- [ ] Playback works in Safari as well as Chrome, which is the stricter test
      of the two — carried into phase 2, which is where a page exists to
      play it in
- [x] Memory stays flat while streaming a 6.5 GiB set
- [x] The index file is byte-identical after serving

## Live acceptance, 2026-09-16

Run against the real channel with the 7,011,563,463-byte two-part film and a
one-part course lesson.

| Check | Result |
|---|---|
| `HEAD` | 200, `content-length: 7011563463`, `accept-ranges: bytes`, `content-type: video/x-matroska` |
| Five 2,000-byte ranges (file head, mid part 0, across the part boundary, start of part 1, file tail) | every one byte-identical to the same range of the local source file |
| 400 MiB slice from offset 2,000,000,000 | sha256 identical to the source file's |
| Whole one-part lesson, 18,968,265 bytes | sha256 identical to the recorded part hash |
| Seek latency, 64 KiB at four offsets up to 6.9 GB | 0.15-0.21 s, flat with depth: `skip_chunks` works as the design assumed |
| Throughput | 5.1-5.3 MB/s (~42 Mbit/s), above a 13.9 Mbit/s source |
| Memory over 800 MiB streamed | 30,324 kB → 30,488 kB |
| `library.db` sha256, before / during / after, including after shutdown | unchanged |
| 416 / 400 / 404 | as specified |

Not yet checked: two processes sharing the session store (`serve` running
while `add` runs). The SQLite init-order defect found this week makes this
worth proving rather than assuming.

## Risk Assessment
- **Seek cost.** Each seek discards up to 512 KiB. Acceptable, and the only
  alternative is a cache (phase 3).
- **Telegram rate limits under scrubbing.** A viewer dragging the scrub bar
  issues many seeks. Phase 3 is the mitigation; until then, note it.
- **File reference expiry.** A `Document` handle can go stale. Re-resolve the
  message on failure rather than surfacing a broken stream.
- **Two processes, one session store.** `serve` runs alongside a possible
  `add`. Both open the libsql session; the SQLite init-order defect found this
  week proves this area bites. Still unverified: the live run above used
  `serve` alone.
