# Renaming a profile — design

**Asked 2026-10-20**, after "i cant edit profile celine": the owner plan
(`plans/261020-1535-owner-manages-every-profile/plan.md`, "Not in this plan") left renaming out, because sync knows a
viewer by its name and renaming one renames its identity on every device.

## The user's decisions (2026-10-20, verbatim from their choices; do not change)

- "Rename keeps history". The renamed profile carries its old name in the sync, so every device on this build or newer
  treats old and new as one person: history, Continue, My List and stats are all kept. A device on an older build (the
  TV, until it updates) shows both names for a while.
- Who may rename: the owner renames anyone, a household's admin renames its grown-ups, and a parent renames their kids.

Every other choice below is marked **controller's call (provisional)**.

## In short

A renamed profile's entry in the sync record gains two optional keys: when it took its current name (`renamedAt`) and
the names it had before (`formerNames`). Before anything is merged, every name in every document is mapped to one
person — a name and all its former names are one person, and the newest rename picks the name shown — and the existing
merge then runs unchanged on the rewritten records. A device takes the result in by renaming its own row in place
(rows are keyed by id, not by name), and folds two rows into one where an older build had already made both. Former
names stay reserved, so no new profile can take one.

## What the code does today

- **Identity is the normalised name, and only on the wire.** `ProfileState.name` "is what identifies one across
  devices"; `localId` is "provenance, not identity" (`web/src/state/sync-record.ts:122-125`; core
  `crates/mediagram-core/src/state/record.rs:94-99`). `normalName` is NFC, trimmed, lower-cased (`sync-record.ts:159-163`,
  `record.rs:163-167`).
- **The record carries no profile id and no created-at.** A device that meets a viewer mints its own id
  (`web/src/state/profiles-insert.ts:43`, via `findOrCreateProfile` at `:82-96`) and stamps `created_at` with its
  own import time (`:41`). Neither can tell "the old Celine" from "a new Celine" across devices.
- **Every merge keys by normalised name**: the rows (`web/src/state/merge.ts:84-108`; core
  `state/merge.rs:76-97`), stats (`stats-merge.ts:26`), roles including a kid's `parent` (`roles-merge.ts:85-103`;
  core `merge/roles.rs:83-121`), and a household's admin (`households-merge.ts:27-36`; core
  `merge/households.rs:25-36`). The shown spelling is the highest device id's (`merge.ts:102-108`).
- **Every local row keys by `profiles.id`.** `profiles.id` is the primary key (`web/src/state/schema.ts:62-66`) and
  progress, watchlist, collections, watched, preferences and stats reference it (`schema.ts:77-110, 143-177`;
  `stats-schema.ts:16-33`; core `state/schema.rs:28-48`). `parent_id` and `households.admin_id` are local ids
  (`roles-schema.ts:18, 69`). The chosen profile is an id: the browser's `mediagram.profile`
  (`web/public/lib/watch-state.js:22, 150-156`) and the core's `chosen_profile` (`state/profiles.rs:30, 71-97`).
  Names appear only when exporting: the entry (`store.ts:368-369`), a kid's parent (`roles-exchange.ts:35-38`), a
  household's admin (`households-exchange.ts:28-29`).
- **Import matches by name, and creates what it does not know** (`store.ts:412-417`; core `exchange.rs:86-96`;
  `profiles.rs:115-132`), then applies roles and households by the first local row of each name
  (`roles-exchange.ts:57-116`; `households-exchange.ts:39-63`; core `exchange/roles.rs:74-88`).
- **There is no tombstone for a profile.** A removed profile returns with the next round from any document that still
  names it (`docs/system-architecture.md:694-698`; core `profiles.rs:5-11`).
- **A reader drops keys it does not know.** The web builds each profile from named keys only
  (`sync-record.ts:192-211`), the core likewise (`record/parse.rs:74-117`). The core's state module is unchanged
  since 0.131.0 (`git diff 1099c825 HEAD -- crates/mediagram-core/src/state/` is empty), so the TV box parses and
  merges exactly as this code does.
- **Every device's last document stays on the channel.** Each device edits one message of its own, and every round
  merges all of them plus this device's fresh export (`sync.ts:38-41, 139-151`; core `sync.rs:134-156`). A retired
  device's document is merged forever.
- **A list's id is global.** `collections.id` is the table's primary key (`schema.ts:104-109`; core `schema.rs:42-47`),
  but the import looks a list up by id *and* profile and inserts when that pair is missing
  (`lists-exchange.ts:157-175`; core `exchange/lists/collections.rs:52-70`). Deleted lists are exported too
  (`lists-exchange.ts:70`). A failed import rolls the round back and nothing is published (`sync.ts:114-130`; core
  `sync.rs:112-122`). Section 7 shows why this matters for an older build.

