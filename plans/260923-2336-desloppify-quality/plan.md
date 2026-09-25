---
title: Desloppify quality improvement
status: in-progress
priority: P1
effort: large
branch: desloppify/quality-20260923
tags: [quality, rust, web, android, desloppify]
created: 2026-09-23
---

# Desloppify quality improvement

Status: in progress; goal ACTIVE. Five of eight milestones below are complete.
Execution detail and finding state live in `.desloppify/`; this checklist is a
delivery checkpoint, not a percentage of findings fixed. Focused Rust, web and Android commits
now exist on the quality branch through 67ed2bce. The ordinary push succeeded
after its required Clippy, Rust, web lint/tests and Android tests/lint hook passed.
The earlier in-progress Android fake adaptation failure is repaired. Verified scanner corrections
were published upstream as PRs #777, #778, #781, #782, and #783.

## Outcome and constraints

Raise the strict score through verified improvements to the Rust, web, and
Android code. Follow `desloppify next`, fix each cause, resolve with evidence,
and repeat scans after completing the active queue. Preserve public contracts,
existing user edits, library data, and credentials. No feature work or score
suppression. Questionable exclusions require user input.

## Scout and review

- Rust workspace: CLI, shared core, metadata provider, and specification crates.
- Web: Bun/TypeScript server and browser JavaScript, with tests under `web/test`.
- Android: Kotlin/Compose with generated UniFFI bindings to the Rust core.
- Follow `docs/code-standards.md`, `docs/system-architecture.md`, and the real
  checks in `scripts/check.sh`. Rust source modules remain under 200 lines.
- Existing uncommitted work changes watch-state refresh and bumps the project
  manifests to 0.40.1; preserve it. The older health branch is already in main.
- Review decision: use narrow, cause-aligned changes from the living queue;
  review larger changes independently before resolving them.

## Phases and acceptance

- [x] Install Desloppify and update its workflow guides; confirm local state ignored.
- [x] Inspect scan exclusions and retain source, tests, and authored build logic.
- [x] Run the root Rust baseline scan and import the initial subjective assessment.
- [x] Fix and resolve the 16 imported Rust review findings with focused checks and independent review.
- [x] Complete broad validation of this checkpoint: formatting, Clippy, Rust tests, rustdoc, web tests, Android tests and lint.
- [ ] Follow the refreshed Rust mechanical findings and verify further fixes.
- [ ] Assess remaining language surfaces separately where supported.
- [ ] Rescan, report score changes and remaining findings, and reconcile processes.

Dependencies: finish each active execution queue before refreshing its assessment;
complete remaining language assessments and resulting fixes before final
validation and delivery. Repeat affected checks and independent review as changes warrant.

Acceptance: fresh scans support the reported scores; resolutions correspond to
actual code changes or independently verified false positives; applicable
tests, lint, formatting, and build checks pass. Report any unverified surface.

## Current execution queue

The sixth Rust reassessment reports 94.8 strict. Its two findings (range API
preconditions and non-idempotent retry tests) and five strategy items are now
fixed/resolved with 1,030 tests, four intentional ignores, formatting, strict
Clippy and rustdoc passing. A postflight mechanical scan is running.

All four seventh TypeScript findings and three fifth browser findings are fixed
and resolved. Independent review, 1,707 web tests, 12,082 assertions across 127
files, TypeScript and ESLint pass. Five focused commits through ca7b5b9c preserve
all 25 tested paths. Server postflight is 93.1 strict; browser postflight is 85.1.
Blind reassessments are running for seven server and three browser dimensions.

All four fifth Android findings are fixed/resolved, including refused-replacement
watch ownership, cache retry UI and diagnostic retention. Independent review and
whole Android tests/lint/app/instrumentation compilation pass. The final executed
task results contain 539 passing tests; unaffected tasks remain up-to-date. Rust
and Android changes are being committed from verified source manifests.

Android's corrected scan reports 88.2 strict but exposes a remaining lint-scope
bug: generic scanning includes an explicitly excluded generated UniFFI file,
although the scoped formatter is correct. A bounded upstream repair and an
independent reconciliation of stale operator-import false positives are running.
No generated source or score state is being changed to hide this problem.

## Checkpoint: 2026-09-24

