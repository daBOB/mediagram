# Rust session lifecycle reassessment

Status: DONE

The three assigned findings are repaired. Subscription, stream initialization,
stream waiting, and state-document upload now preserve raw errors until the
shared revoked-session handler examines them. A current connection's RPC 401
removes its persisted authorization and resets its connection and listener.
Refusals from a replaced connection retain the operation's contextual `Network`
error without invalidating the replacement; ordinary failures also retain their
existing contextual messages.

The upload fix also handles `io::Error::other(InvocationError)`: following only
`Error::source()` skipped the wrapped invocation error. Upload now returns its
original IO error to the production publication flow, which checks revocation
before any edit/send/pin operation.

## Connection and listener ownership

- A listener retains the session identity of the connection that supplied its
  one-shot update receiver. A dropped stream invalidates that matching connection,
  allowing the next subscription/open attempt to obtain a fresh receiver.
- An old listener failure cannot discard a concurrent replacement. Subscription
  also takes only its own connection's receiver; it cannot consume a replacement's.
- Revocation clears an idle listener immediately. If an active wait holds the
  listener mutex, quitting its pool wakes the real update stream; the waiter then
  clears its slot. The state mutex is released before the listener try-lock.
- Key deletion occurs while connection creation is locked, so a concurrent caller
  cannot recreate a connection from the revoked file during the reset.
- Subscription, stream setup/waiting, and state-channel listing, download,
  upload, edit, send and pin carry their original connection identity. Comparison,
  reset and key deletion share one state critical section. Idle-listener cleanup
  also matches the failed identity, preserving a listener opened after the reset.
- Account lookup, library listing, index searches/download, and playback now use
  the same ownership rule. Playback captures its document cache with its client
  under one lock. The optional marker search still falls back to pins for ordinary
  errors, but a 401 is handled explicitly. Sign-out behavior is unchanged.

The public Core/UniFFI API is unchanged. Private modules separate receiver ownership
(`account/session_updates.rs`), listener waiting (`events/listener.rs`), and stream
initialization (`events/open.rs`). All touched production Rust files remain below
200 lines; the largest is 190 lines. Identity-free revocation helpers were removed
after migrating all remaining production callers. Private raw iterator adapters
and account-request injection exercise the actual foreground orchestration offline.

## Verification

The tests use temporary persisted-key files, real lazy sender pools and update
streams, and controlled raw RPC/upload results. They do not invoke Telegram.
The successful reinitialization test executes the actual subscription/open path;
concurrency cases hold the real listener mutex while another operation revokes
the session or replaces its connection.

| Check | Result | Log |
| --- | --- | --- |
| Initial API regressions before fixes | 109 passed, 4 failed: wrapped 401, upload 401, idle cleanup, dropped-client reinitialization | `/tmp/rust-session-lifecycle-red.log` |
| Review-driven stale-session regressions before revision | 3 listener-stage failures and 1 upload failure | `/tmp/rust-session-stale-revocation-red.log`, `/tmp/rust-session-stale-upload-red.log` |
| Focused API tests before foreground extension | 127 passed | `/tmp/rust-session-stale-revocation-green.log` |
| Foreground regression matrices before/after migration | 6 failed before; all 6 passed after | `/tmp/rust-foreground-session-red.log`, `/tmp/rust-foreground-session-green.log` |
| Final full `mediagram-core` tests | 332 passed, 1 intentional network test ignored | `/tmp/rust-foreground-session-core.log` |
| Final core all-target Clippy with warnings denied | Passed | `/tmp/rust-foreground-session-clippy.log` |
| Explicit owned-file rustfmt and diff checks | Passed | Run locally |

Twenty-five added regressions include ordinary-error controls, consumed-receiver
initialization failure, full re-subscription, active concurrent revocation, stale
refusals at each owned error boundary, and replacement preservation. Replacement
tests check a distinct persisted key, live session identity, and receiver ownership.
Independent review found the stale-401 gap after the initial implementation;
four further regressions reproduced it before revision. The revised source and
final gate evidence passed independent rereview. The foreground extension adds
current-401, current-nonauth-error and stale-401 matrices at each newly owned
boundary; all six matrices failed before the fix. Its final source and 332-test
gate also passed independent rereview.

Dependency behavior was checked against the official grammers 0.10 source:
[upload errors](https://docs.rs/crate/grammers-client/0.10.0/source/src/client/files.rs)
are IO errors, and [update stream initialization and waiting](https://docs.rs/crate/grammers-client/0.10.0/source/src/client/updates.rs)
have distinct error boundaries. The regression tests verify the behavior used here.

Concerns/Blockers: none found. Workspace-wide final gates and finding resolution
remain with the parent task. No commits, scanner mutations, live sessions, or
background processes were introduced; the Cargo slot was released.
