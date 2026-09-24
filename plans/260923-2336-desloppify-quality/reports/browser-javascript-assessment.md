# Browser JavaScript assessment

Assessment date: 2026-09-24. Scope: all 52 authored `web/public/**/*.js`
files, including the existing application and watch-state edits. No application
code or finding dispositions were edited, and no manual score overrides were
used for this assessment.

## State isolation and reproducibility

The initial command ran from `web/`:

```sh
desloppify --lang javascript scan --path public --state .desloppify/browser/state-javascript.json --no-badge
desloppify --lang javascript next --state .desloppify/browser/state-javascript.json
```

Scan correctly created a sibling browser plan, but `next` loaded the default
TypeScript plan. The TypeScript state, plan, and config hashes were unchanged at
that point. The original scan and its evidence remain under
`web/.desloppify/browser/`; they are not the operational browser workflow.
The mismatch is reported in upstream [issue #780](https://github.com/peteromallet/desloppify/issues/780).

The operational assessment uses an ignored snapshot:

- Working directory: `web/.desloppify/browser/workspace/`.
- State: `web/.desloppify/browser/workspace/.desloppify/state-javascript.json`.
- Plan: `web/.desloppify/browser/workspace/.desloppify/plan.json`.
- Input manifest: `browser/snapshot-source-sha256.json` (251 source/context files).
- A finding path such as `public/lib/player.js` maps directly to
  `web/public/lib/player.js`; the snapshot's `src/`, `test/`, and `package.json`
  supply supporting contracts and tests.
- Dependencies, build outputs, local state, and credentials were not copied.
  No `.desloppify` directory was created beneath served `web/public/`.

The snapshot used the standard state layout and these commands:

```sh
desloppify --lang javascript scan --path public --no-badge
desloppify --lang javascript next
# next requested a postflight scan; it was performed before reviewing.
desloppify --lang javascript scan --path public --no-badge
desloppify --lang javascript next
desloppify --lang javascript review --path public --run-batches --runner codex --parallel --max-parallel-batches 3 --scan-after-import
```

The first-class runner uses 20 blind dimension batches, with at most three
concurrent reviewers. Its immutable packet and run directory are:

```text
workspace/.desloppify/review_packets/holistic_packet_20260924_000433.json
workspace/.desloppify/subagents/runs/20260924_000433/
```

The blind packet has no prior assessments or target-score context. All snapshot
files and the original 52 browser JavaScript files still match the input hashes.
Other agents changed supporting TypeScript files during the review; the snapshot
is therefore the stable point-in-time context for this baseline.

## Mechanical baseline and limitations

The unpatched scan reports 121 open mechanical findings: 52 orphaned modules,
50 test-coverage findings, 11 empty catches, five structural findings, two
signature findings, and one directory-size finding. Its initial objective score
is 63.6; the initial strict score of 15.9 includes 20 unassessed dimensions and
is not a completed assessment.

The mechanical numbers require these qualifications:

1. **ESLint did not run successfully.** The scanner records
   `tool_failed_no_output`; no ESLint dependency or configuration is present in
   the copied package. This is reduced detector coverage, not a clean lint gate.
2. **The import graph is broken in the installed tool.** Its finder returns
   project-relative keys while its builder compares absolute resolved targets.
   The same 52 files yield zero edges in the installed graph and 126 edges with
   51 imported modules after path normalization. All 52 orphan reports therefore
   need reconsideration; the remaining non-imported `app.js` is the HTML entry
   point. The shared defect is already described by
   [issue #715](https://github.com/peteromallet/desloppify/issues/715).
3. **JavaScript test discovery excludes this project's TypeScript tests.** The
   JavaScript plugin's external test extensions are `.js`, `.jsx`, `.mjs`, and
   `.cjs`. At least 38 `.test.ts` files directly reference `public/lib/`; the
   reported “no test files found” claims do not establish missing test coverage.
   Genuine orchestration gaps still need evidence from the actual tests.
4. The scan's “601 production files” coverage message is a weighted potential
   mislabeled as a file count. The source inventory is 52 files.

No suppressions, exclusions of authored files, or manual score changes were used
to compensate for these limitations.

## Upstream graph repair

[PR #781](https://github.com/peteromallet/desloppify/pull/781) fixes only shared
tree-sitter graph path normalization, preserving graph keys and filtering
unscanned targets. It references #715 without claiming to implement its separate
CommonJS query request. The commit is `6a71566`.

- Six real-parser regressions: five failed before the fix; all pass afterward.
- Relevant shared-language/JavaScript suites: 512 passed, two skipped.
- Full upstream `desloppify/tests/`: 5,814 passed, five skipped, two failures.
  Both failing wrappers invoke
  `TestCmdReviewPrepare::test_do_run_batches_dry_run_generates_packet_and_prompts`
  and expect the existing historical-prompt wording. Both failures reproduce
  unchanged on pristine upstream commit `3a7735d`.
- Scoped Ruff, formatting, diff checks, and independent review passed.
- Read-only verification restores the browser's 126 edges and 51 imported
  modules. The authored Android tree remains at zero resolved edges: Kotlin's
  existing fixed source-root/package-path resolver cannot cover this multi-module
  layout. That separate limitation is not repaired by this patch.

The installed CLI was left unchanged. A patched CLI is retained for handoff at
`/tmp/desloppify-graph-normalization-20260924/.venv/bin/desloppify`; it uses a
temporary upstream checkout and should be integrated with other tool fixes before
later genuine scans. It was not substituted into the ongoing blind review.

Relevant evidence under `web/.desloppify/browser/` includes `snapshot-scan.log`,
`snapshot-postflight-scan.log`, `snapshot-review.log`,
`graph-tests-before.log`, `graph-tests-after.log`,
`graph-integration-tests.log`, `upstream-suite.log`,
`upstream-existing-failures.log`, `authored-graph-verification.json`, and
`source-integrity-after.json`.

## Imported review and next task

All 20 batches completed and all 20 dimension assessments imported successfully.
The trusted import added 27 review findings, with zero resolutions or reopenings.
The stored strict/overall score is 80.0, objective 63.6, and verified strict 63.6.
The initial subjective-zero baseline is not a prior independent assessment, so
this change in score does not represent code improvement.

The review command took 618.50 seconds and exited 1 **after successful import**:
its optional follow-up scan refused to run while the 27-item queue was open.
No forced rescan was used. The import and plan are durable; the operational state
has 148 open work items (121 mechanical plus 27 review findings).

Source-supported behavior findings worth grouping during triage include:

| Identifier suffix | Evidence and affected behavior |
| --- | --- |
| `profile_initialization_response_race` | `watch-state.js` adopts an older `useProfile` response after a newer selection; the reviewer reproduced B as active with A's watchlist. |
| `search_completion_overwrites_current_route` | `app.js` lets pending search success or failure overwrite the DOM after routing elsewhere. |
| `picker_clear_preserves_pending_response` | Clearing collection search leaves an older request eligible to render results. |
| `transcode_startup_lacks_cancellable_ownership` | Pending transcode/HLS startup can attach after closing or replacing the player, and startup failure can leave an acquired session unreleased. |
| `audio_selection_not_applied_to_playback` | Discovered audio selection is reflected in the chooser without updating the actual playback source. |
| `catalog_failure_marks_response_as_applied` | `loadCatalog` caches response text before parsing/building succeeds, causing an identical retry to skip the failed work. |
| `up_next_phase_exit_keeps_countdown` | Leaving the counting phase does not clear the timer, so seeking backward can still advance the episode. |
| `state_mutation_invalidation_split` | Progress/collection writes and shelf invalidation have separate owners, leaving some rendered state stale. |

The remaining findings cover ownership boundaries, API shapes, type/declaration
contracts, naming, and actual orchestration-test gaps. Two findings about creation
response parsing describe the same underlying failure contract in different
dimensions; they should be handled together. Imported review entries are evidence
to validate and triage, not blanket authorization for every proposed refactor.

The actual next command, from the operational snapshot working directory, is:

```sh
desloppify --lang javascript next
```

It selects
`review::.::holistic::api_surface_coherence::grid_mode_argument_shape_drift`.
No fixes or queue resolutions were performed. Future fixes belong in original
`web/` source; refresh the authored snapshot and its hashes before a later scan,
preserving its independent state and plan. Reconcile detector limitations with
the upstream fixes before treating mechanical findings as application defects.

Full imported review inventory (IDs are prefixed by
`review::.::holistic::<dimension>::`):


| Dimension | Identifier |
| --- | --- |
| `cross_module_architecture` | `state_mutation_invalidation_split` |
| `high_level_elegance` | `page_ownership_split_across_app_and_views` |
| `error_consistency` | `create_response_parsing_escapes_failure_contract` |
| `error_consistency` | `collection_search_http_failure_is_empty_success` |
| `error_consistency` | `catalog_failure_marks_response_as_applied` |
| `naming_quality` | `up_next_panel_title_name_collision` |
| `naming_quality` | `adaptive_callback_look_obscures_action` |
| `low_level_elegance` | `interleaved_player_initialization` |
| `low_level_elegance` | `repeated_next_episode_flattening` |
| `mid_level_elegance` | `transcode_startup_lacks_cancellable_ownership` |
| `mid_level_elegance` | `audio_selection_not_applied_to_playback` |
| `test_strategy` | `playback_lifecycle_integration_gap` |
| `test_strategy` | `browser_application_coordination_gap` |
| `api_surface_coherence` | `grid_mode_argument_shape_drift` |
| `incomplete_migration` | `obsolete_watched_string_adapter` |
| `package_organization` | `browser_lib_mixes_feature_ownership` |
| `initialization_coupling` | `player_import_mounts_dom` |
| `initialization_coupling` | `profile_initialization_response_race` |
| `design_coherence` | `player_independent_ui_responsibilities` |
| `contract_coherence` | `first_item_violates_display_order` |
| `contract_coherence` | `creation_writes_escape_silent_failure_contract` |
| `logic_clarity` | `search_completion_overwrites_current_route` |
| `logic_clarity` | `picker_clear_preserves_pending_response` |
| `logic_clarity` | `up_next_phase_exit_keeps_countdown` |
| `type_safety` | `buffer_sample_duration_missing` |
| `type_safety` | `watch_state_shape_stale` |
| `type_safety` | `companion_contracts_drift` |
