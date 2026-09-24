# Independent review: MP4 bounds, export documentation and staging test

Status: DONE

Review result: PASS. No correctness regression or blocking coverage gap found.
This was a read-only source/log review; no tests or production edits were made.

## Evidence

- `crates/mediagram/src/media/mp4_atoms.rs`: `pos` starts at zero, and every
  accepted box is at least its header size and at most `file_len - pos` before
  increment. Consequently `pos <= file_len` remains true, subtraction cannot
  underflow, and adding an accepted size cannot overflow. The bounds check also
  precedes the early return after finding both landmarks. Size-zero boxes and
  extended-size boxes ending exactly at EOF retain their prior order behavior.
- `crates/mediagram/tests/media_mp4_atoms.rs`: the tests exercise short/truncated
  headers, overlarge ordinary boxes, `u64::MAX` extended boxes at zero and
  nonzero offsets, both landmark orders, and exact EOF boundaries. The recorded
  red run has **7 passing and 3 failing tests**, including an actual arithmetic
  overflow; the green run has **10 passing tests**. Evidence:
  `/tmp/rust-mp4-bounds-red.log` and `/tmp/rust-mp4-bounds-green.log`.
- `crates/mediagram-core/src/state/exchange.rs`: the documentation correction
  matches `api/state.rs::snapshot`, `state/rows.rs` timestamp fields and
  `state/lists_exchange.rs` exports. Snapshots do retain progress and watched
  timestamps; sync additionally carries list timestamps and removal tombstones.
  There is no wire-format or runtime change in this diff.
- `crates/mediagram-core/src/versions/install.rs`: explicitly polling the second
  real `Staging::begin` future guarantees it attempted to acquire the turn before
  asserting Pending. This replaces a sleep that could pass merely because the
  spawned task had not run. The test then publishes the first version, awaits
  the second acquisition, checks empty staging, and confirms the first payload
  survived. The supplied run passes **2 staging tests** in
  `/tmp/rust-staging-poll-green.log`.

## Scope and precision

The MP4 scanner remains a top-level ordering probe, not complete file validation:
it stops once both landmarks are known. This is preserved behavior. Root was
notified that “An inspected box” would make the new bounds-error documentation
more precise than an unqualified “A box”; no broader validation change is needed.

No source, test, scanner or git-state mutation was performed during this review.
No processes were started.
