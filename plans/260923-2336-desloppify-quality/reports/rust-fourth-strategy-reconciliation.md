# Fourth Rust strategy reconciliation

Status: DONE

This report supplies evidence for the six open `strategy::` tasks. It does not
resolve them, reassess subjective dimensions, change dispositions, or claim the
overall quality goal is complete. Only this report was written.

The snapshot is Rust scan **12**, last scanned **2026-09-24 01:34:16 UTC**, with
subsequent review resolutions through 02:27:29 and plan commit records through
**02:35:42 UTC**. The scan predates the fourth repair batch; passing current tests
and resolved review records do not make its mechanical inventory a fresh scan.
Sources are the read-only [Rust state](../../../.desloppify/state-rust.json),
[plan](../../../.desloppify/plan.json), current source, existing logs, and linked
implementation/review reports. Earlier evidence is retained in the
[previous reconciliation](rust-strategy-reconciliation.md).

## Six strategy acceptance themes

| Strategy suffix | Dimensions and owner | Evidence and present result |
| --- | --- | --- |
| `reconcile-triage-evidence` | Test health, Code quality, Test strategy; controller/accounting | The inventory, score modes, exact denominators, disposition reconciliation and commit aggregation below account for the current records without converting absence into a repair. |
| `complete-revocation-boundaries` | Auth consistency, Mid elegance; core account/events/channel/read/state-sync owners | All automatic revocation callers carry their originating connection. Current 401 resets auth and its persisted key; late 401 from an older connection preserves a replacement login. Upload errors retain the wrapped RPC cause. Subscription, listener opening/waiting, state document list/download/upload/edit/send/pin, account, library listing, pinned/marker index search, index download and playback use the shared boundary. |
| `close-failure-lifecycle-gaps` | Error consistency, Init coupling, Test strategy; CLI media/survey and core listener owners | Probe failures remain contextual unknown compatibility outcomes requiring confirmation. Ffmpeg read failure/cancellation terminates its child; read errors survive cleanup failure. A dropped or incompletely opened listener releases only its matching consumed connection, allowing the actual subscription/open sequence to run again. Concurrent revocation wakes an active listener without a lock cycle. |
| `target-orchestration-test-debt` | Test health, Test strategy; CLI removal/media and core session owners | Real production removal guards and remote-before-local deletion are exercised, including partial failure. Actual process and connection-lifecycle regressions accompany the repairs. Four CLI mutation groups failed as intended; five reopened attribution warnings are matched to retained behavior tests below. The single coverage `wontfix` was reconsidered individually. |
| `stabilize-public-contracts` | API coherence, Contracts; core API/state and CLI upload owners | Read documentation now describes clamping, EOF rejection, in-range empty reads before channel lookup, and operational failure. Profile creation contributes to committed import counts once; normalized repeat imports contribute zero, including retry after failed publication. Public `profile_named` remains compatible. Upload helpers have descriptive canonical names with deprecated direct aliases preserving old paths and `Document` identity. |
| `control-permanent-dispositions` | Test health, Code quality; controller and affected domain maintainers | Existing contract, coverage and lock choices retain source/test evidence and explicit reconsideration triggers below. No new `wontfix`, suppression or general disposition policy is introduced. Historical records and present plan skips are separate populations. |

The current state has **45 fixed review findings and no open review findings**.
The ten newest closures are accounted for as follows; these are verified repairs
or contract corrections, not inferred fixes from score movement:

