# Profile roles: an admin, parents, and kids with their own age limit

Today every profile is equal. Anyone can pick any of them, anyone can remove
any of them, and a kids profile is one flag with one limit — FSK 12 — fixed
in code on both surfaces (`web/public/lib/age-rating.js`,
`android/core/model/src/main/kotlin/AgeRating.kt`).

The household wants three kinds of viewer instead:

- **The admin** — andre. Cannot be removed. The only one who adds or
  removes grown-ups, or resets a grown-up's PIN.
- **Grown-ups** — parents. Each owns the kids profiles they made, and
  decides for each of them whether it sees FSK 6 or FSK 12.
- **Kids** — see only what their own limit allows, and manage nothing.

A grown-up profile is behind a 4-digit PIN; a kids profile is not. Both
surfaces — the web player and the Android app — get the same rules from the
same synced data.

## 1. What this is not

The player is not becoming a login, and this page should not be read as if
it were. The PIN stops a child tapping into a grown-up's profile. It does not
stop someone technical:

- On the web, the catalog filter for a kids profile runs in the browser
  (`forKidsProfile`), and the state API has no sessions. A kid with the
  browser's developer tools, or `curl`, gets past it.
- On Android, the same holds for anyone with `adb`.

Closing that would mean server-side filtering and a session on every state
call — a different product, and a reversal of the "not a login" note the
picker shows today. It is out of scope; the note on the picker changes to say
what the PIN does and does not do, and the code that checks a PIN says the
same where the next reader will find it.

What *is* enforced where it is decided rather than only where it is drawn:
every management action (create, remove, change a limit, change a PIN, claim
admin) is checked by the web server's store and by the Android core. A
screen that forgets to hide a button still cannot do the thing.

## 2. Who can do what

| Action | Admin | Grown-up | Kid |
|---|---|---|---|
| Enter the profile from the picker | PIN | PIN | free |
| Create a grown-up (and set their first PIN) | yes | – | – |
| Remove a grown-up (their kids go with them) | yes, never the admin | – | – |
| Reset another grown-up's PIN | yes | – | – |
| Change own PIN | yes | yes | – |
| Create a kid under themselves (name + FSK 6 or 12) | yes | yes | – |
| Change the limit of, or remove, their own kids | yes | yes | – |
| Mark an unrated title "from 6" / "from 12" | yes | yes | – |

- **A kid belongs to exactly one grown-up**, the one who created it, and only
  that grown-up manages it. The admin is a grown-up too and can have kids of
  its own, but does not manage another parent's kids.
- **A kid with no parent belongs to the admin.** That covers the kids
  profiles that exist before this change (`TV kids` today) and a kid whose
  parent was removed on one device and whose record came back from another.
  No migration has to guess a parent.
- **The admin cannot be removed** — by anyone, itself included — and there is
  no way to hand the role on. A household that needs that asks for it then.
- **Choosing a remembered profile at start-up asks nothing.** The picker is
  where the PIN is asked. A device left on a grown-up's profile stays there,
  exactly as a television left on a profile does today; that is the
  grown-up's to avoid, not the app's to guess at.
- **Leaving a grown-up for a kid is free.** Only the way in is locked.

### Managing

The picker's "New profile" tile and the `window.prompt` "Rename or remove…"
flow go. In their place, one **Manage profiles** entry on the picker:

1. Choose who you are (grown-ups only).
2. Enter your PIN.
3. See what your role allows: the admin sees every grown-up and its own kids;
   a grown-up sees its own kids and its own PIN.

The PIN entered in step 2 is held in memory for as long as that panel is
open and sent with each action, then dropped. Entering a profile from the
picker never opens the panel by itself — a device left on andre does not
hand a child the controls.

### The first admin

No name is written into the code. A device that knows of no admin — none of
its own, none in any synced document — asks once, above the tiles:

> Who runs this household?

Only grown-ups are offered. The one chosen sets a PIN (or enters the one it
already has) and becomes the admin. That claim syncs to every other device.

If two devices each claim before they have heard of one another, the
earliest claim wins (§3). On this household that means andre claims once, on
the web player, and the other devices learn it on their next sync.

