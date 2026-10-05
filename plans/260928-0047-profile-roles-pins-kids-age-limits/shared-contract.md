# Shared contract — profile roles, PINs, per-kid age limits

Every phase implements against this file. A phase that needs a name, type,
reason or wire key not listed here stops and asks — it does not invent one.
The web is authoritative (`web/test/fixtures/watch-state/` pins the core to
it); where this file and the spec disagree, this file is the later decision
and the spec is amended in phase 08.

Spec: `docs/superpowers/specs/2026-09-28-profile-roles-design.md`.

## 1. Roles

A profile is a **kid** (`kids = 1`) or a **grown-up** (`kids = 0`). One
grown-up may be the **admin** (`admin_claimed_at IS NOT NULL`).

`ownerOf(kid)` = `kid.parent_id` when that id names an existing grown-up,
otherwise the admin's id, otherwise nobody.

## 2. The rule — `allowed(profiles, actorId, action, targetId)`

Pure. `profiles` is every local profile as a `RoleView`
`{ id, kids: boolean, admin: boolean, parentId: string | null }`. Returns
`true`/`false`; never throws. `actor` missing or a kid → `false`.

| `action` | allowed when |
|---|---|
| `create-grown-up` | actor is admin (`targetId` ignored) |
| `create-kid` | actor is a grown-up (`targetId` ignored); the new kid's `parent_id` = actor |
| `remove` | target is not the admin, and either target is a grown-up ≠ actor and actor is admin, or target is a kid and `ownerOf(target) == actor` |
| `set-pin` | target is a grown-up, and target == actor or actor is admin |
| `set-kids-age` | target is a kid and `ownerOf(target) == actor` |

Removing a grown-up also removes every kid whose `parent_id` is that
grown-up (explicit `DELETE … WHERE parent_id = ? AND kids = 1`, not a
foreign key). Removing a kid removes only that kid, even when another kid's
`parent_id` names it (sync sets a parent by name, so it can).

**Admin means a grown-up (amended 2026-10-04).** In the rule, a view counts
as the admin only when `admin && !kids`: `ownerOf` never picks a kid, and
`remove` refuses only a grown-up admin. A view built per §8 never says a kid
is admin, but the rule is total over its input, and `profile-rules.json`
pins it (the three "a kid said to be the admin" cases). Likewise `claim-admin`'s
"an admin already exists" counts only a grown-up's claim (§8), so a claim
left on a kid's row never blocks one.

## 3. Outcomes and the order they are checked

Reasons, as strings on the web and `ProfileOutcome` variants in the core:

| reason | HTTP | core |
|---|---|---|
| `invalid` — malformed input (name, PIN format, age not 6/12) | 400 | `Invalid` |
| `name-taken` — a new profile's name is one a profile here already answers to (amended 2026-10-04) | 409 | `NameTaken` |
| `not-found` — actor or target id names nobody | 404 | `NotFound` |
| `wait` — lockout active; body carries `retryAfter` seconds | 429 + `Retry-After` | `Wait { seconds }` |
| `no-pin` — actor (or unlock target) is a grown-up with no PIN yet | 409 | `NoPin` |
| `wrong-pin` | 403 | `WrongPin` |
| `not-allowed` — the rule in §2 says no | 403 | `NotAllowed` |
| `not-synced` — a first profile before a sync round of the library followed now (amended 2026-10-05, see `create-first` below) | 409 | `NotSynced` |
| success | 201 (create, with profile JSON) / 204 | `Done` |

Error body: `{ "reason": "<reason>", "retryAfter"?: <seconds> }`.