| Review finding suffixes | Owning source and review evidence |
| --- | --- |
| `revocation_handling_partial_adoption`, `sync_upload_bypasses_revocation_boundary`, `listener_reset_retains_consumed_client` | [Shared identity-aware revocation](../../../crates/mediagram-core/src/api/account/revoked.rs), [listener ownership](../../../crates/mediagram-core/src/api/events/listener.rs), [state publication](../../../crates/mediagram-core/src/api/state_sync/publish.rs); [implementation](rust-session-lifecycle-reassessment.md) and [independent review](rust-session-lifecycle-independent-review.md). |
| `compatibility_survey_discards_probe_failures`, `ffmpeg_progress_failure_skips_child_cleanup`, `removal_orchestration_missing_regression_tests` | [Survey](../../../crates/mediagram/src/commands/add_show/survey.rs), [ffmpeg](../../../crates/mediagram/src/media/ffmpeg_progress.rs), [command guards](../../../crates/mediagram/src/commands/remove.rs), [removal ordering](../../../crates/mediagram/src/remove/apply.rs); [failure reassessment](rust-cli-failure-reassessment.md) and [independent review](rust-fourth-contract-cli-independent-review.md). |
| `playback_read_missing_boundary_contract`, `profile_import_missing_change_count` | [Public read contract](../../../crates/mediagram-core/src/api/mod.rs), [read implementation](../../../crates/mediagram-core/src/api/read.rs), [profile lookup](../../../crates/mediagram-core/src/state/profiles.rs), [transactional import](../../../crates/mediagram-core/src/state/exchange.rs); [contract review](rust-contract-dependency-review.md) and [final independent review](rust-fourth-contract-cli-independent-review.md). |
| `upload_plan_names_hide_persistence`, `host_core_unused_sqlite_session_feature` | [Deprecated upload aliases](../../../crates/mediagram/src/upload/mod.rs), [core dependency ownership](../../../crates/mediagram-core/Cargo.toml); the same final independent review verifies legacy compilation and distinct core/CLI feature trees. Normal regenerated Android bindings now compile; the earlier pending regeneration concern is closed. |

## Open inventory and ownership

The state contains **411 records**: 53 open, 118 fixed, 134 wontfix, 104
auto_resolved and 2 false_positive. These status totals include workflow/review
records, so “118 fixed” is not a count of 118 source repairs. The 53 open records
are six strategies above plus the following **47** mechanical/advisory records.
Owners here identify the source boundary responsible for reconsideration, not a
new assignment or disposition.

| Open detector | Count | Dimension/accounting and source owner |
| --- | ---: | --- |
| `structural` | 20 | File health policy, all test-zoned and excluded from that score. CLI integration/verification/index tests and core sync/list/refresh/publication/transport/auth/API/migration tests own these size/complexity advisories. Production's 200-line gate is a separate requirement. |
| `flat_dirs` | 2 | Code quality, currently production-zoned: `crates/mediagram/tests` and `crates/mediagram-core/src`. CLI test navigation and core module layout owners. The former being a test directory does not override its stored detector zone. |
| `responsibility_cohesion` | 1 | Code quality policy, test-zoned/excluded: `mlib-spec/tests/package_format.rs`; package contract-test owner. |
| `signature` | 11 | Queue-visible, excluded from detector scoring. Eight production records span events `open`/`next`, metadata `lesson`, channel-library `read`, status `count`, transport `stream`/`resolve`, index `download`; three test records concern `get_json`, `row`, `rpc`. These are unrelated domain names, pending individual adjudication. |
| `smells` | 5 | Code quality: three scored production `allow_attr` records in TMDB client, CLI main and upload transport; two excluded test-only string-error records in sync and verification tests. Respective crate/test owners. |
| `test_coverage` | 5 | Test health: prepare report, public state facade, state schema, auth attempt and sync error. Exact existing behavior evidence appears below; all remain open. |
| `boilerplate_duplication` | 1 | Duplication: eight-line block attributed to core `api/channel/library.rs` and `state/sync/device.rs`; library/device owners, pending current-source comparison. |
| `stale_exclude` | 2 | Code quality detector, but no `stale_exclude` potential in this stored dimension, hence no present score contribution. `.impeccable/review` and `.impeccable/mocks`; controller/tool-artifact owner. This report does not remove or suppress either. |

## Scores and the strict Test health denominator

