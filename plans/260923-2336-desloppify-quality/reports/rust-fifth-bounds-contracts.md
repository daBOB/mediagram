# Rust box bounds and contracts

The MP4 atom scanner now rejects a declared box that extends beyond the file
before recording either ordering landmark. Remaining-byte comparisons avoid
overflow both in the loop guard and when checking a 64-bit size header; advancing
the position is bounded by the same check. Valid zero-sized final boxes and
extended headers ending exactly at EOF preserve their prior ordering result.

The original malformed-size test expected success for a truncated `moov`; its
expectation now requires the explicit error. Before the repair, three regressions
failed: the oversized 64-bit box panicked in the loop guard, and the truncated
ordinary/second-landmark boxes returned success. All ten file-based parser cases
pass after the repair. Logs: `/tmp/rust-mp4-bounds-{red,green}.log`.

The staging exclusion test pins the second installation future, polls it once,
asserts `Pending` and preservation of the first installation's unfinished file,
then awaits that same future after publishing the first version. Both staging
tests pass (`/tmp/rust-staging-poll-green.log`); no scheduling sleep remains.

The export rustdoc now distinguishes the timestamps retained by UI snapshots
from exported list timestamps/tombstones and the all-profile scope. Verified
against `api/state.rs`, `state/rows.rs`, and `state/lists_exchange.rs`; no data
shape or behavior changes were needed for that documentation correction.

Independent review passes (`rust-fifth-bounds-independent-review.md`). The final
workspace gate, including account diagnostics and native StateDb retirement,
passes 1,025 tests with four intentional ignores, formatting, strict Clippy and
rustdoc (`/tmp/rust-retirement-final-{format,clippy,tests,doc}.log`). The tested
source is committed in the checkpoint through `7b6ee469`; see
`git-manager-rust-android-sixth-commits.md` for exact path hashes.
