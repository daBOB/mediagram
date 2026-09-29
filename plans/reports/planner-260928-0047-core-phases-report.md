# Planner report — core phases 04 and 05 (profile roles, PINs, per-kid limits)

Plan: `plans/260928-0047-profile-roles-pins-kids-age-limits/`
Written: `phase-04-core-schema-sync-merge.md`, `phase-05-core-rules-pin-api.md`; revised after the
contract amendment of 2026-09-28 (§3, §7, §9, §12). No source, test, spec, `plan.md` or
`shared-contract.md` touched.

## Summary

- **04 (data half, ~5h, pushable alone):** schema v7 (contract §6 verbatim), `ProfileRoles`
  (`admin`/`kidsAge`/`parent`/`pin`) flattened onto `ProfileState` and `MergedProfile`, `ListRow.age` read
  only on Kids rows, role merge in `merge/profile_roles.rs` (reuses `tie_break::keep`; `parent` on kids
  only; admin = earliest claim among grown-up viewers, chosen after the pass because kid status is
  sticky across documents), export/import in `exchange/profile_roles.rs`, `rows::set_kids(…, Option<u8>)`
  + `kids_from_six`, runner for `profile-roles-merge.json`. Ends with a proof that regenerated bindings
  are byte-identical.
- **05 (rule half, ~6h, lands with 06):** `pin.rs`, `pin_wait.rs` (clock = `now` parameter), pure
  `rules.rs`, `request.rs` + `role_rows.rs` + `manage.rs` implementing amended §3 (structural
  `not-allowed` before the wait; wait only before a PIN comparison), `create_first_admin` (bootstrap),
  `Profile` fields with uniffi defaults, `ProfileOutcome`, eight `Core` calls in new
  `api/state/profile_roles.rs`, `set_kids(age)`, `StateSnapshot.kids_from_six` (last field),
  `create_profile`/`delete_profile(id)` removed, runners for `pin-hash.json`/`profile-rules.json`,
  regenerated bindings + all-ABI `.so`. Integration tests now build households through the public
  surface (`create_first_admin` + `create_grown_up`), no SQL seeding.
- Contract §5 hash vectors verified with `sha256sum` — both correct.

## Findings the plans act on

- **Latent migration failure:** `open()` runs `migrate` before `repair::add_missing_kids_column`
  (`state/mod.rs:111-112`); v7's `UPDATE … WHERE kids = 1` would make a pre-release v3-without-`kids` file
  unopenable. Phase 04 Task 2 moves the repair first, guarded to `user_version >= 3`.
- **Old fixtures would break:** `merge.json:858-869` has a kid; the core runner compares whole results,
  so `canonical` clears `roles` for the old files, as the web runner picks fields.
- **Kids tie never converged:** core `import_kids` skips every equal-time row (`lists_exchange.rs:58`);
  the web takes an equal-time row that differs (`web/src/state/lists-exchange.ts:86-87`, extended to the
  age by phase 01 Task 5.4). Phase 04 ports the web rule — otherwise a tie with another age never settles.
- **Parity adopted from web phase 01:** Kids `age` exported on live marks only; kids upgrade sets
  `kids_age = COALESCE(kids_age, 12)`; a new kid stores `kids_age = 12`.
- **Nested transaction trap:** `profiles::delete` holds its own transaction, so `role_rows::apply`
  does not wrap `Remove`.

## Module / file-size decisions (Rust hard limit 200)

| Pressure | Decision |
|---|---|
| `state/merge.rs` = 200 | Move `MergedProfile`/`MergedState` to `merge/merged.rs` (+`pub use`) → ~165; role merge in `merge/profile_roles.rs` (~90) |
| `state/rows.rs` = 195 | Kids fns to `rows/kids_marks.rs` (+`pub use`) → ~170 |
| `api/mod.rs` = 200 (no room on `Core`) | Wrong-PIN count is `Mutex<PinWait>` on `StateDb` (one per `Core`); `state/mod.rs` 194 → 197; reached only via `StateDb::guarded` in `profiles/manage.rs` |
| `state/profiles.rs` = 160 | New code in `state/profiles/{pin,pin_wait,rules,request,role_rows,manage}.rs`; `profiles.rs` ~194 (fallback: move `list` into `role_rows`) |
| `api/state.rs` = 180 | Eight calls in `api/state/profile_roles.rs` (~125) → `state.rs` ~168 |
| Wire/merged role keys | One `ProfileRoles` with `#[serde(flatten)]`, reused by record, merge, exchange, fixture runner |