These are **stored accounting values**, not a new quality assessment. Read-only
in-memory evaluation of the active combined tool's aggregation reproduces all
four totals exactly:

| Stored field | Value | Meaning in this tool version |
| --- | ---: | --- |
| `objective_score` | 99.7 | Weighted mechanical dimensions using lenient status accounting. |
| `overall_score` | 94.6 | Weighted mechanical and imported subjective dimensions using lenient accounting. |
| `strict_score` | 93.8 | The same mixed dimension pools, with strict mechanical status accounting. |
| `verified_strict_score` | 98.2 | Mechanical dimensions only, using the separate verified-strict status policy. It is not a stricter version of the 94.6 mixed score. |

The source is the active combined checkout's
`engine/_scoring/{state_integration.py,detection.py,policy/core.py}` under
`/tmp/desloppify-js-test-combined-20260924`. Lenient counts open/deferred/triaged-out
records; strict additionally counts `wontfix` and `auto_resolved`.
Verified-strict counts open/wontfix/fixed/false-positive/deferred/triaged-out and
excludes `auto_resolved`. These status sets explain why verified-strict can be
higher than strict; neither its label nor a scan-verification attestation is
evidence that every manually closed issue was repaired.

The stored Test health values are **98.2 lenient / 84.7 strict / 95.2
verified-strict**. Its denominator is **2439**, consisting of:

- **2003** test-coverage potential: rounded sum of `min(sqrt(LOC), 50)` for
  scorable production files, calculated by the detector's `discovery.py`.
- **218** doctest checks and **218** thread-safety checks, both with no failures.

This is neither 2439 tests nor a measured line/branch-coverage percentage. The
stored `failing: 5` is the lenient count. Recomputing detector statistics in memory
from the read-only records gives:

| Coverage record status | Records | Failure weight | Counted by |
| --- | ---: | ---: | --- |
| Open | 5 | 45.005654 | All three modes |
| Wontfix | 1 | 6.000000 | Strict and verified-strict |
| Auto-resolved | 33 | 322.198926 | Strict |
| Fixed | 6 | 54.190006 | Verified-strict |
| False positive | 2 | 11.429967 | Verified-strict |

Thus strict counts **39 coverage records**, weight **373.204580**:
`100 × (2439 − 373.204580) / 2439 = 84.7` after rounding. Verified-strict counts
14, weight 116.625627, producing 95.2. The 13.5-point displayed lenient/strict
gap principally comes from the 33 auto-resolved records, not absence of the
current 1010-test suite.

Of those 33 auto-resolved coverage records, **27 paths still exist and six do
not**: old `api/auth.rs`, `commands/add_document.rs`, core `document.rs`, core
`shows.rs`, `commands/prepare.rs` and `commands/export_session.rs`. File movement,
detector changes and missing output do not by themselves prove coverage fixes.
Their strict penalties and recorded statuses remain intact. Any future manual
closure needs an individual old-to-current source/test mapping; this report does
not bulk promote them to fixed. The same caution applies to all 104 auto-resolved
records.

Current imported scores relevant to the strategy are Auth consistency 88, Mid
elegance 89, Error consistency 88, Init coupling 90, Test strategy 89, API
coherence 91 and Contracts 91. Closing findings does not establish a fresh blind
assessment or demonstrate an improvement in those dimensions.

## Five reopened coverage warnings: actual prior evidence

Each finding still has `status: open`, `kind: transitive_only`; the first three
have reopened three times, the latter two twice. Re-emitted false positives
intentionally reopen under the tool's policy; see the independently reproduced
[scanner policy report](scanner-false-positive-reopening.md). This report neither
adds wrapper-mirroring tests nor proposes permanent coverage suppression.