All 35 Rust review findings across three assessments are fixed and resolved.
The latest batch protects concurrent package publication and login completion,
checks shared provider IDs, preserves command shutdown on errors, clarifies
contracts, and directly exercises exported watch-state behavior. Three additional
coverage findings now have real SQLite migration/facade and CLI parsing tests.
Six further coverage findings have focused tests for blocking API dispatch,
safe error rendering, SQLite device identity, real media survey, Telegram adapter
listing/download loops, and actual CLI prepare reporting. Two existing fixture
suites establish false positives in charset and hostile-JSON coverage reports.
Forty specification diagnostics and one rustdoc warning are corrected; eight
literal-regex/scalar-serialization invariants are explicitly retained as accepted
warnings, with their strict-score cost. The sync round now retains typed private
errors until its single reporting boundary. Public concrete records and the
necessary sync locks remain intact, with advisory penalties retained. The latest
workspace gate passes 969 tests (four intentional ignores), formatting and strict
Clippy. All four strategy tasks have evidence reports and CLI resolutions.
Independent reviews passed. Rust's latest scanned strict
score is **92.9**, pending fresh subjective review; its initial assessed baseline
was **91.6**, and its first reassessment was **93.4**. New review findings, rather
than manual overrides, account for the intervening changes.

The 13 selected stale Rust dimensions were all reviewed. A trusted
`review --import-run ... --allow-partial` replay was necessary because the
importer incorrectly demanded all 20 dimensions; the seven unselected assessments
retain their prior values. This is reported in
[upstream issue #779](https://github.com/peteromallet/desloppify/issues/779).
A separate queue-order inconsistency has a verified exact-ID CLI workaround;
reproduction and a tested correction were added to existing upstream PR #686.
No scores or plan state were manually edited. Exact strategy IDs exposed another
CLI resolution defect; supported wildcard matching completed the resolutions, and
verification was supplied to existing upstream PR #692. Repeated reopening of
unchanged, evidenced false positives is intentional upstream policy. Six generated
or tooling-directory warnings are now accepted with their strict penalties retained;
Rust coverage findings have not been permanently suppressed. The latest full blind Rust assessment and follow-up scan report **93.8** strict
and ten new review findings. All ten are now fixed and resolved: session ownership is captured across
foreground and background revocation, profile-only imports report changes, CLI
process cleanup/surveys/removal guards are tested, and API/dependency/name
contracts are corrected. Independent reviews pass; the final workspace gate
passes 1,010 tests with four intentional ignores, strict Clippy, formatting and
rustdoc. Normal bindings are regenerated and Android compilation passes. Seven
focused follow-up commits through `3f3a212` exactly match the tested checkpoint.
See [commit evidence](reports/git-manager-rust-fourth-commits.md). All six strategic
reconciliation tasks are complete and resolved with evidence. The subsequent
scan reports 93.7 strict; reopened coverage attribution and newly moved lock
advisories are being reconciled against retained behavior tests before review.
The fifth fourteen-dimension blind review is durably imported at **94.5** strict,
with five new findings. All five are fixed and resolved: MP4 bounds, snapshot
documentation, deterministic staging contention checks, sign-out session locking,
and preservation of diagnostic cause chains. Independent review passes and the
broad gate passes 1,019 tests, formatting, strict Clippy and rustdoc. Android reset
review additionally proved late native database work could recreate removed files;
a terminal StateDb retirement fence and regenerated bindings now pass the full
cross-surface gates. Rust passes 1,025 tests (four intentional ignores), formatting,
strict Clippy and rustdoc. Eight commits through 7b6ee469 preserve all 41 tested
Rust/Android paths. The latest scan and independent coverage reconciliation report
94.5 strict; a six-dimension blind reassessment is running.

Web TypeScript's initial assessed strict baseline is **88.5**. All 25 initial
review findings are fixed and resolved, including application startup, catalog
following, shutdown ownership, transcode lifecycle, atomic state import and
coalescing, HTTP boundaries, cache cleanup and thumbnail publication. A thumbnail
module cycle is also fixed. The full suite passed **1,378 tests**. Independent
application lifecycle review and 12 focused tests passed. A fresh 20-dimension
blind review assessed **91.8** strict and added 13 findings. All 13 are now fixed
and resolved: SQLite write failures, HLS filesystem failures, saved identity
validation, channel discovery/protocol ownership, metadata module organization,
and auxiliary media shutdown/ensure contracts. Credential setup now has 23 passing
behavior tests covering authentication, cleanup, credential redaction and real
file permissions. Whole-web TypeScript checking passes after correcting declarations
and fixtures. The latest source-only scan is **91.9** strict before reassessment;
the local browser review snapshot is explicitly excluded from the server scan.
The next full blind review assesses **92.5** strict and adds 14 findings. All are
fixed and resolved, including database/static-file error propagation, HLS delete
origin checks, login logger lifetime, arbitrary refresh rejections, named cache
requests, and small naming/contract simplifications. Ninety focused boundary tests
and 118 cache/login/refresh tests pass; the new regression cases failed beforehand.
Bound-listener media routing, helper ownership and two integration-test gaps are
also complete, with independent reviews and failing mutations. The full web gate
passes **1,556 tests**, 11,320 assertions across 115 files, and TypeScript is clean.
All authored web changes are committed in six focused groups through `48f0a5c`;
the isolated final snapshot passed the same full gate. Scanner commit tracking
records all 52 web review fixes, the thumbnail cycle and 27 browser fixes.
See [commit evidence](reports/git-manager-web-focused-commits.md).
The fourth full blind review and follow-up scan report **92.1** strict and
11 additional review findings. The score decline reflects the new assessment;
no manual score adjustment was made. All eleven are now fixed and resolved,
including disk-only thumbnail reads, cancelled audio probes, safe transcode
cleanup and arbitrary rejection diagnostics. Full verification passes 1,608
tests and TypeScript. Postflight remains 92.1 strict. The thirteen-dimension reassessment is imported
at 92.4 strict with nine findings. All nine are fixed and resolved; independent review and the coordinated full
gate pass: 1,664 tests, 11,896 assertions, 123 files, TypeScript and browser lint. A real PTY regression exposed
password echo under Bun; the public output gate now passes it and a failing
echo-removal mutation. Browser ESLint is configured and passes all authored
JavaScript, with a failing undeclared-name probe confirming active rules.
The sixth eleven-dimension blind review is imported at **92.7** strict with five
new findings. All five are fixed and resolved. HTTP shutdown now awaits complete
stream cleanup before disconnecting the upstream client; filesystem diagnostics
and profile-creation change counts retain their real failure contracts. Independent
review and the complete web gate pass: 1,688 tests, 12,012 assertions across 124
files, TypeScript and browser ESLint. Six focused commits through b05844a3 preserve
all 27 tested web paths. Postflight is 92.7 strict; a three-dimension blind
reassessment is running.

The initial 52 authored browser JavaScript files have a separate blind assessment:
**80.0** strict, with 27 imported review findings. All 27 are fixed and resolved:
truthful contracts, display-order playback, stale profile/collection replies,
search failure handling, cancellable playback ownership, applied audio selection,
title initialization and countdown cleanup. Playback has 34 new lifetime tests
within a passing 116-test focused gate. Page/state coordination, import-safe
player mounting and feature organization are complete. Browser code is grouped
by playback, catalog and status. The latest full Bun run passes **1,521 tests**;
TypeScript checking and the 55-module browser bundle pass. A real static-handler
test verifies the moved module URLs, MIME types and served bytes.
The operational state lives in an ignored snapshot under
`web/.desloppify/browser/workspace/` because the tool's `--state` override selects
the wrong execution plan (upstream #780). Fixes are made in original sources;
the refreshed snapshot records hashes for all 274 authored source/context files.
The postflight browser scan is **80.2** strict and **88.4** objective. The second
blind review reports **82.0** strict and 16 additional findings. All sixteen are now
fixed and resolved in original sources, including state persistence contracts, shelf-mode
fallback, visible management failures, startup codec detection, construction
naming and existing/nullable type declarations. Nine regression cases failed
before these repairs; 103 focused state/catalog/control tests pass. Six player
lifecycle findings have implementation and 153 passing focused tests; independent
source review found no additional defect. Real-markup/subtitle integration and
playback subfeature organization are complete. The complete web gate passes
1,608 tests and TypeScript. The refreshed snapshot has 288 verified hashes; its
postflight is 81.9 strict / 90.8 objective, with auto-resolved penalties retained.
The fourteen-dimension reassessment is imported at 84.9 strict, with eight
new findings. All eight findings are fixed and resolved. Profile discovery retries visibly,
late collection deletion respects the current route, and subtitle toggles restore
the chosen track. System lifecycle integration passes two isolated mutation
checks. Independent review and the complete 1,664-test web gate pass. The refreshed
291-file snapshot includes working ESLint configuration; postflight is 84.8 strict.
A nine-dimension blind reassessment is imported at 84.8 strict with four new
findings: unavailable profile state, playable-set documentation, track-index
naming, and playback option types. All four are implemented in original sources,
with ten failing-before regressions and 121 passing focused tests; independent
review and the final shared 1,688-test web gate pass. All four are resolved and
committed through b05844a3. The refreshed snapshot has 292 verified hashes and
postflight remains 84.8 strict; a four-dimension blind reassessment is running.
Seven focused web commits
through e57625a1 preserve all 292 tested hash entries, with nine TypeScript and
eight browser findings recorded. See [commit evidence](reports/git-manager-web-fifth-commits.md). Upstream PR #781 repairs the shared
relative/absolute import-graph mismatch. JavaScript test discovery initially missed
TypeScript tests; the verified discovery correction is
[upstream PR #783](https://github.com/peteromallet/desloppify/pull/783).
Its real-project check retains all 55 production JavaScript files while adding
117 TypeScript test/context candidates. The first scanner run could not execute ESLint successfully because no
configuration existed; the declared ESLint configuration now passes in original
sources and will be included in the next snapshot. Neither earlier limitation
was evidence that application code was clean or untested. See the
[browser assessment report](reports/browser-javascript-assessment.md).

Android's initial assessment is **84.2** strict. **All 30** review findings are
fixed and resolved. This includes event/sync lifetime, catalog refresh/artwork
ownership, persistence failures, profile retry behavior, login cancellation and
setup ownership, simpler rendering/player state, and explicit API contracts.
The broad gate passed **394 tests**, app compilation and Hilt generation.
UI domain organization and real Activity/Compose playback lifecycle coverage are
complete; the final UI gate passes 106 mobile and 42 player tests, app/TV
compilation, and Hilt generation. Mutation checks prove the lifecycle tests catch
lost save/stop hooks and rotation stopping playback. Two real unused imports were
removed; 47 operator import findings
were verified false positives. Detector and scoped ktlint corrections are
[upstream PR #777](https://github.com/peteromallet/desloppify/pull/777) and
[upstream PR #778](https://github.com/peteromallet/desloppify/pull/778).
Android import-graph coverage remains to be reconciled, so its mechanical score
is not yet reliable. Scoped ktlint formatting is complete and all authored files
have zero reported violations; the full Android tests/lint/compilation/Hilt gate
passes. The Compose naming exception is recorded in `android/.editorconfig`.
The first-item display-order repair also has a failing-before/passing-after Kotlin
regression and passing catalog/player tests. Authored build logic remains in
scope. Latest scan is **84.9** strict before reassessment. A separately tested
Kotlin package/declaration graph correction is
[upstream PR #782](https://github.com/peteromallet/desloppify/pull/782).
It establishes 455 import edges, including 196 test edges, while conservatively
leaving ambiguous implicit calls unresolved. The full blind reassessment added
17 findings. Sixteen are now fixed and resolved: candidate native-resource cleanup,
player mutation feedback, optional metadata failures, enrichment naming/contracts,
typed Kids rendering and direct JVM configuration, retained Settings completions
and cancellation, real navigation/cache integration coverage, and profile package
ownership. Four Settings regressions failed before repair; all 14 cases now pass.
The broad Android unit-test/lint/app/instrumentation-compile gate passes after
these changes. Unused Kotlin package provisioning is retained as accepted debt
with its strict-score penalty because the accepted design explicitly requires
it; the pending user choice may authorize removal later. The review runner accidentally used the stock CLI for its
automatic Kotlin follow-up scan, losing the verified graph/ktlint corrections.
Its 85.1 strict reading is not comparable to the corrected 84.9 baseline. The
corrected postflight now reports **87.2 strict / 87.0 objective**. Ten stale
dimensions were reviewed and durably imported with the supported partial-import
recovery; the unselected ten retain their assessments. Android now reads 87.7
strict. Three new findings cover reset-failure documentation, identity-failure
classification and production setup-flow coverage. The queued mid-level review
found retained login authorization after sign-out. All four findings are now
fixed and resolved, with actual Compose setup/sign-out regressions, five failing
mutations, 176 focused tests and independent review. Three focused commits through
`2c9e0e9` match the tested checkpoint, and the full Android unit-test/lint/app and
instrumentation compilation gate passes. Four earlier commits through `23ac5bf`
record the prior 49 fixes. The four-dimension blind reassessment is imported at 87.7 strict with five new
findings. Native lifecycle documentation and actual cache-factory byte reads are
complete, with focused playback tests and independent review passing. System diagnostics now retains the previous snapshot or shows a sanitized
retry on first-read failure; focused ViewModel/Compose tests and independent
review pass. Retained profile publication/reset races are fixed with delayed
operation regressions, serialized close/delete ownership, and native StateDb
retirement before releasing the core. All five findings are resolved and committed.
Independent review, 364 focused tests and the full Android unit-test/lint/app and
instrumentation-compilation gate pass. A four-dimension blind reassessment is
running. No score state is edited.

The previous broad checkpoint passed: 904 Rust tests (4 intentional live/network ignores),
1,282 web tests, Clippy with warnings denied, formatting, rustdoc, and Android
tests/lint. Android retains 15 pre-existing lint warning classes. The original
formatting baseline failed; formatting is now applied and the 200-line source
gate passes. Independent reviews of the substantial fixes passed. Original
browser edits and manifest version 0.40.1 changes remain preserved. The second Rust batch also passes the full Rust test suite and Clippy; updated Kotlin bindings are generated. Repeat the affected broad gates once Android/web work settles.

Evidence and focused results:
[progress report](reports/pm-260924-0030-rust-review-checkpoint.md).

Rollback: revert only changes introduced by this task, preserving prior edits.
No production database or live Telegram operations are part of verification.