### Grown-ups from before this change

`andre`, `test` and `TV test` have no PIN. Entering one of them — from the
picker, or as step 2 of Manage profiles — first asks for a new PIN, twice,
and stores it. Until that happens the profile is as open as it is today.

### Wrong PINs

Five wrong PINs in a row and the player waits 60 seconds before it will
check another, whichever profile they were for. The count lives in memory —
in the web server, and in the Android core — and resets on a right PIN or a
restart. Four digits are ten thousand guesses; at five a minute, that is more
than a day of a child pressing buttons.

On the web the count is per server, not per browser: a wrong guess on the
laptop also makes the television wait. That is the simpler rule, and a
minute is short.

## 3. Data and sync

### On each device

One new schema version on each store — `web/src/state/schema.ts` (v10 → v11)
and `crates/mediagram-core/src/state/schema.rs` — adding:

| Where | Column | Meaning |
|---|---|---|
| `profiles` | `kids` (exists) | 1 = kid, 0 = grown-up. Unchanged. |
| `profiles` | `kids_age INTEGER` | 6 or 12 on a kid, `NULL` on a grown-up. Existing kids become 12. |
| `profiles` | `kids_age_updated_at INTEGER NOT NULL DEFAULT 0` | When the limit last changed. |
| `profiles` | `parent_id TEXT` | The grown-up who owns this kid; `NULL` = the admin's (§2). Not a foreign key: a parent removed here must leave its kid-from-another-device readable. |
| `profiles` | `admin_claimed_at INTEGER` | Set only on the admin. |
| `profiles` | `pin_hash TEXT`, `pin_salt TEXT` | Only on a grown-up. `NULL` = no PIN yet. |
| `profiles` | `pin_updated_at INTEGER NOT NULL DEFAULT 0` | When the PIN last changed. |
| `kids` | `age INTEGER` | A title's hand mark: `NULL` = from 12, `6` = from 6. Every existing mark becomes "from 12". |

### On the wire

`ProfileState` in `web/src/state/sync-record.ts` (and its port,
`crates/mediagram-core/src/state/record.rs`) gains optional keys — new keys,
not a `SYNC_FORMAT` bump, for the reason that file already gives: a reader
that predates them drops a key it does not know and keeps merging the rest.

```ts
admin?: { claimedAt: number };
kids?: true;                                   // still written — see below
kidsAge?: { age: 6 | 12; updatedAt: number };
parent?: string;                               // the parent's normalised name
pin?: { hash: string; salt: string; updatedAt: number };
```

and `ListRow`, for a Kids mark only, gains `age?: 6`.

How each merges in `merge.ts` / `merge.rs`:

- **`kidsAge`, `pin`: the newest `updatedAt` wins**, ties broken by device id
  like every other row. Not sticky the way `kids` is: a parent lowers a
  limit as often as they raise it, and an admin resets a PIN.
- **`kids: true` is still written on every kid.** A device on an older build
  then keeps filtering that kid at FSK 12 rather than reading it as a
  grown-up. A document with `kids: true` and no `kidsAge` means 12, at
  `updatedAt` 0 — older than any real change.
- **`parent` is set once**, when the kid is made, and never changes. It is a
  name because the name is what sync identifies a viewer by (`normalName`).
  Two documents disagreeing is a bug, not a case; the device-id tie-break
  `displayName` already uses keeps the merge order-independent anyway.
- **`admin`: the earliest `claimedAt` in the household wins**, ties broken by
  normalised name. The profile that loses is a plain grown-up again, and its
  local `admin_claimed_at` is cleared on import. So there is never more than
  one admin, whatever order devices wake up in.
- **A Kids mark's `age`** rides on the row it is on. Changing a mark from 12
  to 6 is a new row with a later `updatedAt`; removing it is the tombstone it
  already is. An older reader sees any mark as a plain Kids mark — the same
  as "from 12".

### The PIN itself

Exactly four ASCII digits; anything else is refused where it arrives, before
it reaches the store. Stored as

```
salt = 16 random bytes, lowercase hex (32 chars)
hash = lowercase hex of SHA-256(UTF-8(salt + pin))
```