## 1. The wire format

Two new optional keys on a profile's entry, beside `localId`:

```json
{ "name": "Celi", "localId": "5b0e…", "renamedAt": 1760965200000, "formerNames": ["celine"],
  "progress": [], "watched": [] }
```

- `renamedAt`: when this profile took the name it has now. A stamp like every row's (`isStamp`:
  `record-scalars.ts:47-49`, `MAX_STAMP` in `record.rs:49-62`). Written only when above 0, so a profile nobody renamed
  writes nothing new.
- `formerNames`: every name this person had before, normalised, sorted, each once, never its current name. Written
  only when not empty.
- **No `SYNC_FORMAT` bump**, for the reason `sync-record.ts:27-53` gives: an older reader that met a higher format
  would refuse the whole document. With new keys it drops them and reads a profile called "Celi".
- **Parse, read as hostile** (controller's call, provisional). A `renamedAt` that is not a stamp is absent (0). Each
  `formerNames` entry must be a string that is still a name after `cleanName`, at most 120 characters, as a stored name
  is (`profiles.ts:66-73`). Anything else is dropped on its own, and so is an entry equal to the profile's own name. A
  list longer than 32 entries is dropped whole, as an over-long hidden-sections list is (`roles-record.ts:68-72`).
  Neither key can drop the profile.
- Its own small module on both sides (`name-record.ts`, `record/name_record.rs`), spread into the entry the way
  `parseRoleKeys` is (`sync-record.ts:201`), because `record.rs` (172 lines) and `parse.rs` (175) are near their limit.

## 2. The merge

**A pre-pass resolves names, then the existing merge runs untouched.** New `renames.ts` (web) and `merge/renames.rs`
(core); `mergeStates`/`merge_states` call it first and attach its answer to each merged profile (one line each).

1. **One person per connected set of names.** Over every entry of every record: the entry's normalised name and each
   of its former names are one person (a union-find over names).
2. **The name shown is the newest rename's.** Within a person, the entry with the highest `renamedAt` wins (absent is
   0). Ties go to the greater device id, as `keep` does (`tie-break.ts:21-40`), so with nobody renamed the spelling
   is chosen exactly as today.
3. **Every name is rewritten** to the winner's spelling: each entry's `name`, each kid's `parent`, each household's
   `admin.name`. A name that belongs to no person stays as it is.
4. `mergeStates`, `mergeRoles`, `mergeStats` and `mergeHouseholds` then run on the rewritten records as they do today.
   Each merged profile also carries the winner's `renamedAt` and the person's former names: every name in the set but
   the current one, sorted, at most the first 32. Both are omitted when 0 or empty, so every existing merge fixture
   still holds.

The cases:

- **(a) Chains, A→B→C.** The renaming device carries all its former names (`C` with `["a","b"]`), and every
  current-build device writes back what it took in, so knowledge of a rename outlives the device that made it. Partial
  documents still join: a stale `B` listing `["a"]` and an older build's `A` both land in C.
- **(b) Two renames at once.** A→B on one device and A→C on another both list `a`, so B, C and A are one person. The
  newer `renamedAt` wins, ties by device id; the loser's name becomes a former name. Every device converges in one
  round.
