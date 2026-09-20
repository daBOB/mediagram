# Phase 01 — The record, and the merge

**Status:** done — progress and completions; see Scope

## Context

- `web/src/state/schema.ts` — what there is to sync: `progress`, `watched`,
  `watchlist`, `kids`, `collections`, `preferences`
- `web/src/state/store.ts` — `snapshot(profileId)`, and the writers
- `crates/mediagram/src/commands/push_index.rs` — `#mlib-index v=2`, the
  caption convention a state message will follow

## What this phase is

The format and the arithmetic, with no Telegram anywhere near it. Everything
that can be got wrong here is silent — a merge that picks the older of two
positions loses an evening's watching and says nothing — so it is pure, and it
is tested before a single byte is sent.

## Identity

**The record is keyed by the profile's name, normalised; not by its id.**

A profile id is a UUID minted by whichever player created it, so "André" on
the laptop and "André" on the desktop are two ids for one viewer. Keying by id
would mean syncing the profile table too, and the desktop would then show two
"André" profiles — its own and the laptop's — which is worse than not syncing
at all.

The name is the only thing the viewer typed on both machines, so the name is
what identifies them. Two people genuinely sharing a name on one household
would collide, but they would also be indistinguishable in the picker, so they
will already have chosen different names.

The record still carries the writing device's local profile id, as provenance
rather than as identity: it costs nothing and makes a confused merge legible
afterwards.

## The wire format

One JSON document per device, uploaded whole and replaced whole.

```jsonc
{
  "format": 1,
  "device": "a stable id for this install",
  "writtenAt": 1789000000000,
  "profiles": [{
    "name": "André",
    "localId": "b398013d-…",         // provenance, not identity
    "progress":    [{ "setId": "01…", "at": 742, "duration": 1204, "updatedAt": 1789… }],
    "watched":     [{ "setId": "01…", "updatedAt": 1789… }],
    "watchlist":   [{ "setId": "01…", "updatedAt": 1789…, "listed": true }],
    "preferences": [{ "scope": "key:tmdb-tv-4608", "name": "audio", "value": "eng", "updatedAt": 1789… }]
  }],
  "kids": [{ "setId": "01…", "updatedAt": 1789…, "marked": true }]
}
```

`kids` sits outside `profiles` because it always has: a children's film is a
fact about the title, not about who is watching, and the v3 migration says so.

**Every row carries its own `updatedAt`.** Without one, a merge can only
prefer whole documents, and the device that pushed most recently would
overwrite a position it never knew about.

**Removals are rows too.** `listed: false` and `marked: false` rather than
absence — an absent row is indistinguishable from a device that has never
heard of the title, and taking something off a watchlist would not stick.

`collections` are deliberately left for later. They carry an order, which
merges differently from a set of independent facts, and getting the rest
working should not wait for it.

## The merge

```
mergeStates(records[]) -> one merged state
```

- Group by profile name, then by row key (`setId`, or `scope`+`name`).
- Within a key, the highest `updatedAt` wins.
- A tie breaks on the device id, so two devices merging the same pair always
  agree with each other. An arbitrary rule applied consistently beats a
  reasonable one applied differently on each machine.

**Clock skew is the risk worth naming.** `updatedAt` is `Date.now()` on the
writing device, so a machine an hour fast wins arguments it should lose.
Recorded rather than solved: the alternative is a logical clock per device,
which is a larger idea than this problem deserves until it actually bites.

## Files

**Create**
- `web/src/state/sync-record.ts` — the format, and reading one safely
- `web/src/state/merge.ts` — `mergeStates`
- `web/test/state-sync-record.test.ts`, `web/test/state-merge.test.ts`

**Modify**
- `web/src/state/store.ts` — `exportFor()` and `importMerged()`

## Todo

- [ ] `sync-record.ts`: build one from a store, and parse one defensively
- [ ] a document from a newer `format` is ignored, not guessed at
- [ ] `mergeStates`, with last-writer-wins per row and a deterministic tie
- [ ] removals survive a merge
- [ ] `importMerged` writes only what is actually newer, and never clears a
      position that is
- [ ] round trip: export, merge with itself, import, and nothing changes
- [ ] `./scripts/check.sh`

## Success criteria

- Two records with the same title at different times merge to the later one,
  whichever order they are given in.
- Merging a record with itself is the identity.
- A row this player has never seen arrives; a row it has newer stays.
- Nothing in this phase can reach Telegram, and no test needs it to.
