# Phase 01 — The household rule, its words, and the shared fixtures (web reference)

**Spec:** user decisions 1–4; controller calls 1–8, 11–15. **Status:** pending. **Depends on:** nothing on `feat/households`.
**Pattern to copy:** `web/public/lib/daily-limit.js` + `daily-limit.d.ts` + `web/test/fixtures/watch-state/daily-limit.json` (a rule and its words written once as browser JS, imported by server TS, held to a fixture that Kotlin also runs); `web/test/shared-profile-fixtures.test.ts` (its `load<T>()`).

Names used by every case below. Profiles: `andre` (the owner), `test`, `mia` (kid of `test`), `oma` (admin of `b`), `tvtest`, `tvkids` (kid of `tvtest`), `lostb` (kid whose parent is `gone`). Households: `home`, `b`, `c` (empty), `x` (unknown).

### Task 1.1: The household rule and its words, held to `household-rules.json`

**Files:**
- Create: `web/public/lib/household-rules.js`, `household-rules.d.ts`
- Create: `web/public/lib/household-words.js`, `household-words.d.ts`
- Create: `web/test/fixtures/watch-state/household-rules.json`
- Create: `web/test/household-rules.test.ts`

**Interfaces — Produces** (`household-rules.js`):
- `HOME = "home"`.
- `householdView(profiles, households)` where `profiles: {id, kids, owner?, parentId, household?}[]` (`household` = stored id, absent = `home`) and `households: {id, adminId: string|null, removed?: boolean}[]`. Returns `{ profiles: Record<id, {household, admin}>, admins: Record<liveHouseholdId, string|null> }`:
  - live = `home` always, plus every listed household not `removed`;
  - stored(p) = `p.household ?? HOME` when live, else `HOME`;
  - household(p) = a kid whose `parentId` names a grown-up in `profiles` is in household(parent) = stored(parent); anyone else stored(p);
  - owner = the profile with `owner === true` and `kids === false` (none if only a kid says so);
  - adminOf(h) = the owner when household(owner) is `h`; else the household's `adminId` when it names a grown-up whose household is `h`; else `null`;
  - `admin` = `!kids && adminOf(household) === id`.
- `householdAllowed(profiles, live, actorId, action, householdId, targetId)`; `profiles: {id, kids, admin, owner?, parentId, household}[]` (already resolved), `live: string[]`. `false` when the actor is missing or a kid. Then:
  - `create-household`: actor is owner, and `targetId === ""` or the target is a grown-up with `admin === false`;
  - `rename-household`: owner, `householdId` live;
  - `set-household-admin`: owner, live, the owner is **not** in `householdId`, target a grown-up of `householdId`;
  - `move-to-household`: owner, live, target a grown-up not already in `householdId`, and not `admin` unless no other grown-up is in its household (the last grown-up may leave, emptying it for deletion; otherwise change the admin first);
  - `delete-household`: owner, live, `householdId !== HOME`, no profile in it;
  - `join-household`: live, and actor is owner, or actor is `admin` of `householdId`;
  - `claim-household`: live, actor is in `householdId`, no grown-up of `householdId` is `admin`;
  - anything else: `false`.