| Flagged production source | Existing tests, exercised behavior and final-run confirmation |
| --- | --- |
| [prepare/report.rs](../../../crates/mediagram/src/commands/prepare/report.rs) | [prepare_report.rs](../../../crates/mediagram/tests/prepare_report.rs): `dry_run_reports_each_verdict_and_preserves_original_bytes` and `mp4_dry_run_warns_about_unfixable_video_but_not_wrapper_or_audio` execute the compiled CLI and real ffmpeg/ffprobe fixtures. They cover all four verdicts, output columns/summary, Unicode truncation, codec warning filtering/deduplication and unchanged input/no output artifacts. Both pass at final log lines 623–624. |
| [api/state.rs](../../../crates/mediagram-core/src/api/state.rs) | [api_surface.rs](../../../crates/mediagram-core/tests/api_surface.rs): `watch_state_is_profile_scoped_except_kids_and_survives_reopening`, `a_collection_can_only_be_changed_by_its_owner`, `unavailable_state_storage_returns_safe_defaults_and_can_be_retried`. Exported `Core` methods operate on real temporary SQLite, verifying two profiles, global Kids, collection ownership, persistent reopening and obstruction/retry defaults. All pass at final log lines 1138–1141. |
| [state/schema.rs](../../../crates/mediagram-core/src/state/schema.rs) | [migration_tests.rs](../../../crates/mediagram-core/src/state/migration_tests.rs): `populated_v1_rows_and_collection_order_survive_migration_and_reopening` and `a_later_migration_conflict_rolls_back_earlier_schema_changes_and_version`. Actual populated v1 data survives; timestamps backfill; a later conflicting ALTER rolls back earlier ALTERs and `user_version`, retains rows and allows retry. Both pass at final log lines 1057 and 1070. |
| [account/auth_attempt.rs](../../../crates/mediagram-core/src/api/account/auth_attempt.rs) | [auth_tests.rs](../../../crates/mediagram-core/src/api/account/auth_tests.rs): `password_success_after_sign_out_does_not_persist_or_panic`, `refused_password_after_sign_out_cannot_restore_the_password_step`, `an_old_client_cannot_persist_over_a_replacement_even_with_the_same_attempt_marker`, `every_login_result_is_checked_before_it_can_be_interpreted`, plus new-code/refusal retry cases. They execute the actual completion boundary with delayed responses and distinct persisted keys, including the current-success counterpart. Named cases pass at final log lines 931–988. |
| [state/sync/error.rs](../../../crates/mediagram-core/src/state/sync/error.rs) | [sync_tests.rs](../../../crates/mediagram-core/src/state/sync_tests.rs), `typed_failures::channel_causes_survive_the_private_round_without_being_formatted` and `typed_failures::inaccessible_storage_retains_distinct_import_and_read_messages`. Actual `round`/`once` retain a borrowed non-Debug channel error without formatting until the public boundary; real inaccessible storage preserves distinct messages, prevents send and retains the occupying file. Both pass at final log lines 1077 and 1094. The unforceable concrete serde failure is not falsely claimed as dynamically exercised. |

All log references above refer to `/tmp/rust-fourth-workspace-tests.log`.
Earlier scoped evidence remains in
[state adapter/prepare coverage](worker-260924-state-adapter-prepare-coverage.md)
and [typed sync failures](worker-260924-sync-typed-errors.md). These tests support
the disputed attribution; they do not prove every possible branch is covered.

New fourth-batch orchestration coverage is independently stronger than counting
tests: [removal tests](../../../crates/mediagram/src/remove/apply_tests.rs) verify
100/100/5 remote batches precede local deletion and partial failure retains
recovery rows; [command tests](../../../crates/mediagram/src/commands/remove_tests.rs)
exercise dry-run/confirmation/all-set validation before connection.
[Ffmpeg process tests](../../../crates/mediagram/src/media/ffmpeg_progress_tests.rs)
assert a real child's PID is reaped after invalid progress or cancellation, retain
non-UTF8 failure diagnostics and drain more than a pipe buffer.
[Listener lifecycle tests](../../../crates/mediagram-core/src/api/events_lifecycle_tests.rs)
exercise actual pool shutdown/reopening, held-listener revocation, and late
subscription/open/wait 401 after a replacement login. Foreground boundary
matrices cover current 401, ordinary failure and stale 401. Their red/green and
mutation evidence is in the implementation/review reports above.

