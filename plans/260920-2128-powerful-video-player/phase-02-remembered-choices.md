# Phase 02 — What a viewer chose, remembered

**Status:** not started

## Context

- `web/src/state/schema.ts` — `STATE_SCHEMA = 4`, and `GROUPS[n]` takes a file
  to version `n+1`
- `web/src/state/store.ts` — `migrate`, which replays only what it has not had
- `web/public/lib/watch-state.js` — the browser's copy
- `crates/mlib-spec` — a *different* database and a different version number;
  the two must not be confused, and `schema.ts` says so at the top

## The problem

A German/English series is 22 episodes. Choosing English is choosing it 22
times, and on the way it costs 22 conversions. The same is true of a subtitle
language, and of a playback speed on a lecture course.

None of it is remembered because there is nowhere to put it.

## The shape

A fifth migration, and one table:

```sql
CREATE TABLE preferences(
  profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  scope TEXT NOT NULL,     -- a show's key, or a set's id
  name  TEXT NOT NULL,     -- 'audio' | 'subtitle' | 'speed' | …
  value TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(profile_id, scope, name)
)
```

**Scope is the show where there is one, the set where there is not.** A series
remembers across its episodes; a film remembers for itself. `show_key` in the
index is the key that already groups episodes, so it is the one to use rather
than a new idea about what a show is.

**Name and value as text, not a column each.** A column per preference is a
migration per preference, and these are going to keep arriving — this phase
adds three and phase 03 adds four more. The cost is that nothing constrains
the value, so the reader that turns it back into a choice is where the
validation lives, and it is written as if the row were hostile.

**Preferences are a hint, never an instruction.** A remembered audio track
that no longer exists in a re-uploaded file must not stop the title playing:
every read falls back to what the file actually offers.

## Files

**Modify** `web/src/state/schema.ts`, `web/src/state/store.ts`,
`web/src/state/routes.ts`, `web/public/lib/watch-state.js`,
`web/public/lib/player.js`

**Create** `web/src/state/preferences.ts`, `web/test/state-preferences.test.ts`

## Todo

- [ ] `GROUPS[4]`, and `STATE_SCHEMA = 5`
- [ ] `scopeOf(set)` — show key, else set id — pure and tested
- [ ] read/write through the existing state route, under the same guard
- [ ] audio language remembered and applied on open
- [ ] subtitle language remembered and applied on open
- [ ] speed remembered and applied on open
- [ ] a remembered choice the file no longer has is ignored, not obeyed
- [ ] migration test: a v4 file with rows opens as v5 and keeps them
- [ ] `./scripts/check.sh`

## Success criteria

- Pick English on episode 1; episode 2 opens on English without a conversion
  being asked for twice.
- A film remembers for itself and does not leak its choice to another film.
- A v4 database opens, migrates, and every existing position survives.
- Deleting a profile takes its preferences with it.

## Security

Nothing new is exposed: the same local-only state route, the same JSON
content-type guard. A preference is a language tag or a number, and it is
validated on read rather than trusted because this player wrote it.