## Bindings / `.so` commands found

- Bindings **and** all four ABIs: `CARGO_INCREMENTAL=0 ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/generate-android-bindings.sh`
  (calls `scripts/build-android-core.sh`, then `uniffi-bindgen generate --library …/arm64-v8a/libmediagram_core.so --language kotlin --no-format`, then trims whitespace).
- `.so` only: `scripts/build-android-core.sh`. `ANDROID_NDK_HOME` is unset in agent shells; path from
  `plans/reports/merge-main-second-260926-0145-report.md`; `cargo-ndk 4.1.2` installed.
- Freshness check (phase 04): generate into `mktemp -d`, trim, `diff` against the committed file.

## Kotlin breakage handed to phase 06

`core/testing/.../FakeCore.kt:404,432,450` (+ missing `createFirstAdmin` and the other new members),
`core/testing/.../CoreContract.kt:70,79,89,90,97` (→ `RealCoreContractTest`, `FakeCoreContractTest`),
`core/data/.../WatchStateRepository.kt:255,271,306`, `WatchStateRepositoryTest.kt:37,68`,
`WatchStateOwnershipTest.kt:50`. Positional `StateSnapshot(…)`/`Profile(id, name[, kids])` still compile.
Phase 06's plan matches every 05 signature except `createFirstAdmin(name, newPin): ProfileOutcome`,
which it does not list yet.

## Contract questions — status

| # | Question | Resolution |
|---|---|---|
| 1 | Bootstrap | Amended §3/§9: `create_first_admin` — implemented in 05 |
| 2 | Malformed current `pin` | Amended §3: compared, `wrong-pin`, counts — as planned |
| 3 | Scope of `wait` | Amended §3: only before a PIN comparison; structural `not-allowed` first — 05 rewritten |
| 4 | claim-admin order | Structural `not-allowed` before the PIN — 05 rewritten |
| 5 | `parent`/`admin` restrictions | Amended §7: `parent` kids only; claims on kid viewers ignored — 04 rewritten |
| 6 | Parse coercion | Matches web phase 01 (`age` JSON number only; timestamps scalar-coerced; lowercase hex) |
| 7 | Role check at parse or merge | Matches web phase 01 (shape at parse, role at merge) |
| 9 | `set_kids` age ∉ {6,12} | Matches web (`age === 6 ? 6 : null`) — stored as from 12 |
| 10 | Tombstone age | Web phase 01: live marks only — adopted in 04 |
| 11 | Absent optional in `expect` | Web runner omits absent keys and uses `toEqual` — "must be absent", as planned |

## Still needing a decision

1. **Clock clamp (Q8) — web parity.** The core stamps local Kids-age changes, kid limits and PINs at
   `MAX(now, last + 1)` (precedent `rows::set_watched`) so a change beats an imported row from a faster
   clock; web phase 01's `setKids` stamps `Date.now()`, and phase 02 (limits, PINs) is not planned yet.
   Recommendation: the web adopts the same. Otherwise it is a deliberate difference for phase 08 to
   record in `docs/system-architecture.md`. Not fixture-visible.
2. **Web phase 01 predates the §7 amendment.** Its "For the core port" note says `parent` is output on
   any viewer, and its merge considers every admin claim. The core now follows amended §7 (kids-only
   `parent`; claims on kid viewers ignored). Phase 01's fixture has no case that tells the two apart, so
   the runners agree while the implementations differ — phase 01 needs the same change.
3. **Reading to confirm with phase 02:** a PIN-less grown-up acting during a running wait gets `no-pin`,
   not `wait`. That is the only reading under which §3 items 4 ("only when about to compare") and 5 both
   hold. No shared fixture pins refusal order, so phase 02 must implement it the same way.

**Status:** DONE_WITH_CONCERNS
**Summary:** Both core phases are revised to the amended contract (bootstrap call, refusal order,
kids-only parent, grown-up-only admin) and aligned with web phase 01's decisions on the Kids mark wire.
Three cross-surface parity items remain for the lead: the clock clamp, phase 01's pre-amendment merge
rules, and the `no-pin`-during-wait reading.