— `node:crypto` on the web, the `sha2` crate the core already depends on.
Both sides produce the same string for the same salt and PIN, which is what
lets a PIN set on the television open the profile on the laptop. The web
compares with `timingSafeEqual`; the core compares the full digests without
an early exit.

The hash leaves the store only in the sync document, which lives in the
household's own Telegram channel. `GET /api/profiles` and the core's
`Profile` say `hasPin` and nothing more.

A salted SHA-256 of four digits is not a password hash in any strong sense —
anyone holding the document can try all ten thousand. The document is only
readable by the Telegram account that owns the library, which is andre; the
people the PIN is for cannot read it. That is the threat model, and a slow
hash would buy nothing against it.

## 4. What a kid sees

One rule, for a kid with limit `N` (6 or 12):

- **Rated FSK ≤ N** — shown. Nobody has to mark it.
- **Rated above N** — hidden. A hand mark cannot bring it back.
- **Unrated** — shown only when marked "from 6", or "from 12" when `N` is 12.

`forKidsProfile(sets, marks, limit)` on both surfaces, where `marks` maps a
set id to the age it was marked from. `KIDS_AGE_LIMIT` goes. The empty shelf
names the kid's own limit: "Nothing rated FSK 6 or under yet."

A title's **Kids** control, for a grown-up, on an unrated title, becomes a
choice of **Not for kids / From 6 / From 12** — a native `<select>` on the
web. On a rated title it keeps showing what the rating decided, locked, as it
does now. On a kids profile it stays hidden, as it already is on the web and
must be on Android.

## 5. The web player

**Server, `web/src/state/`**

- `profiles.ts` (new) — the profile rules, moved out of `store.ts`, which is
  near 800 lines already: list, create a grown-up, create a kid, remove,
  claim admin, unlock, set PIN, set a kid's limit, the wrong-PIN counter.
  Each management method takes `{ actorId, pin }` and returns a reason when it
  refuses — `wrong-pin`, `wait` (with seconds), `not-allowed`, `invalid` — so
  the route can answer precisely.
- `schema.ts` — v10 → v11 (§3).
- `sync-record.ts`, `merge.ts`, and the export in `store.ts` — the keys and
  merge rules in §3.
- `routes.ts`:

| Method + path | Body | Answers |
|---|---|---|
| `GET /api/profiles` | – | each profile with `kids`, `kidsAge`, `parentId`, `admin`, `hasPin`; never a hash |
| `POST /api/profiles` | `{ actorId, pin, name, kids, kidsAge? }` | the new profile |
| `DELETE /api/profiles/:id` | `{ actorId, pin }` | 204 |
| `POST /api/profiles/:id/unlock` | `{ pin }` | 204 |
| `POST /api/profiles/:id/claim-admin` | `{ pin }` (sets it if the profile has none) | 204 |
| `PUT /api/profiles/:id/pin` | `{ actorId, pin, newPin }` | 204 |
| `PUT /api/profiles/:id/kids-age` | `{ actorId, pin, age }` | 204 |
| `PUT /api/kids/:setId` | `{ age: 6 \| 12 }` | 204, as today |

A wrong PIN is 403, a waiting player 429 with `Retry-After`, a refused role
403 with the reason, bad input 400.

`PATCH /api/profiles/:id` — rename — goes, with `renameProfile` in
`store.ts` and `watch-state.js`. Nothing calls it: the picker's "Rename or
remove…" only ever removed. Left in, it would let anyone rename the admin
with no PIN, and a rename cannot sync anyway (a name is a viewer's identity
— the core deferred rename for exactly that reason).

**Browser, `web/public/lib/`**

- `profile-picker.js` — tiles show "Kids · FSK 6" or "Kids · FSK 12". A
  grown-up's tile opens the PIN prompt (or "set a PIN" for one without); a
  kid's opens at once. "Who runs this household?" when there is no admin.
  The "not a login" note is rewritten per §1.
- `pin-prompt.js` (new) — the one PIN dialog: four digits,
  `inputmode="numeric"`, `autocomplete="off"`, the wait shown in seconds.
  Used by the picker and by Manage.
