# Rust strategy reconciliation

Status: implementation, evidence reconciliation and postflight scans complete;
fresh subjective review remains a delivery step. This report does not claim the
overall quality goal complete or any project commit pushed.

## Attribution and delivery

The authoritative finding identities and notes remain in Desloppify's local
state, changed only through its CLI. Current resolved review findings map to:

| Review dimension | Fixed findings |
| --- | ---: |
| Error consistency | 6 |
| Test strategy | 6 |
| Contract coherence | 6 |
| Mid-level elegance | 4 |
| Naming quality | 3 |
| Type safety | 3 |
| Logic clarity | 2 |
| Cross-module architecture, high-level elegance, API coherence, design coherence, convention consistency | 1 each |

Mechanical work is distinct: nine coverage findings have added behavior tests;
40 Clippy finding locations have verified annotations, documentation, checked
conversions or package metadata corrections. Eight regex/serialization invariants,
95 public-shape advisories and three intentional synchronization advisories retain
their accepted strict-score cost. Nothing is called fixed merely because its
location moved or a later detector omitted it.

The checkpoint plan records language-specific progress and original dirty-file
constraints. Independent implementation reviews and focused evidence are linked
from the [progress report](pm-260924-0030-rust-review-checkpoint.md),
[specification review](spec-diagnostic-disposition.md),
[adapter coverage report](worker-260924-state-adapter-prepare-coverage.md), and
[typed-error report](worker-260924-sync-typed-errors.md). Fifteen focused Rust
commits now exist through `55f8ab2`, with version 0.40.2 synchronized across all
three manifests. Twelve intermediate staged snapshots independently compile.
Eighty fully committed findings are recorded through the CLI; pending Android,
architecture-documentation and strategy-report work is not recorded as committed.
See the [commit report](git-manager-260924-rust-focused-commits.md).

## Testing deficit

The historical 13.4-point Test health gap was 95.7 lenient versus 82.3 strict.
The current pre-postflight state is 100.0 versus 86.4. This gap is not simply a
count of declined fixes: the installed policy counts `auto_resolved` findings
against strict scoring. Coverage state contains 33 auto-resolved findings, nine
verified manual fixes, two evidenced false positives, and one retained advisory.

The 33 auto-resolved entries cover CLI command adapters, package preparation,
verification/upload flows and core authentication/document/transport modules.
Six refer to moved files. Their disappearance is not proof of new tests and
they have not been bulk reclassified. A follow-up audit should correlate current
owners with executable behavior tests before any further manual resolution.
The package-naming advisory remains accepted because the existing public fixture
suite already checks digest choice, epoch limits, leap centuries and timestamp
extremes. The two false positives have actual hostile-JSON and charset fixtures.

New coverage exercises real SQLite migration rollback and reopen, public state
facades, Clap parsing, blocking-thread execution and panic propagation, safe
error rendering, concurrent persisted device identity, real ffmpeg media survey,
Telegram adapter iteration boundaries, and actual CLI dry-run reporting. Tests
do not pretend to exercise live Telegram RPCs. The broader database-repair slice
demonstrated failure detection: malformed/unreadable schema-version regressions
failed before the repair and all eight database tests passed afterward, recorded
in `.desloppify/migration-before.log` and `migration-after.log`.

The latest workspace run passes 969 tests with four intentional live/network
ignores. Formatting and Clippy with warnings denied pass. Postflight scans put
strict scoring at 92.9 and objective scoring at 99.5. Five current coverage
findings have direct executable evidence: authentication-attempt invalidation,
typed sync errors, actual CLI prepare output, public state-facade behavior and
SQLite migration rollback. They were classified through the CLI as false positives,
but the next scan reopened unchanged judgments. This is intentional upstream
policy, verified in the [policy report](scanner-false-positive-reopening.md).
Six tooling/generated-directory warnings are now accepted with their strict cost
retained. Coverage findings have not been permanently suppressed. A fresh blind
review is running; repeated mechanical rescans are not additional progress.

## Bound specification revisits

`package/mod.rs`, `ids.rs` and `caption_codec.rs` were inspected together. Their
compatible annotation and error-documentation work was batched with checked
part-count/schema/date conversions. Public wire structures and error enums keep
their existing construction and matching contracts. The scalar associated-data
serializer remains an explicit invariant backed by byte-exact tests. Applying
`non_exhaustive` would restrict callers without fixing a demonstrated defect.

Stable Clippy identities show 40 manual fixes, 16 old locations absent from later
output, and eight retained invariants. The old line-based identities are not
treated as new independent causes. Further visits require a new diagnostic or
behavioral failure, rather than repeating annotations to influence the score.

## Design and convention evidence

The latest design review found independently maintained profile/collection name
normalization. Collections now use the existing profile normalizer and limit;
the related tombstone/rename documentation is corrected and state tests pass.
The convention review found Telegram commands bypassing shutdown on failure.
Post-connect commands now settle their operation and await shutdown, while the
HTTP server binds before connecting; smoke sends retry only flood waits.

These were concrete findings and have code changes and independent review.
Design assessment history (94, 93, 94) and convention history (96, 95, 89) alone
do not establish additional regressions. A blind reassessment should evaluate the
changed owners before more structural work is inferred from score movement.