## Permanent disposition reconciliation and reconsideration triggers

**134 wontfix is not 112 plan skips.** The populations reconcile exactly:

| Population | Count | Relationship |
| --- | ---: | --- |
| Current permanent plan skips also in state as wontfix | 110 | 95 future-proofing + 7 boilerplate + 2 directory layout + 1 coverage + 2 error-boundary + 3 locking advisories. |
| Historical state wontfix absent from plan skips | 24 | 18 line-keyed Clippy records and six tooling/generated-directory exclusion records. Their current notes say they remain absent after manual wontfix. |
| Total state wontfix | **134** | 110 + 24. Line-keyed warning history must not be counted as 18 distinct newly accepted source defects. |
| Current false-positive plan skips | 2 | Package charset and hostile JSON helper coverage, also false_positive in state. |
| Total plan skips | **112** | 110 permanent + 2 false positive. |

All **112** skip objects have `reason: null`, but **112 nonempty notes and 112
attestations**. The structured reason field is unpopulated; the rationale is not
missing. The state merger can replace a finding's note with “Still absent…” or
“Reopened…”, so the plan skip notes and historical reports are necessary audit
evidence. The two current false positives are distinct from the five reopened
warnings above.

The highest-impact retained decisions were reconsidered against current source:

| Existing decision | Current evidence and limit | Reconsider when |
| --- | --- | --- |
| 95 concrete public-shape/future-proofing advisories | Rust callers construct/match public media/package/state values; UniFFI and wire shapes are concrete contracts. Blanket `non_exhaustive` would restrict consumers without making Kotlin/wire schemas extensible. Public contract tests, deprecated upload aliases and normal regenerated bindings preserve compatibility. This is retained advisory cost, not proof that every future evolution is safe. | A real extension/versioning requirement or caller breakage requires changing one identified public shape; assess that type and migration explicitly. |
| Package naming coverage wontfix | [package_format.rs](../../../crates/mlib-spec/tests/package_format.rs), tests beginning `package_file_name_`, directly exercise [the production function](../../../crates/mlib-spec/src/package/naming.rs): digest selection, same-day content, epoch/pre-epoch, day/millennium/leap-century boundaries and i64 extremes. Retained weight is 6. | Naming/date/hash behavior changes or a demonstrated input path lacks a production assertion. |
| Charset and hostile JSON coverage false positives | Package-format malformed key/digest fixtures, [shared parser fixtures](../../../crates/mediagram-core/tests/shared_watch_state_fixtures.rs) and [list-record tests](../../../crates/mediagram-core/src/state/record/list_record_tests.rs) exercise invalid types, numeric/string/coercion boundaries, missing keys and surviving valid neighbors. Existing evidence is retained; helpers are not duplicated just to create an import edge. | A helper gains a branch not reached by parser/package fixtures, or a fixture no longer reaches the helper. |
| Event stream async lock | [listener.rs](../../../crates/mediagram-core/src/api/events/listener.rs) serializes one mutable UpdateStream. [revoked.rs](../../../crates/mediagram-core/src/api/account/revoked.rs) uses identity-aware nonblocking idle cleanup; held-listener revocation and reopening tests pass. The historical skip's “only acquisition is next” wording predates these added cleanup paths; its serialization rationale survives, while that old access inventory must not be reused literally. | New lock acquisition/order, multiple independent stream consumers, a failed cancellation/reinitialization test, or measured contention blocks unrelated work. |
| Whole sync-round async lock | [serialized](../../../crates/mediagram-core/src/state/sync.rs) owns the pull/merge/publish memo across channel awaits. `concurrent_rounds_use_the_production_guard_and_send_once` runs this exact function with a gated channel. Releasing early would permit duplicate initial publication. | Memo/ownership semantics change, another lock is acquired in the reverse order, or real workload requires independent-library concurrency. |
| Test-only transport mutex | [ScriptedIo::resolve](../../../crates/mediagram-core/src/transport/fetch_tests.rs) performs a bounded in-memory vector push; no await or IO occurs while guarded. Production transport uses the real IO boundary. | The fixture grows blocking IO/awaits under that guard, or this implementation enters production. |
| CLI error-boundary and literal spec invariants | Optional course sidecars remain best-effort; ffmpeg prerequisite failure cannot silently count unrun media tests as success. Literal regex compilation and scalar serialization are documented invariants with byte/filename tests; see [diagnostic review](spec-diagnostic-disposition.md). Historical warning-line drift does not create new fixes. | Sidecars become required, recoverable library callers need typed errors, patterns become input-driven, or serializer data becomes fallible. |