- `profile-manage.js` (new) — the panel from §2.
- `age-rating.js` — `forKidsProfile(sets, marks, limit)`, `kidsVerdict(set,
  limit)`.
- `watch-state.js` — the client calls for §5's routes; `kids()` returns the
  mark's age alongside its id.
- `playback/player-library-marks.js` — the Kids choice in §4.
- `catalog/shelf-view.js`, `catalog/settings-page.js`, `app.js` — the kid's
  own limit where 12 was assumed.

## 6. Android

**Core, `crates/mediagram-core/src/state/`**

- `schema.rs`, `record.rs`, `merge.rs`, `exchange.rs`, `lists_exchange.rs`
  — the same columns, keys and merge rules, held to the web by the shared
  fixtures (§7).
- `profiles.rs` — the same rules as `profiles.ts`, the same refusals, the
  same PIN format and hash, the same wrong-PIN counter (kept on the `Core`).
- `api/state.rs` — `create_profile` and `delete_profile` take the actor and
  PIN; new `unlock_profile`, `claim_admin`, `set_pin`, `set_kids_age`;
  `set_kids` takes an age. Refusals come back as one uniffi enum matching the
  web's reasons. `Profile` gains `admin`, `kids_age`, `parent_id`, `has_pin`,
  each with a uniffi default so existing `Profile(...)` call sites compile.
- The native library must be rebuilt after these changes: a stale `.so`
  builds cleanly and fails at launch with `UnsatisfiedLinkError`.

**Kotlin**

- `core/model/AgeRating.kt` — `forKidsProfile(…, limit)`; `KIDS_AGE_LIMIT`
  goes.
- `feature/catalog/profile/ProfileViewModel.kt` — unlock, claim admin, and
  the manage actions, each ending in done / wrong PIN / wait N s / not
  allowed.
- `ui-mobile/…/profile/` — a PIN dialog on the picker and a Manage profiles
  screen; `RemoveProfileDialog` becomes one of its actions.
- `ui-tv/…/profile/` — a four-digit pad that works with a D-pad alone;
  `TvAddProfileFlow` becomes the manage flow (add a kid with FSK 6/12; add a
  grown-up, admin only); `TvRemoveProfileDialog` one of its actions.
- `feature/player/PlayerMarksController.kt`, `ui-tv/…/player/TvMarksRail.kt`
  — the Kids choice in §4, hidden on a kids profile.

## 7. Checking it

- **Shared fixtures** in `web/test/fixtures/watch-state/`, which
  `crates/mediagram-core/tests/shared_watch_state_fixtures.rs` already runs
  against the core: a limit changed on two devices, an admin claimed on two
  devices, an older document with `kids: true` and nothing else, a mark
  moved from 12 to 6. The web is the authority; the core agrees with it.
- **Web** (`bun test`): every row of §2's table, allowed and refused; the
  wrong-PIN wait; the migration of an existing kid and mark; the routes'
  status codes; `forKidsProfile` at both limits with rated, unrated-marked
  and unrated-unmarked titles.
- **Core** (`cargo test`): the same rules and the same PIN hash for a known
  salt and PIN as the web computes.
- **Android** unit tests for the view model and `forKidsProfile`; the TV
  picker's instrumented test updated.
- **By eye**: the web through the stub preview (`cd web && bun run preview`),
  never the real player; Android on the tablet with `ANDROID_SERIAL=caad49da`,
  on a test profile, so Continue on the real ones is not touched.

## 8. Left for later

- **Removals that stick across devices.** Removing a profile still only
  removes it here; another device that holds it brings it back on its next
  sync. True of every removal today; making them propagate is its own change.
- **Moving a kid to another parent, turning a kid into a grown-up, handing
  on the admin role.** None was asked for.
- **Rename.** Removed from the web (§5), still absent from the core. A rename
  that syncs needs an identity other than the name.
- **The web Settings page's own lock** (`web/src/settings/admin-gate.ts`)
  stays separate. It guards the Telegram account, not a viewer, and the
  admin's PIN does not open it.

## Release

A new feature: one minor bump, in step, across `Cargo.toml`,
`web/package.json` and `android/app/build.gradle.kts`. Core and web land
before the Android screens, each as its own commit.
