---
phase: 2
title: "Bun Telegram client and Range server"
status: pending
priority: P1
effort: "2d"
dependencies: [1]
---

# Phase 2: Bun Telegram client and Range server

## Overview
The Bun app talks to Telegram itself, through `teleproto`, and serves a set's
parts as one virtual file. This is what lets the player run somewhere the
uploader is not.

## Why this reverses an earlier decision
The plan originally said "Rust owns Telegram; Bun never speaks MTProto",
partly because GramJS was archived in July 2026. That held while the player
was assumed to run beside the uploader. It does not hold once the player must
run anywhere: off that machine there is no `mediagram serve` to proxy to, and
a Rust sidecar on every host that plays video is a worse answer than a
TypeScript client in the app already there.

## Verified before planning on it
Measured against the live channel on 2026-09-16, with the real
7,011,563,463-byte two-part film:

| Claim | Result |
|---|---|
| `teleproto@1.229.0` runs on Bun 1.4.2 | imports, constructs a client, connects, authorizes |
| The uploader's session is portable | a 369-character `StringSession` built from `session.sqlite`'s auth key connected and authorized with **no second login** |
| Arbitrary byte ranges come back correct | five reads (part 0 byte 0, unaligned mid-part, part 0 tail, part 1 byte 0, deep unaligned) all byte-identical to the local source file |
| Seek cost | 46-152 ms, against 150-210 ms through the Rust server |

Two things the type definitions get wrong, found by running it:

- `IterDownloadParams.offset` is documented as handled precisely. It is not:
  Telegram answers `OFFSET_INVALID` unless `offset % 4096 == 0`.
- `limit` is documented as "rounded up to whole chunks". It is not: an
  unaligned limit answers `LIMIT_INVALID`.

The working shape is therefore: align the offset down to 4 KiB, choose a
`requestSize` from `{4096, 8192, … 524288}` (a 4 KiB multiple that divides
1 MiB), iterate, and drop the head. Same pattern as the Rust server, at 4 KiB
granularity instead of 512 KiB.

`client.iterDownload(file, params)` takes two arguments, not GramJS's single
options object.

## Requirements
- Functional: `GET /api/sets` and `GET /api/sets/:id/stream` match phase 1's
  contract exactly — 200, 206, 416, 400, `Content-Length` on every response,
  well-formed `Content-Range` on a 206.
- Functional: no response to a browser contains a session string, the channel
  id, or a message id.
- Functional: the set of bytes returned for any range is identical to what
  phase 1 returns for the same range. Phase 1 is the oracle.
- Non-functional: no part is ever buffered whole; one Telegram client per
  process; a stale file reference is re-resolved rather than surfaced as a
  broken stream.

## Architecture
```
Bun.serve
  ├─ catalog  ── bun:sqlite ── library.db   (phase 3 supplies it off-host)
  └─ stream   ── range plan ── teleproto ── Telegram
```

The range maths is a port of `crates/mediagram/src/serve/range.rs`, and its
tests port with it: same fixtures, same assertions, same part geometry. The
port is simpler in one respect — with a 4 KiB alignment the discarded head is
at most 4 KiB, so a small seek costs almost nothing.

## Related Code Files
- Create: `web/src/telegram/client.ts` (connect, resolve channel, fetch a
  part's message), `web/src/telegram/download.ts` (aligned reads, the head
  drop), `web/src/range.ts` (pure: header parsing and the part walk),
  `web/src/catalog.ts` (`bun:sqlite` over library.db),
  `web/src/routes.ts`, `web/src/server.ts`
- Create: `web/test/range.test.ts`, `web/test/http.test.ts`
- Modify: `crates/mediagram/src/commands/` — a command that emits the
  player's session string (see Security)

## Implementation Steps
1. Port `range.rs` to `web/src/range.ts` with its tests, adapting `CHUNK` to
   the 4 KiB alignment and the legal request sizes. Tests first; the Rust
   tests are the specification.
2. `telegram/client.ts`: connect from a `StringSession`, resolve the channel,
   fetch a message by id. Fail loudly when the session is unauthorized rather
   than prompting — the player may have no terminal.
3. `telegram/download.ts`: one planned read to a byte stream, aligned offset,
   head dropped, tail truncated. Stop as soon as the range is satisfied.
4. `catalog.ts` with `bun:sqlite`, opened **read-only**, asking the same
   `PLAYABLE_SQL` the Rust catalog asks. Keep the SQL in one place rather
   than paraphrasing it.
5. Routes, with phase 1's header contract asserted over a real socket.
6. A differential test: for a list of ranges, assert Bun and `mediagram serve`
   return identical bytes. This is the cheapest way to know the port is right,
   and it is why phase 1 is not being deleted.

## Success Criteria
- [ ] Byte-for-byte agreement with `mediagram serve` across at least a dozen
      ranges, including both part boundaries and the file's last byte
- [ ] A range crossing the part boundary is correct
- [ ] Every response carries `Content-Length`; a 206 carries a well-formed
      `Content-Range`
- [ ] 416 on unsatisfiable, 400 on malformed, 404 on not playable
- [ ] Memory flat while streaming several hundred MB
- [ ] Seeks anywhere in a 6.5 GiB set stay under about 250 ms
- [ ] The backend runs on a machine that has no `library.db` of its own,
      given a package (phase 3) and a session string
- [ ] No route's response body or headers contain the channel id, a message
      id, or session material — asserted, not assumed

## Security

**Two things are called "the player" and only one of them touches Telegram.**

| | Holds the session | Speaks MTProto | Sees channel/message ids |
|---|---|---|---|
| Player backend (Bun server) | yes | yes | yes |
| Web UI (browser) | never | never | never |

The browser is a client of the backend over HTTP, exactly as it would be of
any media server. It receives a catalog and a byte stream. It never receives
a session string, the channel id, or a message id — those are precisely what
`mlib-package-v1` treats as the secret worth encrypting, and handing them to
a browser tab would undo that. `PlayableSet` carries no location fields, and
`partLocations` stays server-side; an assertion in the route tests keeps it
that way.

The backend holds the account's MTProto auth key, because `upload.getFile`
authenticates every call and the protocol has no scoped credential. On the
uploader's machine that was already true. On a host you do not fully control
it is a different risk, because the key is the account, not just the library.
Consequences to decide before running the backend anywhere exposed:

- A dedicated Telegram account for the library limits the blast radius to the
  library. Worth doing if the player will live on rented hardware.
- The session string must never reach the repository, a log, an error
  message, or an HTTP response. The command that emits it prints to stdout
  only, writes no file by default, and is excluded from any diagnostic dump.
- Revocation is Telegram's "terminate session", and it invalidates the
  uploader's session too if they share an auth key. Deciding whether the
  player gets its own login or a copy of the uploader's is therefore a
  security decision, not just a convenience one.

## Risk Assessment
- **Two clients, one auth key.** MTProto allows several sessions per auth
  key, and the live probe ran while `serve` was stopped. Concurrent use by
  both is untested and could surface as dropped connections. Prove it, or
  give the player its own login.
- **teleproto is a one-maintainer fork.** Mitigated by the client surface
  being small and behind `telegram/client.ts`; the rest of the app does not
  know what speaks MTProto. If the fork dies, that file is the port.
- **Type definitions that lie.** Two found already, both by running it
  against Telegram. Treat the `.d.ts` as a hint and the server as the
  authority.
- **Bandwidth doubles off-host.** Telegram to the player, then player to the
  viewer. At home that second hop is the LAN. On a VPS it is paid egress, and
  a 13.9 Mbit/s film is about 6 GB an hour in each direction.