- `deviceHousehold(stored, chosen, live, heard)` → `string|null`, in order: `stored` if live; `chosen` (the chosen profile's household) if live; `null` while `!heard`; the only live household when there is one; else `null` (ask "Join a household").

**Interfaces — Produces** (`household-words.js`):
- `HOUSEHOLD_WORDS = { households: "Households", owner: "Owner", ownerNote: "Households are the owner’s to set up.", ownersPin: "The owner’s PIN", thisDevice: "This device", switchHousehold: "Switch household", joinAHousehold: "Join a household", joinNote: "This device shows one household’s profiles. Choose yours, then its admin’s PIN.", createAHousehold: "Create a household", householdName: "Household name", admin: "Admin", aNewGrownUp: "A new grown-up", rename: "Rename", changeAdmin: "Change admin", moveHere: "Move a grown-up here", useHere: "Use on this device", delete: "Delete", householdNameTaken: "A household with that name already exists.", profileNameTaken: "That name is already used on this install." }` (typographic ’ as in `pin-prompt.js`).
- `householdLine(adminName: string|null, count: number)` → `"Admin andre · 4 profiles"`, `"Admin Oma · 1 profile"`, `"No admin · 0 profiles"`.
- `joinPinTitle(adminName: string|null)` → `"Oma’s PIN, or the owner’s"`; `null` → `"The owner’s PIN"`.

- [ ] **Step 1: Write `household-rules.json`** as `{ words, lines[], view[], allowed[], device[] }`, every case with a `name`.
  - `words`: the object above. `lines`: `{fn, args, expect}` for the five examples above.
  - `view` (input `profiles`, `households`; `expect: {profiles, admins}`), base andre(owner), test, tvtest grown-ups and tvkids (kid of tvtest), all `household: "home"`:
    1. "from before households: everyone is in home, and the owner runs it" — households `[{id:home, adminId:null}]` → all `home`, only andre `admin`; admins `{home: andre}`.
    2. "the owner runs the household they are in, whatever its record says" — home `adminId: test` → andre admin, test not.
    3. "a household the owner is not in is run by its recorded admin" — tvtest in `b`, `b.adminId: tvtest` → tvtest `{b, admin}`, tvkids `{b}` although its own says home.
    4. "a kid follows its parent, whatever its own household says" — tvkids `household: b`, tvtest in home → tvkids `home`.
    5. "a kid whose parent is gone keeps its own household" — `lostb` (parent `gone`, `b` live) → `b`.
    6. "a household that is gone, or unknown, reads as home" — test in `c` (`removed: true`), tvtest in `x` → both `home`.
    7. "a recorded admin who is not a grown-up of that household runs nothing" — `b.adminId: test` (test in home) → admins `b: null`; and `c.adminId` naming a kid of `c` → `c: null`.
    8. "a kid marked as owner is no owner" — every grown-up `owner: false`, a kid `owner: true` → admins `{home: null}`, nobody `admin`.
    9. "no owner and nothing recorded: home has no admin" — nobody `owner` → admins `{home: null}`.
  - `allowed` (input `profiles` resolved, `live`, `actorId`, `action`, `householdId`, `targetId`, `expect`). Base H: andre `{admin, owner, home}`, test `{home}`, mia `{kid of test, home}`, oma `{admin, b}`, tvtest `{b}`, tvkids `{kid of tvtest, b}`; live `["home","b","c"]`. Cases, in order:
    1. owner creates with a new grown-up (andre, create-household, "", "") → true
    2. owner creates with a grown-up who runs nothing (target test) → true
    3. …not with a household's admin (oma) → false
    4. …not with a kid (mia) → false
    5. a household's admin who is not the owner may not create one (oma) → false
    6. a kid may not create one (mia) → false
    7. the owner renames one (b) → true
    8. nobody renames a household that is not there (x) → false
    9. a household's admin may not rename even its own (oma, b) → false
    10. owner makes a grown-up of a household its admin (b, tvtest) → true
    11. …not one of another household (b, test) → false
    12. …not a kid (b, tvkids) → false
    13. …not in the household the owner is in (home, test) → false
    14. owner moves a grown-up who runs nothing (b, test) → true
    15. …not a household's admin while another grown-up is there (home, oma) → false
    16. …not a kid on its own (b, mia) → false
    17. …not into the household it is in (home, test) → false
    18. …not into a household that is not there (x, test) → false
    18a. …but a household's admin who is its last grown-up may leave (H without tvtest and tvkids; home, oma) → true
    19. owner deletes an empty household (c) → true
    20. …not one with anybody in it (b) → false
    21. …never home, even empty (profiles: andre owner+admin in `b`, live `["home","b"]`; delete home) → false
    22. a household's admin may join a device to it (oma, b) → true
    23. …not to another household (oma, home) → false
    24. the owner may join a device to any household (andre, b) → true
    25. a grown-up who runs nothing may not (tvtest, b) → false
    26. nor a kid (tvkids, b) → false
    27. nobody joins a household that is not there (andre, x) → false
    28. a grown-up may claim a household nobody runs (H with oma `admin: false`; tvtest, b) → true
    29. …not one somebody runs (H; tvtest, b) → false
    30. …not another household (H with oma `admin: false`; test, b) → false
    31. a kid may not claim (H with oma `admin: false`; tvkids, b) → false
  - `device` (`stored`, `chosen`, `live`, `heard`, `expect`):
    1. a device that joined keeps its household (`b`, null, [home,b], true) → `b`
    2. one household: a device that never joined joins it by itself (null, null, [home], true) → `home`
    3. …but not before it has heard the household (null, null, [home], false) → null
    4. two households: a device that never chose anyone asks (null, null, [home,b], true) → null
    5. an existing device joins its chosen profile's household (null, `b`, [home,b], true) → `b`
    6. …even before a round has landed (null, `home`, [home,b], false) → `home`
    7. a device whose household was deleted follows its chosen profile (`c`, `b`, [home,b], true) → `b`
    8. …or, with nobody chosen and one household left, joins it (`c`, null, [home], true) → `home`
    9. a stored household that is unknown reads as none (`x`, null, [home,b], true) → null
- [ ] **Step 2:** Write `household-rules.test.ts`: describes `"household words fixtures"`, `"household view fixtures"`, `"household allowed fixtures"`, `"device household fixtures"`, one `test(case.name)` each. **Run** `cd web && bun test test/household-rules.test.ts` → FAIL (modules missing).
- [ ] **Step 3: Implement** both modules and their `.d.ts` (every export typed; header comments say why: one rule for server and browser, as `daily-limit.js`). Each file under 200 lines.
- [ ] **Step 4: Run** the test → PASS; `bunx tsc --noEmit -p .`, `bunx eslint public/lib/household-*.js`.
- [ ] **Step 5: Commit** `feat: a household rule and its words, held to shared fixtures`.

### Task 1.2: Who manages whom stays inside a household

**Files:**
- Modify: `web/src/state/profiles-rules.ts` (`RoleView.household?: string`; `ownerOf` takes the admin of the kid's household; `allowed` answers `false` for any action with a target whose household differs from the actor's — absent is `home`)
- Modify: `web/test/fixtures/watch-state/profile-rules.json` (append cases; existing ones carry no `household` and must stay green)
- Modify: `web/test/profile-api.test.ts` (its `offered()` runner hands `manageable` only the profiles of the actor's household, absent = `home`: the panel only ever sees one household)
- Modify: `crates/mediagram-core/src/state/profiles/rules.rs` (`RoleView.household: Option<String>`, `#[serde(default)]`; same guard and `owner_of`)
- Modify: `android/core/model/src/main/kotlin/WatchSnapshot.kt` (`Profile.household: String = HOME_HOUSEHOLD`, with `const val HOME_HOUSEHOLD = "home"` beside it), `ProfileRoles.kt` (`ownerOf` and `allowed` the same)
- Modify: `android/core/model/src/test/kotlin/ProfileRolesFixtureTest.kt`, `android/feature/catalog/src/test/kotlin/profile/ManageOffersFixtureTest.kt` (`toProfile` reads `household`; the offers runner filters to the actor's household like the web's)

**Interfaces — Produces:** unchanged signatures; `allowed(profiles, actorId, action, targetId)` refuses across households.

- [ ] **Step 1: Append to `profile-rules.json`.** Base: andre `{admin, home}`, test `{home}`, oma `{admin, b}`, tvtest `{b}`, tvkids `{kid of tvtest, b}`, lostb `{kid, parent gone, b}`:
  1. each household's admin may create a grown-up (oma, create-grown-up) → true
  2. a household's admin may not remove a grown-up of another (andre, remove, tvtest) → false
  3. …but may remove one of its own (oma, remove, tvtest) → true
  4. nobody sets the PIN of a grown-up in another household (andre, set-pin, tvtest) → false
  5. a household's admin may reset the PIN of its own grown-up (oma, set-pin, tvtest) → true
  6. a kid whose parent is gone belongs to its household's admin (oma, remove, lostb) → true
  7. …not to another household's admin (andre, remove, lostb) → false
  8. a grown-up may not set a kid's limit across households (test, set-kids-age, tvkids) → false
  9. no one hides sections across households (andre, set-access, tvtest) → false
  10. a household's admin is never removed, by another household's admin either (andre, remove, oma) → false
- [ ] **Step 2: Run** `cd web && bun test test/shared-profile-fixtures.test.ts test/profile-api.test.ts` → FAIL on 2, 4, 7, 8, 9.
- [ ] **Step 3: Implement** in TS, Rust and Kotlin.
- [ ] **Step 4: Run** the web tests, `cargo test -p mediagram-core --test shared_watch_state_fixtures`, `cd android && ./gradlew :core:model:test :feature:catalog:testDebugUnitTest --tests '*ManageOffersFixtureTest*'` → PASS.
- [ ] **Step 5: Commit** `feat: who manages whom stays inside a household`.

### Task 1.3: The sync cases for households

**Files:**
- Create: `web/test/fixtures/watch-state/households-sync.json` as `{ parse[], merge[] }`. No runner yet: the web's lands in Task 2.2, the core's in Task 3.3, each beside the code that passes it.

**The wire** (decided here; `docs/system-architecture.md` gets it in phase 07):
- Document top level: `households?: [{ id, name?: {text, updatedAt}, admin?: {name, updatedAt}, removed?: true }]`.
  - `id`: `^[A-Za-z0-9-]{1,64}$` (`home`, a UUID, or the core's ULID).
  - `name.text`: non-empty after trimming, cut to 120 characters.
  - `admin.name`: a viewer's name, as `parent` is.
  - `updatedAt`: a stamp, 0 < t ≤ 2^53−1. A field dated 0 is never written.
  - `removed`: only the literal `true`, never on `home`. A row left saying nothing is dropped.
- Per profile: `household?: { id, updatedAt }`, same `id` and stamp rules.
- `parse` cases (`input` = document text; `expect` = `{ households, profiles: [{name, household}] }`, profiles listed only when they carry a `household`):
  1. households and a profile's household read back as written (home renamed "Borck"; `01J9ZB` named "Oma" with admin "Oma"; profile "Oma" in `01J9ZB`)
  2. a document from before households says nothing about them → `{households: [], profiles: []}`
  3. a row without a usable id is dropped (missing, `""`, `"has space"`, 65 characters)
  4. a name dated 0, dated past 2^53−1, or empty is dropped, and the row with it when it says nothing else
  5. an admin with an empty name or a bad date is dropped alone
  6. removed counts only as the literal true (`"yes"` dropped)
  7. home is never removed (`{id:"home", removed:true}` alone → no row)
  8. a name longer than 120 characters is cut to 120
  9. a profile's household with a bad id or a stamp of 0 drops the key alone
  10. unknown keys inside a household are dropped
- `merge` cases (`records`; `expect` = `{ households (sorted by id), profiles: [{name (normalised), household}] }`, both orders of `records` give the same answer):
  1. a rename on two devices: the newer wins
  2. a rename at the same moment: the larger device id wins
  3. a rename on one device and a new admin on another both stand
  4. a removal on any device stands, however old
  5. a household one device has not heard of comes through whole
  6. an admin is merged under the name sync knows a viewer by ("Oma" → "oma")
  7. a profile moved on two devices: the newer move wins
  8. a profile moved at the same moment: the larger device id wins
  9. a device that says nothing about households changes nothing

- [ ] **Step 1:** Write the file; check it parses: `cd web && bun -e 'JSON.parse(await Bun.file("test/fixtures/watch-state/households-sync.json").text())'`.
- [ ] **Step 2: Commit** `test: shared cases for households on the sync record`.