Order (amended 2026-09-28 after the core plan's questions):

1. `invalid` — `name`, `newPin`, `age`/`kidsAge` malformed. A malformed
   *current* `pin` is **not** `invalid`: it is compared, fails, and counts
   as `wrong-pin` (a PIN-less self set-pin may send `""`).
1a. `name-taken` (amended 2026-10-04) — `create-first`, `create-grown-up`
   and `create-kid` only: the new name collides with any existing profile,
   kid or grown-up, compared as `normalName(cleanName(name))` on both sides
   (`profile-names.json`). Why: sync keys a viewer by its normalised name and
   `kids` only ever turns on, so a kid named after the admin made the admin a
   powerless kid on every device — after which anyone could claim admin and
   the old admin could not be removed. Checked before `not-found`, so a name
   is refused whoever asks; names are already public through
   `GET /api/profiles`. There is no rename, so nothing else can collide.
2. `not-found` — actor or target id names nobody.
3. `not-allowed` (structural) — actor is a kid; for `claim-admin`, an admin
   exists or the target is a kid; for `create-first`, a grown-up exists.
4. `wait` — **only when this call is about to compare a PIN**. A kid's
   unlock and a PIN-less self set-pin never wait, and a PIN-less grown-up
   acting during a wait gets `no-pin`, not `wait` (nothing is compared).
5. `no-pin` — actor (or unlock target) is a grown-up with no PIN yet.
6. `wrong-pin`.
7. `not-allowed` — the rule in §2 says no.

Exceptions:

- **Self `set-pin` on a grown-up with no PIN** skips `no-pin`/`wrong-pin`
  (`pin` is ignored, may be `""`). This is how a pre-upgrade grown-up gets
  its first PIN.
- **`unlock`**: target kid → success without a PIN; grown-up with no PIN →
  `no-pin`; otherwise PIN check.
- **`claim-admin`**: `not-allowed` if an admin already exists or the target
  is a kid; if the target has a PIN it must match (`wrong-pin`), if not, the
  given PIN (format-checked → `invalid`) becomes its PIN. Sets
  `admin_claimed_at = now`.
- **`create-first`** (the bootstrap): allowed only while **no grown-up exists
  locally**. Creates a grown-up with `newPin` as its PIN and
  `admin_claimed_at = now`, no actor, no current PIN. Without it a fresh
  install could never get a first profile. A device that bootstraps before
  its first sync and later learns of an older claim loses admin to it by the
  earliest-claim rule (§7) — no special case.
- **`create-first` waits for a sync round of the library followed now — both
  surfaces (amended 2026-10-05).** After every check above says yes, the answer
  is `not-synced` (HTTP 409; core `NotSynced`) until this player has imported a
  sync round of the channel it follows *now*. Why: sync merges viewers by name
  and the newer PIN wins, so a first profile made blind under a household
  member's name handed its PIN to that member on every device. A new
  household's first round finds nobody and still counts, so it can begin.
  - **The mark** (`state_meta['first_round_imported']`, local, never exported)
    names the library whose round was imported; it is written only after the
    import has committed, so a channel that could not be listed, an import that
    rolled back, or (web) a signed-out list that reads as empty never counts.
  - **Per library:** following another library is joining another household.
    Core: `refresh_library(handle)` — the step every first choice and every
    switch takes — records `state_meta['followed_library']` *before* installing,
    and the mark counts only when it names that library (a device with no
    `followed_library` yet, installed before it existed, trusts any round).
    Web: the channel the connection points at now (`TelegramStateChannel.library()`),
    read before and after the round; a round whose channel changed under it
    marks nothing.
  - **Who waits:** a web player that syncs with nobody (`MEDIAGRAM_SYNC_STATE`
    off, or no database) has no household to hear from and never waits.
  - **Telling the picker:** core `has_synced_once()`; web `GET /api/profiles`
    carries `heard: boolean`. Both pickers show "Waiting for this household's
    profiles…" with Try again in place of the form; a refusal reads "The
    household's profiles have not arrived yet." The web's Try again re-reads
    the list; its next round comes from the server's own timer.

## 4. Wrong-PIN wait

`MAX_WRONG_PINS = 5`, `WAIT_MS = 60_000`. **Counted per profile whose PIN
is compared (amended 2026-10-04)** — the actor's, or the unlock or
claim-admin target's. Every failed comparison of profile X's PIN (any
action) adds one to X's count. X's 5th failure starts a 60 s wait during
which every call that would compare X's PIN answers `wait` with the seconds
left (rounded up), without comparing; other profiles' PINs are compared as
usual. When X's wait ends, X's count is 0. A successful comparison of X's
PIN resets X's count only. The clock is injectable for tests. Pinned by
`pin-wait.json`.

**Where the count lives differs by surface (amended 2026-10-05).** Web: in
memory, one per server process — it restarts rarely, and only at its admin's
hand. Core: in the local `state.db` (`state_meta`, never exported, never
synced), read and written around every comparison — on a device a child
holds, swiping the app away is a restart, and an in-memory count turned
~33 h of guessing into hours. A stored wait further off than `WAIT_MS` (the
clock went back, e.g. a box booting before its time is set) is re-anchored
to `now + WAIT_MS`, never longer.

Why per profile: one count for the whole player was reset by any right
PIN, so four guesses at the admin, then one's own known PIN, repeated,
never waited (a review cracked the admin's PIN in 7392 guesses, 0 waits).

## 5. PIN format and hash

- Valid PIN: exactly `/^[0-9]{4}$/`. Anything else → `invalid`.
- `salt` = 16 random bytes, lowercase hex (32 chars).
- `hash` = lowercase hex SHA-256 of the UTF-8 bytes of `salt + pin`.
- Web: `node:crypto` `createHash`/`randomBytes`, compare with
  `timingSafeEqual`. Core: `sha2::Sha256`, `getrandom` for the salt,
  compare full digests without early exit.
- Fixture `web/test/fixtures/watch-state/pin-hash.json` (exact values):

```json
[
  { "salt": "00112233445566778899aabbccddeeff", "pin": "1234",
    "hash": "f377124b2c2ffeb096001d94cb0e3df86fbe20ad9981335bc624b960d4d80924" },
  { "salt": "ffeeddccbbaa99887766554433221100", "pin": "0000",
    "hash": "48618b45393cde1c8841b73b2ca684f9b39a2efcbf35d4bf602d7f908b5137f8" }
]
```

## 6. Schema (web v10 → v11; core next version), identical statements

```sql
ALTER TABLE profiles ADD COLUMN kids_age INTEGER;
ALTER TABLE profiles ADD COLUMN kids_age_updated_at INTEGER NOT NULL DEFAULT 0;
ALTER TABLE profiles ADD COLUMN parent_id TEXT;
ALTER TABLE profiles ADD COLUMN admin_claimed_at INTEGER;
ALTER TABLE profiles ADD COLUMN pin_hash TEXT;
ALTER TABLE profiles ADD COLUMN pin_salt TEXT;
ALTER TABLE profiles ADD COLUMN pin_updated_at INTEGER NOT NULL DEFAULT 0;
UPDATE profiles SET kids_age = 12 WHERE kids = 1;
ALTER TABLE kids ADD COLUMN age INTEGER;
```

`kids.age`: `NULL` = from 12, `6` = from 6.

Amended 2026-10-05 (web v12 → v13, core v8 → v9), for proven PINs (§7):

```sql
ALTER TABLE profiles ADD COLUMN pin_proven INTEGER NOT NULL DEFAULT 0;
```

Every PIN stored before it is a first PIN.

## 7. Wire (sync record) — new optional keys, no `SYNC_FORMAT` bump

On `ProfileState`:

```ts
admin?: { claimedAt: number };                       // > 0
kids?: true;                                         // unchanged, still written for every kid
kidsAge?: { age: 6 | 12; updatedAt: number };        // >= 0; only on kids
parent?: string;                                     // parent's profile name as stored; non-empty
pin?: { hash: string; salt: string; updatedAt: number; proven?: true }; // 64 hex / 32 hex / > 0; only on grown-ups
```

On `ListRow` (Kids marks only): `age?: 6`. Only the literal `6` parses;
anything else is absent (= from 12). Watchlist rows never carry it. A
tombstone carrying `age` is parsed without it — the age is stripped at
parse, not later (pinned by `profile-roles-record-parse.json`).

Role stamps use the shipped bound (2026-10-04): `admin.claimedAt` and
`pin.updatedAt` must pass `isStamp` (`> 0`, `<= 2^53 − 1`); `kidsAge.updatedAt`
is `0` or passes `isStamp`. Past the bound the key is dropped, as any synced
row is.

Parsing drops a malformed sub-key on its own, never the profile.

`pin.proven` (amended 2026-10-05): only the literal `true` parses; anything
else, or nothing, is a **first PIN** and keeps the PIN. Every PIN written
before the key existed is a first PIN. A PIN is **proven** when whoever set
it proved the PIN before it, or when the admin reset it; it is **first** when
set where its grown-up had none: `create-first`, `create-grown-up`, a
`claim-admin` that takes its first PIN, and a self `set-pin` with nothing to
prove. Pinned by `profile-roles-record-parse.json`.

**Merge** (per viewer, identity = `normalName`):

- `kidsAge`: newest `updatedAt` wins, ties by device id (`keep`). A viewer
  that is `kids` with no `kidsAge` anywhere merges to `{ age: 12, updatedAt: 0 }`.
  Output only on a kid.
- `pin` (amended 2026-10-05, user's rule: a first PIN never replaces a PIN
  set earlier on another device): a proven PIN beats any first PIN; between
  proven PINs the newest `updatedAt` wins; between first PINs the **oldest**
  wins; ties by device id. Web `keepPin`/`pinBeats` (`tie-break.ts`), core
  `keep_pin` (`merge/tie_break.rs`). Why: a kid's tablet that last synced
  before a parent set a PIN offers the kid "choose a PIN" on the parent's
  tile; under "newest wins" that PIN became the parent's on every device
  (with the admin's, Manage as admin). Output only on a grown-up.
- `parent`: first seen, then the device-id tie-break `displayName` uses.
  Output as `normalName(parent)`, **only on a kid**.
- `admin`: across **all grown-up** viewers (a claim on a kid viewer is
  ignored), the smallest `claimedAt` wins, ties by smaller normalised name. Only that viewer's merged profile carries
  `admin`; every other carries none.
- Kids marks: `age` rides on the row `keep` chose. **Exact tie (amended
  2026-10-04):** at an equal `updatedAt` a live mark with an age beats one
  without *before* the device id is asked — rank `(updatedAt, live && age == 6 ? 1 : 0, device)`.
  So a from-6 mark also beats a removal stamped the same moment, outright.
  Why: a build from before ages drops `age` on import and writes the mark
  back at the same time; under a plain device-id tie that echo stripped the
  age everywhere whenever the older device's id sorted higher (verified
  against 0.103.0). Web: `kidsMarkRank` (`roles-merge.ts`) through `keep`'s
  `rank`. **Port note:** the rule is sound only because an age change is
  never an equal-time pair — the core's own `set_kids` must move the clock on
  every age change, 6 → 12 included, not only on a re-mark after a removal.

**Import** (corrective, like every other row):

- `kids_age`: apply when merged `updatedAt` > local, or equal with a
  different value.
- `pin_*` (amended 2026-10-05): apply over no PIN, or when the merged PIN
  ranks above the local one in the merge's order (`pinBeats`), or ranks
  equal with a different hash/salt. A merged first PIN older than a local
  first one is taken — that is the stale tablet getting the household's back.
- `admin`: when the merge names an admin, set `admin_claimed_at` on that
  local profile and clear it on every other. When it names none, change
  nothing.
- `parent_id`: set only while local `parent_id IS NULL`, to the local
  profile whose `normalName` matches; never overwritten.
- Kids mark `age`: written with the row.

**Local writes stamp `max(now, stored updatedAt + 1)`** for `kids_age`,
`pin` and a Kids mark's age/removal — the same guard `setWatched`'s un-mark
already uses (`MAX(?3, finished_at + 1)`), so a device whose clock runs
behind cannot write a change that loses to the value it replaced. Clamped as
shipped (2026-10-04): `MAX(now, MIN(stored, 2^53 − 2) + 1)`, never an
unclamped `stored + 1`.

**Export**: `admin` when `admin_claimed_at` set; `kids` + `kidsAge`
(`kids_age ?? 12`, `kids_age_updated_at`) on kids; `parent` = the stored
name of `parent_id` when it resolves; `pin` when `pin_hash` set, with
`proven: true` when `pin_proven = 1`.

## 8. Web HTTP

Profile JSON (every route that returns one):
`{ id, name, createdAt, kids: boolean, kidsAge: 6 | 12 | null, parentId: string | null, admin: boolean, hasPin: boolean }`
— never `pin_hash`/`pin_salt`.

**A kid is never `admin` and never `hasPin` (amended 2026-10-04)**, whatever
its columns hold: a grown-up with a claim or a PIN that another device later
calls a kid keeps both columns after the sticky `kids` upgrade, and a kid
opens without a PIN — read as admin, it could manage everyone. Enforced in
`toProfile` (`admin: !kids && admin_claimed_at IS NOT NULL`,
`hasPin: !kids && pin_hash IS NOT NULL`), in the merge by `earliest()`
skipping kids (§7 `admin`), and in the rule (§2). The core's `Profile` (§9)
applies the same rule.
Export still writes `admin`/`pin` whenever the columns are set (§7); the
merge drops them on a kid.

| Method + path | Body |
|---|---|
| `GET /api/profiles` | → `{ profiles: Profile[], remembers }` (shape as today, new fields) |
| `POST /api/profiles` | `{ actorId, pin, name, kids: boolean, kidsAge?: 6\|12, newPin?: string }` — kid needs `kidsAge`, grown-up needs `newPin` → 201 Profile. **No `actorId`** = `create-first`: `{ name, newPin }` → 201 Profile (admin) or 403 `not-allowed` |
| `DELETE /api/profiles/:id` | `{ actorId, pin }` → 204 |
| `POST /api/profiles/:id/unlock` | `{ pin }` → 204 |
| `POST /api/profiles/:id/claim-admin` | `{ pin }` → 204 |
| `PUT /api/profiles/:id/pin` | `{ actorId, pin, newPin }` → 204 |
| `PUT /api/profiles/:id/kids-age` | `{ actorId, pin, age }` → 204 |
| `GET /api/kids` | → `{ kids: string[], fromSix: string[] }` (`kids` = every live mark, `fromSix` ⊆ it) |
| `PUT /api/kids/:setId` | `{ age?: 6\|12 }`, absent = 12 → 204; a live mark's age is updated in place (new `marked_at`) |
| `DELETE /api/kids/:setId` | unchanged |
| `PATCH /api/profiles/:id` | **removed** (rename) |

## 9. Core uniffi API (`crates/mediagram-core`)

```rust
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Profile {
    pub id: String,
    pub name: String,
    #[uniffi(default = false)] pub kids: bool,
    #[uniffi(default = None)]  pub kids_age: Option<u8>,
    #[uniffi(default = None)]  pub parent_id: Option<String>,
    #[uniffi(default = false)] pub admin: bool,
    #[uniffi(default = false)] pub has_pin: bool,
}

#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum ProfileOutcome { Done, Invalid, NameTaken, NotFound, Wait { seconds: u32 }, NoPin, WrongPin, NotAllowed, NotSynced }

// on Core, async like the rest of api/state.rs:
create_first_admin(name, new_pin) -> ProfileOutcome     // create-first, §3; NotSynced before a round
has_synced_once() -> bool                                // a sync round imported here (2026-10-05)
create_grown_up(actor_id, pin, name, new_pin) -> ProfileOutcome
create_kid(actor_id, pin, name, kids_age: u8) -> ProfileOutcome
delete_profile(actor_id, pin, id) -> ProfileOutcome      // replaces delete_profile(id) -> bool
unlock_profile(id, pin) -> ProfileOutcome
claim_admin(id, pin) -> ProfileOutcome
set_pin(actor_id, pin, id, new_pin) -> ProfileOutcome
set_kids_age(actor_id, pin, id, kids_age: u8) -> ProfileOutcome
set_kids(set_id, age: Option<u8>)                        // replaces marked: bool; None = remove
// create_profile(name, kids) is removed from the API; profiles::create stays for sync.

StateSnapshot { …, #[uniffi(default = [])] pub kids_from_six: Vec<String> }  // kids stays every live mark
```

A storage failure inside any of these is logged and answered `Invalid` —
the file's "nothing here throws" rule.

## 10. Shared fixtures (`web/test/fixtures/watch-state/`, created in phase 01/02)

| File | Shape | Run by |
|---|---|---|
| `pin-hash.json` | §5 | web phase 02, core phase 05 |
| `profile-rules.json` | `[{ name, profiles: RoleView[], actorId, action, targetId, expect: boolean }]` | web phase 02, core phase 05 |
| `profile-roles-merge.json` | `[{ name, records: SyncRecord[], expect: { profiles: [{ name, displayName, admin?, kids?, kidsAge?, parent?, pin? }], kids: ListRow[] } }]`, compared on those fields only | web phase 01, core phase 04 |
| `profile-names.json` (2026-10-04) | `[{ name, existing: string[], candidate: string, expect: boolean }]` — whether `candidate` is taken (§3 item 1a) | web phase 02, core phase 05 |
| `pin-wait.json` (2026-10-04) | `[{ name, steps: [{ at: ms, failed?: id, succeeded?: id, secondsLeft?: id, expect?: seconds }] }]` — one fresh wait per case, its clock at a fixed start + `at` | web phase 02, core phase 05 |
| `profile-roles-record-parse.json` | `[{ name, input: string, expect: SyncRecord \| null }]`, the shape of `record-parse.json`; run through the real `parseRecord`, compared whole | web phase 01, core phase 04 |

Amended 2026-10-04:
- `profile-roles-merge.json`'s expected profiles carry `displayName`, as
  `stats-merge.json`'s do, so the core can deserialize `expect` as its
  `MergedState` (whose `display_name` has no serde default) and compare
  through a `roles_only` projection like `stats_only`. It pins rule 1 (§7
  Kids-mark tie, a same-stamp removal included) and the merge half of rule 2
  (a claim on a kid is ignored).
- `profile-roles-record-parse.json` pins the parse: role keys read back,
  each malformed key dropped alone, the role-stamp bounds (§7: `0` kept for
  `kidsAge` only, `2^53 − 1` kept, `2^53` dropped), numeric-string times
  coerced like every older row's, and a Kids tombstone's `age` stripped.

## 11. Client filter — both surfaces

`forKidsProfile(sets, marks, limit)`; `marks: Map<setId, 6 | 12>`;
a title is visible iff (rated and `age <= limit`) or (unrated and
`marks.get(setId) <= limit`). `kidsVerdict(set, limit)` →
`"safe" | "unsafe" | "unrated"`. `KIDS_AGE_LIMIT` is deleted.
Empty shelf text: `Nothing rated FSK ${limit} or under yet.`

## 12. Picker start states — both surfaces

| Local state | Picker shows |
|---|---|
| no grown-up at all (fresh install, or only kids) | "Create the first profile — it runs this household": name + PIN twice → `create-first` |
| grown-ups, none is admin | "Who runs this household?" above the tiles: choose a grown-up, enter or set its PIN → `claim-admin` |
| an admin exists | tiles + "Manage profiles" |
