# Phase 08 — Subtitle language and style follow the profile to every device

## Context links
- Existing sync (plan `plans/260920-2221-watch-state-across-devices/`): one pinned `#mlib-state` document per device, merged newest-wins per row with a device-id tie-break — web `web/src/state/sync.ts:53,77` (`StateSync`, `once`), `sync-record.ts:57` (`SYNC_FORMAT = 1`), `:110` (`ProfileState`), `:129` (`SyncRecord`), `merge.ts:85` (`mergeStates`), `tie-break.ts:20` (`keep`), `store.ts:383` (`exportRecord`), `:422` (`importMerged`); core `crates/mediagram-core/src/state/record.rs:37,69,93`, `record/parse.rs`, `merge.rs` (200 lines, full), `merge/tie_break.rs:37` (`keep`), `exchange.rs:20,68`.
- Preferences today (same table on both surfaces, never on the wire): web `web/src/state/store.ts:315-343` (`setPreference`; a null value deletes), core `crates/mediagram-core/src/state/preferences.rs:45-73`, schema `state/schema.rs:107-114`; `web/src/routes.ts:72-79` (`writeWorthSyncing` returns false for `/preferences`).
- Old readers drop unknown keys and rebuild their own document from their own db (`sync-record.ts:188-205`, `record/parse.rs`), fixture `web/test/fixtures/watch-state/record-parse.json` ("an unrecognised top-level key is ignored"); a higher `format` is refused whole → **no format bump**.
- Scopes: web `web/public/lib/playback/preference-scope.js:30-38`, Android `android/feature/player/src/main/kotlin/PreferenceScope.kt:25-30`; keys differ only for untagged manual films/episodes (web `web/src/package/posters.ts:57-67` slugs any kind, core `crates/mediagram-core/src/dto/summary.rs:171-185` only courses/docus).
- Cadence: `docs/system-architecture.md:479-501` (web `WriteDebounce`; Android `WatchSync` rounds on start, every 5 min, film left, background, peer push).
- Ratchets: `web/src/state/store.ts` 800 (at 799), `sync-record.ts` 328 (at 328), `schema.ts` 245; Rust 200 per file.
- Collision: plan `plans/260928-0047-profile-roles-pins-kids-age-limits` (pending) edits `web/src/state/{sync-record,merge,store}.ts`, `web/src/routes.ts` `writeWorthSyncing`, `crates/mediagram-core/src/state/**`. Phase 03 of this plan also edits `web/src/routes.ts` (the preload call at `:115`); 08 runs after 03.

## Overview
Priority P2. Effort 1d. Version: next **minor**. Status: pending. Depends on phases 03 and 04 (the `profile` preference exists). Never in parallel with profile-roles phases 01–05; rebase onto whichever lands first.

## Key decisions
- Synced names: `subtitle`, `cue-size`, `cue-backing`, `cue-offset`, in every scope (`key:`/`show:`/`set:` per show, and `profile` for the preferred language). `subtitle` values are track keys since phases 03/04 (`off`, `de`, `en:sdh`, `und`; a plain language code from older builds is the same key). `audio`, `speed`, `framing` stay per device (not asked for); the toggle's in-memory "last regular" is not a preference and is not synced.
- Wire: optional `ProfileState.preferences: [{scope, name, value, updatedAt}]` — the shape the original watch-state design sketched and never shipped. Old clients drop it harmlessly; each device only ever writes its own message.
- Merge: per (profile, scope, name) newest `updatedAt` wins; ties by device id (the existing `keep`). Import writes only when the merged row is newer than the local one (or equal-time but different, resolved by the tie-break).
- Local writes stamp `max(now, stored + 1)` so a row's time never goes backwards on one device (the rule the profile-roles contract uses).
- Synced names are never deleted: every writer stores a value (`off`, a track key, `100`, `shadow`, `0`) — verified today (`transport.js:312`, `subtitle-panel.js:127`, `SubtitleStyleController.kt:102-109`) and kept by phases 03/04 (picker, 'c', CC and the captions key write `off` or a key). A delete would come back from other devices; no tombstone is added (YAGNI).
- Triggers: web — preference writes become sync-worthy (debounced like every other write); Android — no new trigger, the existing cadence (film left, background, 5 min) carries them.

