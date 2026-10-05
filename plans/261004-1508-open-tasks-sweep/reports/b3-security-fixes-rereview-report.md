# B3: re-review of the four profile/PIN security fixes

Branch `worktree-agent-aecc271f05e5665ba`, `main...` = e22bd863..cfc09c3b (base 3f75e503). Read-only review.
Each fix was checked by walking its scenario through the code and then looking for anything it opens.

**Verdict:** fixes #1, #2 and #4 close what they name and open nothing new. #3 closes the hole on Android.
The same hole is still open on the web, though, and the amended contract says it cannot happen there,
which is false. There is also a sibling path that the `has_synced_once` gate does not cover.

## Bindings

I rebuilt `mediagram-core` (host cdylib, scratch target dir) and ran
`uniffi-bindgen generate --library` with the same `sed` step as `scripts/generate-android-bindings.sh`.
The output is **byte-identical** to the committed `mediagram_core.kt`. The docstrings and checksums match:
`create_first_admin` is 20033, `has_synced_once` is 19015, and `NotSynced` is variant 9, last in the
Rust enum and last in Kotlin. Nothing in `crates/` changed after e22bd863.

## Findings

### M1: The web still makes a first profile without hearing from the household, and the contract says it cannot (Medium)

- **The claim.** The fix report says "the web is unchanged because its server syncs before answering".
  The same claim appears in the contract §3 amendment (`shared-contract.md:114`), in
  `outcome.rs:24-26` (which also ships in the generated bindings), in `manage.rs:12-13` and in `ProfileWords.kt:40-41`.
- **What the code does.** `web/src/application/lifecycle.ts:53-60` does not await the start round. Its
  own comment says "Not awaited: … a page has nothing correct to gain by waiting".
  `index.ts:251` then goes on to serve.
- **No guard on the web side.** `ProfileManage.createFirst` (`web/src/state/profiles-manage.ts:54-59`)
  has no sync check at all. When `config.syncState && state.remembers` is false (`index.ts:~231`),
  there is no sync object at all, so no round ever runs.
- **Scenario.** A second machine starts a fresh web player for the household. The page loads while the
  start round is still listing and downloading, or that round fails because the machine is offline at start.
  The picker has no grown-up, so it offers "Create the first profile". The user types "André" and a PIN.
  The next round that succeeds merges André by name, `pin` newest-wins (`merge/tie_break.rs:78`),
  and the new PIN replaces André's on every device. This is the original #3, on the reference surface.
  Under CLAUDE.md § Surface Parity, a gap between the surfaces counts as a defect.
- **Fix, either way:**
  - (a) Port the guard. The web import sets the same `state_meta` marker, `createFirst` answers
    `not-synced`, and the picker shows the waiting line.
  - (b) If the web is meant to stay as it is, correct the contract and the four comments so they stop
    saying it is safe. Saying the risk is accepted is a choice for the user to make, not a fact.

### M2: A first PIN set on a stale PIN-less copy still overrides the household's PIN (Medium, existed before this branch, same failure class)

- **The paths.** A self `set_pin` on a grown-up with no PIN proves nothing (`manage.rs:165-169`). The same
  goes for `claim_admin` with a first PIN (`manage.rs:137-151`). On Android, `ProfileViewModel.pick` and
  `claim` lead there when `!profile.hasPin` (`ProfileViewModel.kt:136,148`). Both write a PIN stamped `now`,
  and the merge keeps the newer stamp.
- **Why the new gate misses it.** `has_synced_once` gates only `create_first`. A device that synced once,
  long ago, passes it.
- **Scenario.** It is live today, because 0.110.0 introduced PINs on 2026-10-05.
  1. A kid's tablet last synced before Dad set his PIN, so locally Dad has no PIN.
  2. The tablet is offline (in the car, with preloaded films).
  3. The kid taps Dad's tile, gets "choose a PIN", sets 0000, and is in Dad's profile.
  4. Back online, the next round makes 0000 Dad's PIN everywhere. If Dad is the admin, Manage opens as
     admin with it on every device.
