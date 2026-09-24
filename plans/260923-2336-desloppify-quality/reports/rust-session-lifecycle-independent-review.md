# Independent review: Rust session and listener lifecycle

Status: DONE

Final verdict: PASS. One high-priority finding was identified, repaired by the
implementation owner, and independently re-reviewed. No unresolved finding
remains in the reviewed scope.

## Scope and method

Reviewed the pending production diff and new modules under
`crates/mediagram-core/src/api/`: `events.rs`, `events/{listener,open}.rs`,
`account/{session,session_updates,subscribe,revoked}.rs`, and the state-sync
publication/channel path. Read the focused lifecycle and publication tests,
the worker's reassessment report, and the surrounding login-attempt and
sign-out ownership paths. The final controller-approved extension also covers
`account/profile.rs`, foreground channel listing/search/index download, and
`read.rs` with their regression fixtures. Public Core/UniFFI signatures were compared with
the previous implementation.

Production review was read-only. The reviewer did not repeat tests: initial
logs confirmed 120 API tests, 319 core tests with one intentional network
ignore, and strict core all-target Clippy. The concrete concern below prompted
the owner to add failing regressions, repair it, and rerun the affected gates.

## Finding: late revocation can destroy a replacement login

Severity: high. Resolved in the revised implementation.

The initial dropped-stream path preserved a replacement by calling
`session::invalidate(core, &failed.handle)`. The initially added 401 paths instead
called identity-free `revoked::checked` or `unless_revoked`. That handler reset
the current `State` and deleted the current persisted key, irrespective of
which connection produced the error.

Concrete initial paths:

- `account/subscribe.rs::subscribe_with` captures `(client, handle)`, awaits
  the old client's GetState result, then passes a 401 to the global handler.
- `events/open.rs::finish` conditionally invalidates the old connection, but
  subsequently invokes the global handler, defeating replacement protection.
- `events/listener.rs::finish_wait` clears the failed listener on 401 and then
  invokes the global handler without its captured identity.
- `state_sync.rs::sync_state` captures only a client, and
  `state_sync/publish.rs::put` forwards upload 401 to the same handler.

If an old request completed after a replacement login had installed a different
key and client, that old response signed out the new login and deleted its key.
The initial replacement tests covered `Dropped` and an old successful
subscription, but not delayed 401 results.

The controller authorized repair, and the owner confirmed the defect with four
failing-before cases: subscription, stream initialization, waiting, and upload.

## Other initial review results

- The IO wrapper fix inspects the wrapped `InvocationError` itself before
  following further sources, retaining upload revocation semantics. Ordinary
  failures keep contextual Network errors and do not publish after failed
  upload.
- No blocking lock cycle was found. Event waits hold the event lock, acquire
  state only briefly, and do not hold state during network waiting. Revocation
  releases state before trying the event lock without waiting.
- Receiver ownership is tied to the session that subscribed. A stale successful
  subscription cannot take a replacement's receiver; ordinary initialization
  failure resets the consumed connection so a subsequent attempt can reopen.
- The public Core/UniFFI API is unchanged. New module boundaries and the
  DocumentWriter error-type change are private.

## Re-review of the repair

The revised implementation passes independent source review:

- `account/revoked.rs::unless_revoked_for` checks the captured session identity while holding
  the same state mutex used to reset the state and remove its persisted key.
  There is no check/reset gap. An obsolete request gets its contextual Network
  fallback; it neither deletes the new key nor reports that the new login was
  signed out.
- `account/revoked.rs::clear_idle_listener` clears an idle listener only when its session matches
  the failed connection. The event lock remains nonblocking and is acquired
  after releasing state, preserving lock ordering and replacement listeners.
- `account/subscribe.rs` retains the original handle across GetState.
  `events/open.rs::finish` performs identity-aware revocation before conditional
  invalidation; a current 401 therefore still clears authorization, while an
  ordinary initialization failure still releases the consumed receiver.
- `events/listener.rs:66` captures the waiting listener's handle before clearing
  its slot and passes that identity to error handling. Current and stale 401s,
  and dropped connections, retain their intended distinct outcomes.
- `state_sync.rs` captures the client and owner together. The owner flows through
  `TelegramStateChannel` and every upload/edit/send/pin/list/download refusal.
  No handler obtains a new identity after receiving an old response.

The regression fixtures use distinct replacement-key bytes and assert retained
client/key/receiver ownership. They cover stale subscription/open/wait/upload,
edit/send/pin, listing/download, and cleanup after a replacement listener opens.
Current-session 401 and ordinary-error controls remain green.

Final evidence read by this reviewer:

| Check | Result | Log |
| --- | --- | --- |
| Stale subscription/open/wait before repair | 3 intended failures | `/tmp/rust-session-stale-revocation-red.log` |
| Stale upload before repair | 1 intended failure | `/tmp/rust-session-stale-upload-red.log` |
| Revised API tests | 127 passed | `/tmp/rust-session-stale-revocation-green.log` |
| Revised full core tests | 326 passed; 1 intentional network ignore | `/tmp/rust-session-stale-revocation-core.log` |
| Revised all-target Clippy, warnings denied | Passed | `/tmp/rust-session-stale-revocation-clippy.log` |

## Final foreground-path extension

PASS after independently reviewing the final source and six further regression
matrices. The same late-401 cause was present in older account/channel/read
callers, so the controller extended the repair to those paths:

- Account lookup captures client and owner together before awaiting `get_me`.
  Dialog listing, pinned/marked index searches, and index download carry that
  original owner through their raw response boundaries. No handler fetches a
  new identity after its old operation has failed.
- Playback captures client, owner and the session's document cache in the same
  state critical section; download error handling receives that captured owner.
  This prevents mixing an old transport with replacement document state.
- Marker-search 401 errors now reach revocation handling. Ordinary optional
  search failures still stop that search and permit selection from collected
  pins, preserving the existing fallback. Message filtering and selection are
  unchanged by moving the search implementation into its own module.
- The identity-free `checked`/`unless_revoked` helpers are removed. Source search
  confirms every automatic revocation call requires an owner. Explicit
  `sign_out` behavior and all exported Core/UniFFI signatures are unchanged.
- The private iterator adapters forward raw production responses. Fixtures use
  real lazy pools and temporary persisted keys with distinct bytes 7 and 9,
  testing current 401, ordinary 500, and stale 401 for account, listing, pinned
  search, marked search, index download and playback error handling. Download
  additionally asserts the actual partial temporary file before refusal.

The six cases fail before the repair in `/tmp/rust-foreground-session-red.log`
and all pass in `/tmp/rust-foreground-session-green.log`. Final full-core evidence
supersedes the earlier 326-test checkpoint: `/tmp/rust-foreground-session-core.log`
reports **332 passed, 0 failed, 1 intentional network ignore** (236 library and
96 integration tests). `/tmp/rust-foreground-session-clippy.log` confirms strict
all-target Clippy passed. No tests were repeated by this reviewer.

Touched production modules remain below 200 lines; the largest is 190 lines.
The public Core/UniFFI API remains unchanged. The implementation owner released
the Cargo slot after verification; this reviewer started no background process.

Concerns/Blockers: None in the reviewed scope. This review changed only this
report; production fixes and regression execution belong to the implementation
owner. Final workspace gates and finding disposition remain with the controller.
