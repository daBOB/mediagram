# Player system information

Four surfaces that say what the player already knows. Design:
[`docs/superpowers/specs/2026-09-19-player-system-information-design.md`](../../docs/superpowers/specs/2026-09-19-player-system-information-design.md).

## Phases

| # | Phase | Status |
|---|---|---|
| 01 | [Per-title technical line](phase-01-per-title-technical-line.md) | complete |
| 02 | [HUD reassurance](phase-02-hud-reassurance.md) | complete |
| 03 | [Colophon](phase-03-colophon.md) | complete |
| 04 | [Status snapshot and route](phase-04-status-snapshot-and-route.md) | complete |
| 05 | [Diagnostics panel](phase-05-diagnostics-panel.md) | complete |

## Order and dependencies

01, 02 and 03 are independent of each other and of the backend. 05 depends on
04. Built in this order so something is visible after the first change rather
than after the last.

## Constraints

- No `chat_id`, `message_id` or `doc_id` on any surface.
- `/api/status` answers 404, not 403, to a non-local client.
- `routes.ts` does not grow: the status route is its own router, following
  `state/routes.ts`.
- Every source file stays under 200 lines.
- `scripts/check.sh` passes before anything is committed.

## Definition of done

All five phases complete, `bun test` green, `scripts/check.sh` green, and
`docs/system-architecture.md` §7 describing the new module.

## Implementation log

All five phases complete. `scripts/check.sh` green: clippy, `cargo test`,
and 649 bun tests across 47 files (up from 600).

**Two corrections the build made to the design.**

The design claimed the player never said *why* a title was being converted.
It did — `noteFor` in `player.js` has carried `decision.reason` all along.
What had no reason on it was the shelf badge, so the reason went there
instead, as a tooltip rather than as more text on a wall of cards.

`RefreshResult` gained an `identity` field rather than the design's plan of
reading the pointer's `created_at` directly. After a `kept` those are two
different catalogues, and the one worth reporting is the one the queries
actually run against.

**One thing found by a test rather than by reading.** `Number(null)` is `0`,
and a fill rate of `0.0` is a real reading — a dead stall — so an absent
measurement had to be separated from a measured zero explicitly instead of
left to coercion.

**Two files were already over the 200-line rule** before this work and are
still over it: `cache/reader.ts` (274 → 289) and `transcode/registry.ts`
(266 → 290). Not split here; noted rather than hidden.

**Verified by a stub harness**, not by the real player, which holds the auth
key: the real `startServer` with a fake `ByteSource` and no Telegram, driven
headlessly. `/api/status` answered 200 to loopback, the panel rendered and
polled, the colophon read
`three films · one episode · one lesson · nine hours · 31 GB · published 3 days
ago · schema 6 · Status`, and the HUD read
`2160p · HDR10 · mkv · hevc · eac3 · 14 GB · 5 parts · 15 Mbps`.

## Second pass

**The remaining readings from the inventory**, and **System** in the masthead.

Added: upstream throughput (derived by the page from two readings — the
server does not know its own polling cadence, and an average since startup
describes a different situation), failed upstream reads (`TelegramSource`
counts stream errors; a cancel is not one, since a seek cancels a read every
time), transcode bytes on disk (`status/dir-bytes.ts` — no budget, no
eviction beyond the idle reaper), and resident memory.

**Not added, and why.** FLOOD_WAIT counts and reconnects: teleproto absorbs
both inside the client, and there is nothing to hook without reaching into
its internals. A counter that could only ever read zero would be a worse
answer than no counter. A playback-mode line in the HUD was dropped as
redundant — the note already states the conversion and its reason, and the
technical line already states the file.

**The menu.** `#/status` became `#/system`, the heading became "System", and
the colophon link was removed: one way in, not two. The entry is `hidden`
until `/api/status` answers a `HEAD`, so a remote viewer still learns
nothing. Adding it pushed the masthead onto two lines at 1440px — by eight
pixels, because a wrapping flex container breaks on its items' *base* sizes
before shrinking anything. `.search` went from a 19rem basis to 17rem rather
than touching the masthead's gaps, which are the page's rhythm.

Gate green: 664 tests across 48 files, clippy and `cargo test` clean.
Re-verified through the stub harness: masthead on one line at 1440 and
wrapping as designed at 900, the rate deriving between polls, and **zero
`/api/status` requests in the six seconds after navigating away**.
