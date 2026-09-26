# Sync-watched removal tombstone, and prompt publish

## Overview

Two cross-device watch-state sync defects, fixed on both engines.

1. Un-marking `watched` does not survive a sync round: `watched` has no
   tombstone, so a later round re-imports an older device's mark and the
   un-mark is lost. Give it the same `removed` tombstone the watchlist,
   Kids and collections already carry.
2. A local write only reaches another device on the 5-minute timer, on
   another device's push, or on shutdown. Add a debounced round a few
   seconds after the last local write, on the web (Android already has
   `WatchSync.soon()`).

## Design decision — watched removal

**Revised after review** (`plans/reports/code-reviewer-260925-1603-sync-watched-removal-report.md`,
finding C1): the first pass carried a removal as `removed: true` on the same
`WatchedRow`, mirroring the watchlist. That is unsafe specifically for
`watched`, because there are builds in the fleet right now that predate
tombstone support for it (unlike the watchlist, whose tombstone shipped and
fully replaced the old behaviour fleet-wide already). Such a build reads
`{setId, updatedAt, removed: true}`, drops the flag it does not recognise,
and imports the row as a *live* mark at that same moment — and the next
merge, weighing that resurrected live row against the real removal at an
exact tie, decides by device id rather than by what happened, wrongly about
half the time, permanently (the old build never learns of the removal, so
it never stops re-exporting that same live row).

- Wire (`sync-record.ts` / `record.rs`): a removal travels as its own row,
  `UnwatchedRow`, on its own key (`unwatched`), not a flag on `WatchedRow`.
  An old reader simply does not know to look for that key and drops it, the
  same way it already drops `kids` and its optional siblings — leaving its
  own live mark unchanged and always older than a removal a new device
  holds. `UnwatchedRow` also carries `lastFinishedAt`, the `finished_at` it
  took the mark from.
- Local storage (`schema.ts` v8 / Rust `schema.rs` v4, unchanged by the
  revision): `watched` gains a nullable `removed_at`. `finished_at` keeps
  meaning "last marked finished"; `removed_at` is null while currently
  watched, and set at least one millisecond past `finished_at`
  (`MAX(now, finished_at + 1)`) so this device's own clock running behind
  another device's cannot let an imported live mark outrun its own removal.
- Merge (`watched-reconcile.ts` / `merge/watched.rs`): a live row and its
  removal, each already the per-kind LWW winner, are weighed against each
  other — the removal wins whenever its `updatedAt` is at least as new as
  the live row's; **a tie goes to the removal outright, not to a device-id
  tie-break**, since the two rows are unrelated claims from different
  devices, not two writes of the same kind. `progress` is filtered against
  whichever side won: a live row's own time, or a removal's
  `lastFinishedAt` — so a position from before the finish a removal carries
  stays suppressed, while a genuine rewatch made since survives.
- Import (`watched-exchange.ts` / `watched_exchange.rs`): each kind is a
  single-kind writer again, the merge having already decided which one
  applies. A live row supersedes stale local progress ≤ its own time; a
  removal supersedes it ≤ `lastFinishedAt`, never touching progress beyond
  that — un-marking never did, on this device or any other.
- Old records without `unwatched` still parse; the field is absent, not
  empty, and behaves exactly as a document from before this feature.

Also from the same review: **H1**, the write debounce (phase 3) must not
fire on the player's ten-second progress autosave — only the final flush on
leaving a title or pausing, plus watched/watchlist/Kids/profile writes,
never a preference. **L1**, `WriteDebounce.stop()` now latches so a write
landing after it cannot re-arm a timer against closing resources. **M2**,
the removal clamp above. **L4**, the Rust "un-mark never touches a
position" test now actually sets a position after the mark, not before,
where `set_watched(true)` had already cleared it.

**Re-review, three more:** **R1** — marking watched again is clamped the
same way M2 clamps a removal: `finished_at = MAX(now, removed_at + 1)`, so
a re-mark always beats a future-dated removal (a value this device only
knows about through an import carrying another device's ahead-running
clock). **R2** — import disagreed with merge on an exact tie: it skipped a
removal whenever the local live mark's time was merely *as new*, but
`watched-reconcile.ts` gives an exact tie to the removal. Fixed to skip
only when the local live mark is *strictly* newer; a local removal already
standing keeps the ordinary same-kind rule, where a tie changes nothing
either way. **R3** — inferring "final" from the HTTP method was fragile: an
older browser that refuses `sendBeacon` a JSON body falls back to the same
PUT the periodic tick uses, silently losing the trigger. The final flush
now marks itself explicitly, `?final=1`, on both the beacon and its PUT
fallback (`flushProgress` in `watch-state.js`); `pause` uses it too, having
switched to the final flush for H1. `writeWorthSyncing` reads that marker,
not the method, and a progress `DELETE` (forgetting a position outright)
always counts, no marker needed.

## Phases

- [x] [Phase 1 — web: watched tombstone](phase-01-web-watched-removal-tombstone.md)
- [x] [Phase 2 — Rust core: watched tombstone, mirrored](phase-02-rust-core-watched-removal-tombstone.md)
- [x] [Phase 3 — debounced sync after a local write](phase-03-debounced-write-triggered-sync.md)

## Key references

- `docs/system-architecture.md` — "Sync is the web's channel sync" (~line 670)
- `plans/260920-2221-watch-state-across-devices/phase-01-the-record-and-the-merge.md`
- `plans/260922-2135-android-watch-state-sync/phase-08-list-sync-record-extension.md`
- `web/src/state/lists-exchange.ts` — the tombstone shape `UnwatchedRow`
  deliberately does not reuse (see the revision note above for why not)
- `android/core/data/src/main/kotlin/WatchSync.kt` — Android's `soon()`, the
  parity target for phase 3
- `plans/reports/code-reviewer-260925-1603-sync-watched-removal-report.md` —
  the review that reshaped the removal design and narrowed phase 3's trigger

## Verify live

1. `ANDROID_HOME=/home/andre/android-sdk timeout 1500 ./scripts/check.sh` green.
2. Two profiles, mark a title watched on one device, sync, un-mark it, sync
   again from a third device holding only the old "watched" record with no
   `unwatched` key (simulating a pre-fix build) — confirm the removal still
   wins on the two updated devices, and the old-shaped device keeps showing
   "watched" until it is itself updated.
3. Watch `MEDIAGRAM_SYNC_STATE=1` player logs while a film plays: no
   `sync (write)` line every ten seconds. Un-mark a title, or leave/pause a
   playing title — a `sync (write)` line follows a few seconds later.
