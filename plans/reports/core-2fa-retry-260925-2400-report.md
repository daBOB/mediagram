# Core: two-factor password retry after a wrong entry

Commit: `86d4861 fix(core): let a two-factor password be retried after a wrong entry` (branch feat/android-tv-ui, not pushed).

## What was wrong
- grammers-client 0.10 `check_password` (client/auth.rs:416-465) returns `InvalidPassword(PasswordToken { password: password_info })` carrying the same `srp_id`/`srp_B` it just spent. Telegram SRP params are single-use, so the retry got `SRP_ID_INVALID`.
- The core stored that spent token, and on `SignInError::Other` restored nothing, so the third try said "no password step is in progress".

## Fix
- New `crates/mediagram-core/src/api/account/auth_password.rs` (password step moved out of `auth.rs`, re-exported from it; `auth.rs` shrank to ~140 lines).
- `PendingPassword` is now `Ready(Box<PasswordToken>)` | `Refetch`.
- Invariant (module doc): a password step is only ever dropped by success or by starting over (`Attempt::begin`).
- Each token is used for exactly one check. After a failed check:
  - wrong password / non-network refusal (e.g. SRP_ID_INVALID) / out-of-step answer: fetch fresh params via `account.getPassword` on the same client, store `Ready(fresh)`; if that fetch fails, store `Refetch`. The viewer still sees the original verdict ("the password was not accepted" / "Telegram refused the sign-in (…)").
  - network fault (incl. flood wait, 5xx): store `Refetch` without fetching — the proof may or may not have reached Telegram, so its params are treated as spent.
  - `Refetch` makes the next `check_password` fetch first; if that fetch fails the step stays `Refetch`.
- `fetch_token` rejects parameters missing `current_algo`/`srp_B`/`srp_id` (grammers would `unwrap` them and panic across FFI).
- The `Attempt` resume/complete/persist pattern is unchanged: the check and the refetch run inside the one future handed to `attempt.complete`, so a stale attempt still cannot restore a step or persist a key.

## Seam
`finish_password(core, attempt, pending, check, fetch)` takes the two Telegram requests as closures (`FnOnce(PasswordToken) -> Future<Result<(), SignInError>>`, `Fn() -> Future<Result<PasswordToken, CoreError>>`). This extends the existing seam (previously it took the response future). No trait added.

## Tests (auth_tests.rs)
New:
- `a_wrong_password_then_the_right_one_signs_in_with_fresh_parameters` (second check asserts it received the fresh token, not the spent one; key persisted)
- `a_spent_srp_id_keeps_the_step_open_with_fresh_parameters`
- `a_network_fault_keeps_the_step_and_refetches_on_the_next_check`
- `failing_to_fetch_fresh_parameters_still_keeps_the_step`
Replaced `the_current_password_step_can_retry_then_persist_its_own_session` (asserted the old stale-token behaviour). Existing stale-attempt tests adapted to the new signature and still pass.

Results:
- `cargo test -p mediagram-core`: 425 passed, 0 failed (1 ignored, pre-existing).
- `cargo clippy -p mediagram-core --all-targets --all-features -- -D warnings`: clean.
- `cargo fmt --check`: the three touched files are clean. The crate as a whole is NOT fmt-clean at baseline (~20 unrelated files, e.g. api/mod.rs, search/normalize.rs) — pre-existing, left untouched.

## Android rebuild
`CARGO_INCREMENTAL=0 ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/build-android-core.sh`: all four ABIs built; `jniLibs/{arm64-v8a,armeabi-v7a,x86_64,x86}/libmediagram_core.so` timestamped 2026-09-26 00:01. UniFFI surface unchanged (`Core::check_password(String)` same signature; only crate-private items changed), so no binding regeneration or Kotlin changes; gradle not run.

## Unresolved / concerns
- Not verified on the TV box yet; needs a device retest (wrong password, then right one).
- `sign_in` still drops everything if grammers' internal `getPassword` fails right after SESSION_PASSWORD_NEEDED (surfaces as `Other`, indistinguishable from a code refusal). Out of scope; restart of sign-in recovers.
- Baseline `cargo fmt --check` failures in unrelated files.
