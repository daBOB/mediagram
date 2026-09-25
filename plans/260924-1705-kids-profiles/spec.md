# Kids profiles — design spec

Date: 2026-09-24 · Status: awaiting review · Target version: 0.43.0

## Intent

A profile can be created as a **kids profile**. A kids profile sees only films
and series rated **FSK 12 or under**, on the web player and the Android app
alike. It is a **filter, not a lock**: nothing stops a child choosing another
profile, and the server does not enforce it.

Decisions taken with the user (2026-09-24):

| Question | Decision |
|---|---|
| Strictness | Filter only — no PIN, no server enforcement |
| Unrated titles | Hidden, unless marked for Kids by hand |
| Surfaces | Web and Android in the same piece of work |
| Storage/sync | Flag on the profile, carried in the sync record (approach A) |

## Rule

A title is **allowed** for a kids profile when:

- its rating is FSK 0, 6 or 12 (`kidsVerdict` = `safe`, `KIDS_AGE_LIMIT = 12`), or
- it is unrated and marked for Kids by hand (the existing device-wide `kids` list).

Everything else is hidden: FSK 16 and 18, unrated titles not marked, and so
every tutorial/course unless marked. A rated title is judged by its rating
alone; a hand mark does not override it (unchanged from today's `kidsShelf`).
Episodes carry their show's rating, so a series is allowed or hidden whole.

The rule already exists on both surfaces (`web/public/lib/age-rating.js`,
`android/core/model/.../AgeRating.kt`); it is reused, not restated.

## Data

**Web** — `state.db` schema 6 → 7: `ALTER TABLE profiles ADD COLUMN kids
INTEGER NOT NULL DEFAULT 0`. Existing profiles stay ordinary. `Profile` gains
`kids: boolean`.

**Rust core** (Android) — the state schema gains the same column;
`Profile { id, name }` → `{ id, name, kids }`, through the uniffi bindings to
the Kotlin `Profile` model.

**API** — `POST /api/profiles` accepts `{ name, kids?: boolean }`;
`GET /api/profiles` returns `kids` per profile. Rust: `create_profile(name,
kids)`. The flag is set at creation only; there is no endpoint to change it.

## Sync

- `ProfileState` gains optional `kids`, written **only when true**; an
  ordinary profile's entry is byte-for-byte unchanged. `SYNC_FORMAT` stays 1,
  as when `kids?` (the hand-marked list) was added.
- Both parsers (`sync-record.ts`, `record.rs`) read it as optional; absent
  means false.
- **Merge:** profiles group by normalised name as today; the merged profile is
  kids if **any** record marks it. The flag is sticky: sync never turns it off.
- **Import:** an existing local profile of that name becomes kids when the
  merged answer is yes; a profile the import creates is created with it.
- **Older devices** read the key as absent and show that profile unfiltered;
  their own records lack the key, which cannot unset it under "any says yes".
  This lasts only until they update and is documented.

## Where the filter applies

Applied **once, where each surface takes in its catalog**, so every view built
from it inherits it.

**Web** — the catalog rows are filtered before `groupLibrary`, `byId` and the
shelf counts. Covered by construction: Movies (all pages), Series, Tutorials,
Home rows, Continue/Next up, genre pages, Kids shelf, Featured, Play next.
Search results (server-side) are kept only if present in the filtered
catalog. Watchlist and Collections show only allowed titles.

**Android** — the same filter at the single point `MediaSet`s enter the
catalog repository; shelves, search and title pages derive from it.

**Switching profile** rebuilds the library from the last catalog received —
no re-download. A catalog refresh re-applies it.

## UI

- **Web create:** the profile picker's `window.prompt` becomes a small form in
  the page's existing style: name field and a "Kids profile — only FSK 12 and
  under" checkbox.
- **Android create:** the name dialog gains the same switch.
- **Both:** a kids profile's tile in "Who's watching?" carries a small "Kids"
  label.
- **Inside a kids profile:** the player's "Mark for Kids" control is hidden,
  so a child cannot approve an unrated title for themselves.
- **Empty library:** "Nothing rated FSK 12 or under yet."
- **Correcting a mistake:** remove the profile and create it again, from the
  web (Android cannot remove profiles today).

## Out of scope

PIN or profile lock; server-side enforcement (a direct stream URL still
plays); changing the flag after creation; a per-profile age threshold; cache
changes (the chunk cache stays device-wide and shared by all profiles).

## Testing

- **Shared sync fixtures** (`web/test/fixtures/watch-state/`): a record with
  `"kids": true`; both readers parse it and export it only when true.
- **Merge:** two devices disagree on the same name → kids; import upgrades an
  existing ordinary profile; nothing downgrades.
- **Filter table, same cases on both surfaces:** FSK 0/6/12 shown; 16/18
  hidden; unrated hidden; unrated + hand-marked shown; rated + hand-marked
  judged by rating; course hidden.
- **Web app-level:** switching to a kids profile hides an FSK 16 film from
  Movies, search, Home and Featured; switching back restores it without a
  catalog fetch; the Kids-mark control is absent.
- **Web server:** migration 6→7 keeps profiles ordinary; `POST` with `kids`
  round-trips through `GET`.
- **Android:** unit tests for the repository filter and the dialog switch.
- **Real device (adb):** a kids profile created on the web appears as Kids on
  the phone after sync, with a filtered catalog.
- **Web look:** checked on the stub harness, never the real player.

## Docs and release

- `docs/system-architecture.md`: a Kids profiles section (rule, sticky merge,
  filter-not-lock, older devices, removal from the web) and the optional `kids`
  key in the sync-record prose.
- `docs/project-changelog.md`: an Added entry.
- Version 0.43.0 in all three manifests (minor: new feature, automatic 6→7
  migration).