- **Options:**
  - Refuse an unproven first PIN while the local row is older than the last round imported (needs a stamp,
    not a flag).
  - Or give a first PIN a stamp that any proven PIN beats (e.g. the row's `created_at`). This needs a
    contract change on both surfaces.

### L1: The first-round marker belongs to the device, not to the library (Low)

- **The cause.** `first_round_imported` (`sync/first_round.rs`) is one key per `state.db`.
  `SettingsViewModel.chooseLibrary` (`:144-150`) switches channels and keeps `state.db`.
- **When it matters.** A device whose library A had only kids (so `create_first` still applies) switches to
  library B. For up to one sync period it offers and accepts a first profile before B's names arrive.
- **How likely.** Narrow: it needs a household of kids only and Settings, which a chosen profile can reach.

## Checked, no finding

- **#1, the scenario.** The kid's library re-settles, so `showsLibrary` becomes true and the
  `LaunchedEffect` calls `manage.close()`. `close()` nulls `heldPin`, `cancel()` bumps `asking`, so an
  `actAs`/`newPin` answer still in flight is dropped (`PinAsk.kt:94`). A `change()` still in flight
  is dropped by `report`'s step guard (`ManageProfilesViewModel.kt:165`).
- **#1, the stop path.** On ON_STOP, Manage closes and the picker prompt is cancelled. Both gates call
  this before their early return. Manage opens only from the two gates.
- **#1, saved state.** The PIN dialogs use plain `remember`, and the only `rememberSaveable` is for names
  and the age, so a stop does not save a PIN.
- **#1, leftover in memory.** Manage's `newPin` closure still holds the old PIN in `PinAsk.send` after
  close (`PinAsk.kt:77-81` does not clear it). No UI path can reach it, because `enter()` needs a prompt
  on screen and `ask()` replaces the closure. Not a finding.
- **#1, rotation.** The manifest declares orientation/screenSize (`AndroidManifest.xml:59`). Other config
  changes recreate the activity, which closes Manage. That fails closed.
- **#2, storage.**
  - `prove()` loads, compares and saves under the one connection mutex, with no outer transaction to roll the count back.
  - A failed save gives `Invalid` whether the PIN was right or wrong, so there is no oracle.
  - No other code deletes `state_meta` or exports it.
  - The clock going back is clamped to now + 60 s.
- **#2, the clock going forward.** Moving the clock forward still ends a wait. That was already true
  before, since the wait used the same wall clock, so it is nothing new. Clearing app data also wipes the
  Telegram session (`resetAccount`/`forget`), so it is no practical reset.
- **#2, a behaviour change to note.** A count below 5 now survives forever until a right PIN
  (`pin_wait.rs:67-75`). It used to last for the process's life. This is a UX point, not a bypass.
- **#3, core.**
  - The marker is set inside `import_merged`'s transaction, which has no early returns, and the import
    runs whenever `list()` succeeds, even with an empty channel.
  - Unparseable documents are skipped rather than failing the round.
  - `create_first` checks the marker last, under the same mutex as the import.
- **#3, Android.**
  - `attempt()` reloads while `syncedOnce` is false, so a late round still brings up the form.
  - `invalidate()` resets the flag.
  - The gates sit behind setup `Ready`, which means signed in with a library.
- **#3, trade-off (not a finding).** One pinned document that cannot be downloaded fails the whole round
  (`telegram_channel.rs:157`, `download_capped(..)?`), and the hold has no way out. A fresh device in such
  a household waits forever instead of starting, as it could before.
- **#3, `RealCoreContractTest` seed.** It writes `state_meta` with `user_version` 0, and the core's
  `IF NOT EXISTS` migrations accept that. Plausible, but not run.
- **#4.** A pure move. The class went from `data class` to `class` and nothing compares instances.
  The phone and TV wiring are unchanged.

## Unresolved questions

- M1: should the web get the guard (parity), or the contract say the risk is accepted?
- M2: is fixing the first-PIN-on-a-stale-copy path in scope for this sweep, or a new issue?

**Status:** DONE_WITH_CONCERNS. Findings: 0 critical, 0 high, 2 medium, 1 low.