No new or retired wontfix disposition is recorded by this report. For the next
comparable scan, compare IDs/statuses and their existing rationale separately
from score totals; changed paths, line-key churn and auto-resolution require
evidence before being described as retired debt. The original six exclusion
choices concern dependencies/generated/tooling artifacts, not production code
coverage; their historical records remain separate from the two open exclusions.

## Commit aggregation and final gates

The initial plan `commit_log` entry at **55f8ab2** records **80 finding IDs** and
explicitly describes a checkpoint across **15 actual commits**. Read-only
`git log df88713..55f8ab2` confirms 15. One tracking row does not mean one source
commit or 80 separate commits; see the [focused commit report](git-manager-260924-rust-focused-commits.md).

The completed fourth batch adds **seven actual commits** from `446c8d0` through
**3f3a212**, as documented in the [fourth commit report](git-manager-rust-fourth-commits.md).
The latest plan now has two entries at that same endpoint: one finding plus the
remaining nine, **10 unique findings**, not two additional implementation
batches. Across all three tracking rows there are **90 unique finding IDs**.
The split is a recording-command correction, not missing work or duplicate
credit. The 15 + 7 counts refer to these Rust deliveries; intervening Android and
later web commits are not silently included.

Existing final gate logs were read, not rerun:

- `/tmp/rust-fourth-workspace-tests.log`: independently summed **1010 passed,
  zero failed, four intentional ignores** across 93 result groups. Ignored cases
  are real Telegram upload, two live TMDB cases and a real network TLS handshake.
- `/tmp/rust-fourth-workspace-clippy.log`, `rust-fourth-workspace-doc.log` and
  `rust-fourth-format.log`: strict workspace Clippy, rustdoc and formatting pass,
  consistent with the controller's recorded successful exits.
- `/tmp/rust-fourth-android-bindings.log`: both shipped native targets built and
  normal UniFFI generation completed; `/tmp/android-regenerated-binding-gate.log`
  reports successful app/instrumentation Kotlin compilation. Existing Compose
  configuration/opt-in warnings remain visible.
- Session and CLI/public-contract independent reviews pass. New profile tests
  `importing_an_empty_profile_counts_its_creation_only_once` and
  `an_empty_remote_profile_reports_a_change_to_refresh_the_picker` include
  normalized repetition and failed-first-publication retry in this final run.

No test process, service or daemon was started for this reconciliation. No live
service or user database was accessed. The controller owns any subsequent scan,
blind reassessment and strategy resolution. For the installed strategy-routing
defect, the previously verified explicit suffix wildcard workaround persists
real findings through normal guards; the [upstream verification](desloppify-strategy-resolution-verification.md)
explains why an exact `strategy::` ID's printed success is insufficient. Verify
persisted status after the controller's resolution, rather than editing JSON.

Concerns/Blockers: no blocker to this evidence reconciliation. The mechanical
inventory predates the completed repair batch, five coverage attribution warnings
remain open by design, and future scan/review results are not predicted here.