## Requirements
Web
- `web/src/state/preferences-record.ts` (new): type, parse (strings trimmed and capped as `short()` does, finite `updatedAt`, synced names only), export rows for a profile, import merged rows.
- `sync-record.ts`: `ProfileState.preferences?` + one parse call (net ≤ 0 lines — move/condense to stay at 328).
- `merge.ts`: fold `preferences` per profile via `keep` (new helper file if it would pass 200).
- `store.ts`: `exportRecord`/`importMerged` call the new module; `setPreference` stamps `max(now, stored+1)` (net ≤ 0 lines).
- `routes.ts` `writeWorthSyncing`: `/preferences` → `true`; comment updated.
- Fixtures: `record-parse.json` (preferences parsed; unknown name dropped; bad types dropped), `merge.json` (newest wins; tie by device; an old record without preferences keeps the other's rows).
Core
- `state/record.rs` field; `record/parse.rs`; `state/merge_preferences.rs` (new; `merge.rs` is full); `state/preferences_exchange.rs` (new) called from `exchange.rs`; `preferences.rs::set` stamp. Same fixtures.
Docs
- `docs/system-architecture.md`: watch-state section lists preferences; line 493 ("a preference, being per-device and unsynced, never does") rewritten; note the untagged-title scope difference.

## Architecture
```
device A: setPreference(profile, scope, name, value) ─stamp─> preferences
  └ round: exportRecord → ProfileState.preferences (synced names) → own #mlib-state doc
device B round: read docs → mergeStates (keep newest per scope+name) → importMerged → preferences
player open on B: remembered(show) / preferred(profile) / cue style read locally, as today
```

## Related code files
- Create: `web/src/state/preferences-record.ts`, `web/test/preferences-sync.test.ts`, `crates/mediagram-core/src/state/merge_preferences.rs`, `crates/mediagram-core/src/state/preferences_exchange.rs`, `…/preferences_exchange_tests.rs`.
- Modify: `web/src/state/sync-record.ts`, `web/src/state/merge.ts`, `web/src/state/store.ts`, `web/src/routes.ts`, `web/test/fixtures/watch-state/record-parse.json`, `web/test/fixtures/watch-state/merge.json`, `crates/mediagram-core/src/state/{record.rs,record/parse.rs,exchange.rs,preferences.rs,mod.rs}`, `docs/system-architecture.md`, `docs/project-changelog.md`.

## Implementation steps
1. Fixture cases first (shared by both surfaces).
2. Web module + wiring within ratchets; `bun test`.
3. Core port against the same fixtures; `cargo test -p mediagram-core`; rebuild `.so` + bindings only if the uniffi surface changed (it should not).
4. Docs; bump by pattern; changelog.

## Todo
- [ ] fixtures (parse, merge)
- [ ] web record/merge/export/import + trigger + stamp
- [ ] core record/merge/export/import + stamp
- [ ] docs, manifests, changelog
- [ ] cross-device check

## Success criteria
- `bun test` and `cargo test` green on the shared fixtures; web ratchets hold; Rust files ≤ 200 lines.
- Cross-device (test profile, user's go-ahead — profile data spreads household-wide; the web preview cannot show this, it syncs nothing): on the tablet set Settings → Profile → Subtitles = English for the test profile, background the app (a round); on the TV box, after its next round, a film with English tracks opens with English on for that profile. Set it back to Off afterwards.
- An older build's device keeps syncing watch state normally beside the new ones (its document simply has no `preferences`).

## Tests
| Level | What |
|---|---|
| Unit (both) | parse/merge fixtures; stamp never decreases; import only newer |
| Integration (web) | a `/preferences` PUT schedules a debounced round |
| Device | tablet → TV preference travel (above) |

## Risk assessment
| Risk | L × I | Mitigation |
|---|---|---|
| Collision with profile-roles edits to the same files | Med × Med | Sequential; rebase; shared fixtures merged by hand |
| State document grows (per-show rows) | Low × Low | Four names × shows actually changed; KBs per profile |
| Clock skew makes an old choice win | Low × Low | Newest-wins with the monotonic stamp, as for every other synced row |
| A delete of a synced name resurrects | Low × Low | No writer deletes them; comment at `setPreference` says so |

## Security
Parsed values are trimmed and capped before storing (existing `short()` rules); only allow-listed names are imported; nothing new is sent to any browser.

## Rollback
Revert: older builds ignore `preferences` in documents and keep their local rows; no schema change to undo.

## Next
None inside this plan.