- **(c) A new profile under an old name.** The record carries neither an id nor a created-at that could tell a new
  Celine from the old one (above). A guard on the rename stamp (an entry joins the person who gave its name up only if
  it took that name earlier) would separate them on current builds, but an older build merges the two by name itself
  (`merge.ts:84-101`) and writes the mix back under the old name, which then folds into the renamed person. So this is
  settled by **reservation** (section 4): no current build lets a new profile take a former name, and the merge needs
  no rule for it (controller's call, provisional). A new profile under an old name can still appear on an older build,
  or on a current device that removed the person locally and made the name again before the next round brought them
  back. Both are folded into the renamed person: the same thing that happens today when a name is removed and made
  again.
- **(d) Renaming back.** Allowed. B→A takes `a` out of the former names and adds `b`; stale documents still listing
  `a` join the same person, and the newer `renamedAt` names it A.
- **(e) An older build's document still saying the old name.** It has no `renamedAt`, so it joins the person through
  the former name and never wins the name. Its rows, its removals and its role keys merge into the person by their
  own stamps, exactly as another device's rows of the same viewer do. The old name is not brought back. What the
  older build shows itself is in section 7.
- **(f) Removing a renamed profile.** As today, removing is local and there is no profile tombstone. The person
  returns with the next round from any document that still holds them: under the new name while a current-build
  document carries it; **under the old name** if only older or stale documents name them. Tombstones of rows
  (`removed`, `unwatched`) are keyed by title inside a person, and the person is resolved first, so a removal made
  under either name counts. A profile tombstone is a non-goal.

## 3. The import

**Rows are not rekeyed. A rename is a name update on the row**, because only the wire uses names. New local columns,
the next schema group on each side (web v16→v17, core v12→v13): `renamed_at INTEGER NOT NULL DEFAULT 0` and
`former_names TEXT NOT NULL DEFAULT '[]'` (a sorted JSON list, as `hidden_sections` is). Existing rows read "never
renamed", and an untouched install's document stays byte-for-byte what 0.132 writes.

For each merged profile, before its rows are imported (replacing `findOrCreateProfile` at `store.ts:414` and
`profile_named_with_creation` at `exchange.rs:88`):

1. **Candidates**: the first local row of its current name and of each former name (the `firstOfEachName` map,
   `roles-exchange.ts:109-116`), each row once. Two local rows of the *same* name keep today's behaviour: only the
   first is matched.
2. **None**: create the row as today, with the merged `renamedAt` and former names.
3. **One or more**: keep the **oldest** (controller's call, provisional: it is the id the device has used longest, so
   the remembered choice usually survives). Fold any other into it: move its rows to the kept id (an update that
   ignores a key the kept row already has; the rows import that follows then writes the newest of the two, because
   this device's own export was part of the merge), point kids' `parent_id`, households' `admin_id` and the core's
   `chosen_profile` from it to the kept id, then delete it. Its role columns are not copied: its own export was part of
   the merge, so the role import that follows applies whichever value won.
4. **Rename the kept row** to the merged spelling when its normalised name differs or the merged `renamedAt` is newer
   than its own, and write the merged former names when they differ (compared as stored). Then rows, roles and
   households import as today; by then every local name matches the merged one.

All of it is inside the existing transaction, so a failure rolls the whole round back as today. A second round changes
nothing. Folding happens only where a device holds two names of one person: an older build that took the new name in
before it updated (section 7), or two devices that picked one name at the same time. On the web the browser remembers
the chosen id itself; when the row it named was folded away, the page asks "Who's watching?" again (`watch-state.js:
150-156`).

## 4. Name rules

- **A new name is unique across the install**, normalised, exactly as a new profile's is (`nameTaken`,
  `profiles-rules.ts:23-26`; core `rules.rs:22`). The refusal is the existing `name-taken`, "That name is already
  used on this install." (`household-words.js:27`).
- **Former names count as taken.** The callers that collect the existing names (`profiles-manage.ts:143-146`; core
  `manage/checks.rs:33-38`) also collect every local profile's former names. `nameTaken` and `profile-names.json` stay
  as they are.
- For a rename, the target's own current and former names are not counted: renaming back, or changing only the case
  ("celi" → "Celi"), is allowed. A case-only change carries a new `renamedAt`, so its spelling wins everywhere.
- **Former names stay reserved for good** (controller's call, provisional). Reason: the old name never stops being
  written. An older build writes it until it updates, and a retired device's last document keeps it on the channel
  for ever (`sync.ts:139-151`). A different person under that name would collect all of it, and an older build would
  mix the two itself. No device can tell when the last such document is gone. The name is freed only if the person is
  removed from every device.

## 5. The action `rename`

The rule (`profile-rules.json`, ported to `profiles-rules.ts`, `profiles/rules.rs` and `ProfileRoles.kt`) gains
`"rename"`:

- a kid renames nobody, itself included (today's first check, `profiles-rules.ts:58-59`);
- **the owner** renames anyone, in any household, itself included;
- **a household's admin** renames the grown-ups of its own household, itself included (controller's call,
  provisional: the decision says "its grown-ups");
- **a kid** is renamed by whoever manages it: `ownerOf`, which is its parent, or the household's admin when the parent
  is gone (controller's call, provisional: the same rule as `set-kids-age`, `profiles-rules.ts:72-73`);
- anyone else: no. That includes a grown-up who does not run their household renaming themselves (open question 2).

**How it composes with the owner plan.** That plan adds `owner` to the rule's profile view and lets the owner act
across households, in the same fixture and the same three ports, on `feat/owner-manages-all` now. The rename case is
one more branch beside its cases: the owner's bypass of the household check, then today's same-household check, then
the matrix above. Build this after that plan merges, from its `owner` field; built first, it would have to add `owner`
itself and collide with that plan's Task 1 (controller's call, provisional). The owner plan's "the owner is never the
target of their own owner powers" covers removing and hiding, not renaming; the user's "the owner renames anyone"
includes themselves.

The fixture gains cases for each line of the matrix, the across-households case both ways (owner yes, a household's
admin no), and somebody or a target not there.

## 6. Surfaces

- **Web server.** `PUT /api/profiles/:id/name` with `{actorId, pin, name}`: one more name in the `ACTION` route
  (`profiles-routes.ts:20, 75-90`). It answers in the order every management action does (`profiles-manage.ts:4-11`):
  `invalid` 400, `name-taken` 409, `not-found` 404, a kid acting or the rule 403, `wait` 429, `no-pin` 409,
  `wrong-pin` 403; done is 204. The method is `ProfileAccess.rename` (`profiles-access.ts`, 29 lines; `profiles-manage.ts`
  is at its limit), and the write sets name, `renamed_at` (clamped one past the stored stamp, as `profiles-write.ts:
  9-15` does) and former names in one update. Like every profile write, it starts a sync round
  (`routes.ts:89-97, 131`).
- **Web player, Manage profiles.** A **Rename** pill on every row Manage draws: Manage already lists exactly the rows
  the actor manages (`profile-api.js:108-114`, widened to every household for the owner by that plan), and those are
  the rows the rename rule allows. An admin or the owner also gets **Rename** beside "Change your PIN"
  (`profile-manage.js:174`). It opens a one-field prompt, "A new name for Maja", sent with the PIN held for the visit.
  A refusal shows through `refusalText`. `profile-manage.js` is at 200 lines, so the prompt goes in its own module
  (controller's call, provisional).
- **Rust core API.** `rename_profile(actor_id, pin, id, name) -> ProfileOutcome` in `api/state/profile_roles.rs`
  (164 lines), backed by `profiles/manage/rename.rs` beside `hidden_sections.rs` (`manage.rs` is at 190). The outcomes
  already exist (`profiles/outcome.rs:8-22`). It ships with the core from day one, with `FakeCore` and the generated
  bindings, so the Android phase is UI only.
- **Android.** Its Rename UI follows in its own phase. **Its core merges and imports renames from day one**: Android
  merges through the core (`sync.rs:153`), so a tablet that has the new core keeps a renamed profile as one person,
  and one without it splits it. That is a deliberate, recorded lag in the sense of CLAUDE.md § Surface Parity: the
  Android Manage screen has no Rename until that phase, and the phase that adds it says so. `RoleAction` gains
  `RENAME` with the rule (`ProfileRoles.kt:12`), because Kotlin runs `profile-rules.json`.
- **Shared fixtures.** A new `profile-renames.json`, shaped like `households-sync.json` (`{parse, merge}`), with the
  parse rules of section 1 and the merge cases (a)–(f) and the reference rewrites. The web
  (`shared-watch-state-fixtures.test.ts`) and the core (`tests/shared_watch_state_fixtures.rs`) run it. Kotlin does not
  parse or merge (it runs `profile-rules.json` and `daily-limit.json` only, `android/core/model/build.gradle.kts:16-17`),
  and Android's merge is the core, so the core's runner is Android's coverage. All three run the new `rename` rule
  cases.

## 7. What an older build does: the TV box on 0.131.0

**It depends on whether the renamed person has a hand-built list.** This is narrower than the user's "shows both names
for a while".

**Without a list** (today Celine, Nadin, TV test and TV kids have none), the TV shows both names. Say Celine is
renamed Celi:

- Its next round drops `renamedAt` and `formerNames`, meets "Celi" as a new viewer, creates a local Celi and gives her
  the history her entry carries (all of Celine's), her PIN, limits, hidden sections and household. A kid's parent
  link stays on the old row, since it is set once (`roles-exchange.ts:97-103`). The picker shows Celine and Celi.
- Celi keeps taking in everything watched anywhere. Celine takes in nothing new, because no current device exports
  that name any more.
- What the TV writes under Celine (positions, finishes, un-marks, list changes, preferences, stats, role changes) is
  published under Celine. Every current build folds it into Celi by its stamps, and it comes back to the TV under Celi
  one round later. Nothing is lost, and Celine is never brought back on a current build.
- The owner's claim or a household's admin may sit on either of the TV's two rows (ties by smaller name,
  `roles-merge.ts:127-135`; by device id, `households-merge.ts:31-33`). That affects only the TV.
- **A list made on the TV under Celine** turns this into the next case on the following round.

**With a list, live or deleted** (today andre has 29 and test has 1), **the TV stops syncing** until it updates. Its
import of the new name inserts the person's lists under a new local row. The TV already holds those list ids under the
old name, so the primary key refuses them, the transaction rolls back, and the round ends before publishing (section "What the code
does today"). The TV keeps working on its own data, takes in nothing from anyone, and publishes nothing, for every
profile. Nothing is lost: it publishes everything on its first round after the update.

**How long.** Until the TV installs a build with this core. It checks at most hourly when the app comes to the
foreground, downloads when nothing plays, and installs when the app goes to the background
(`docs/superpowers/specs/2026-10-02-android-self-update-design.md:156-168`). That is hours after an Android release
reaches the channel, if the TV is used. Phones and tablets never update themselves (`:189`), so they stay as they are
until someone installs over `adb`. On its first round with the new core, the TV renames its old row in place (a stalled
TV never made the new one), or folds the two rows into the older one (the old name's, the id the TV remembers as
chosen). Lists, rows, parent links and the
household admin follow. A device that never updates keeps showing both names, and its last document is folded on
every current build for ever.

**Hence the build order** (controller's call, provisional): ship the merge and import first, with no way to rename,
and release them on Android. Ship the rename action and the web control only after the TV and every tablet in use run
that core (open question 1). Don't remove the stale name on an older TV meanwhile: removing a grown-up there takes
their kids with it locally (`profiles-manage.ts:85-98`), and they only come back one round later.

## 8. Testing

- **Shared fixtures**: `profile-renames.json` (web and core); the `rename` cases in `profile-rules.json` (web, core,
  Kotlin). Every merge case is also run with its records reversed, so the answer does not depend on arrival order.
- **Migration tests**, web and core: the new columns read 0 and `[]`, and an untouched store exports exactly what
  0.132 did.
- **Import tests**, web and core (not shared; each store is its own). A rename in place keeps the row's id and its
  history. A fold keeps the older id, takes the newest of each row, moves lists without a key clash, and repoints a
  kid's parent, a household's admin and (core) the chosen profile. A second import changes nothing.
- **Two devices agree**, like `state-households-exchange.test.ts:173-193`. The laptop renames Oma to Omi. One round
  later the TV has one profile, with the same id as before, named Omi, with her history and `formerNames ["oma"]`, and
  a second round returns 0. Then an older-build document, simulated by stripping the two keys, still names Oma with a
  new position: the laptop files it under Omi and creates no Oma. The older build's own view (merging the stripped
  records) shows both names. A last case shows the list clash that section 7 rests on, so the build order is pinned by
  a test rather than by this page. The core repeats the round trip in `exchange_tests.rs`.
- **Route and page**: HTTP tests for every refusal and for the matrix; `profile-manage.test.ts` for which rows get
  Rename; a preview-harness walk, renaming a grown-up and a kid at desktop width and at 390 px.

## 9. Risks and non-goals

**Risks**

1. **A fold cannot be undone, and a former name joins two people everywhere.** A hostile or buggy document listing
   someone else's name as a former name merges two real viewers on every current device, rows and all. Mitigations:
   the rename refuses taken names, the list is capped and parsed as hostile, and fixtures pin the merge. The channel is
   the household's own, which is already trusted to write any viewer's rows.
2. **Older builds.** A person with a list stalls an older build's sync until it updates (section 7). Without one, the
   older build shows both names. A profile removed everywhere current can come back under its old name from stale
   documents (2f).
3. **Two devices picking one name before they hear of each other** become one person. Today this happens to two new
   profiles; rename adds a second way. The owner plan is changing the same rule, fixture and Manage page right now, so
   this has to be built after it.

Also: three implementations of one merge (the fixtures are the guard); line limits push most of this into new modules
on both sides; a report filed before a rename keeps the old name (`report/routes.ts:51`), as a snapshot should.

**Non-goals**: a profile tombstone; freeing a former name; showing "formerly Celine" anywhere; names unique per
household; Android's Rename UI (its own phase); renaming a household (already exists, `households-merge.ts`).

## The user's answers (2026-10-20)

1. **Rollout: "After devices update".** Ship the merge and import first (no way to rename), release them on Android,
   and add the Rename action and its control only once the TV runs that core and the tablet has been updated over
   `adb`. No device ever stalls.
2. **Self-rename: "No".** The owner renames anyone, a household's admin its grown-ups, a parent their kids; anyone else
   asks their household's admin.
3. **Former names: "Yes, reserved".** A former name is never free again on this install.
