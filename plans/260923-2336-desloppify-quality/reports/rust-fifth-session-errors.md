# Sign-out locking and nested error diagnostics

Status: DONE

## Changes

`Core::sign_out` now retains the session-state mutex guard across live-state
reset and removal of `session.key`. Lazy connection creation uses that same
mutex, so it cannot rebuild a session from the outgoing key between those two
operations. This matches the existing revoked-session cleanup boundary.

The implementation remains in `api/account/profile.rs`. A private
`sign_out_with` helper supplies only the RPC and filesystem-removal boundaries
for tests; production passes the real Telegram sign-out operation and existing
key remover. The ten-second remote timeout, best-effort remote logout, local
removal error, public Rust/UniFFI signature and listener-lock behavior are
unchanged. Existing profile tests moved intact to `profile_tests.rs` alongside
the new regressions, keeping the production module at **97 lines**.

`CoreError::io`, `network` and `logged` now use alternate Display formatting for
the private diagnostic cause. This preserves nested `anyhow` context and its
underlying cause. The returned public variants and sanitized messages remain
identical. `error.rs` is **60 lines**; no dependencies were added.

Changed files:

- `crates/mediagram-core/src/api/account/profile.rs`
- `crates/mediagram-core/src/api/account/profile_tests.rs` (new)
- `crates/mediagram-core/src/error.rs`
- `crates/mediagram-core/src/error_tests.rs`

Session creation, revocation, shared fixtures and other production modules were
read but not edited.

## Regression evidence

The sign-out tests use the existing real local session fixture, a temporary key
file, an injected successful or refused RPC result, and the real lazy
`session::connection` future. They poll that future precisely at the filesystem
removal boundary. This deterministically detects the unlocked interval without
sleeping or sending an RPC. After removal the same future completes, and its
replacement session has no outgoing auth key.

Before the guard fix:

```sh
cargo test -p mediagram-core --lib api::account::profile::tests
```

**4 passed, 2 failed** in `/tmp/rust-fifth-signout-before.log`. Both the successful
and refused remote-logout cases admitted reconnect before removing the key.

A third new test substitutes a real directory collision at `session.key` during
the controlled remote operation. Actual unlink fails, the connection is reset,
and the public result remains `Io("removing the stored login")`.

Diagnostic tests install a local tracing subscriber and inspect emitted event
fields. All three helper tests construct an `anyhow` error with two context
layers and a private document identifier in the underlying cause. They assert
the full logged chain and the exact unchanged public error text.

Before the formatting fix:

```sh
cargo test -p mediagram-core --lib error::tests
```

**2 passed, 3 failed** in `/tmp/rust-fifth-error-chain-before.log`: each new test
captured only the outer context instead of the full chain.

Final verification:

| Command | Result | Log |
| --- | --- | --- |
| `cargo test -p mediagram-core --lib api::account::` | 26 passed | `/tmp/rust-fifth-account-green.log` |
| `cargo test -p mediagram-core --lib error::tests` | 5 passed | `/tmp/rust-fifth-error-chain-green.log` |
| `cargo clippy -p mediagram-core --lib --tests -- -D warnings` | Passed | `/tmp/rust-fifth-session-clippy.log` |
| `rustfmt --edition 2024 --check` on the four changed Rust files | Passed | `/tmp/rust-fifth-session-fmt.log` |

Scoped `git diff --check` passed. Account coverage also retains the existing
sign-in retry, stale completion, revocation and key-permission regressions.

Root's independent source/test review passed: the named guard's lexical lifetime
spans unlink, the real connection future proves the pending boundary, and log
tests preserve sanitized public payloads. The review also checked that the TMDB
request boundaries already strip credential-bearing reqwest URLs before those
causes can reach the diagnostic helpers.

## Limits and handoff

If deleting the local key itself fails, sign-out still returns its existing IO
error; this change does not claim successful credential deletion in that case.
The mutex is held only across reset and synchronous removal, never during the
remote request. No new timeout behavior was introduced.

No live Telegram, user data, public API, manifest, scanner state or git state
mutation was used. Test processes exited; temporary fixtures clean up on drop.
No whole-workspace checks were run. Root owns independent review and the final
coordinated gate.
