# Persisted strategy resolution verification

Status: DONE_WITH_CONCERNS

The exact fix already exists in upstream [PR #692](https://github.com/peteromallet/desloppify/pull/692) and [PR #773](https://github.com/peteromallet/desloppify/pull/773), with the reported failure in [issue #747](https://github.com/peteromallet/desloppify/issues/747). Following the parent's direction, no duplicate PR was opened. Independent validation was [posted on #692](https://github.com/peteromallet/desloppify/pull/692#issuecomment-5805526782) under the authorized [Desloppify upstream bug workflow](../../../.agents/skills/desloppify/SKILL.md).

`strategize._create_strategic_work_items` persists `strategy::` records. The old `split_synthetic_patterns` sends those IDs to the workflow resolver, which only purges plan entries and prints success. PR #692 excludes strategy IDs from this local plan-only routing while leaving the global synthetic-prefix definition unchanged. The ordinary resolver then enforces note, attestation and queue checks and persists the finding status.

Added nine independent CLI regressions in `/tmp/desloppify-strategy-resolution-20260924/desloppify/tests/commands/plan/test_strategy_resolution_cli.py`. They use the actual strategist creation helper, temporary state/plan files, real subprocess CLI commands, and reload persisted records after `next`. No orchestration or resolver is mocked.

| Case | Baseline | PR #692 |
| --- | --- | --- |
| Exact strategy ID persists fixed, note and attestation; `next` does not reinsert | Fails | Passes |
| `*::ownership-audit` wildcard equivalence | Passes | Passes |
| `strategy::*` wildcard | Fails | Passes |
| Mixed workflow + strategy resolution | Fails | Passes |
| Too-short note rejected | Fails | Passes |
| Missing attestation rejected | Fails | Passes |
| Later exact strategy ID blocked by queue order | Fails | Passes |
| Unknown strategy ID does not claim success | Fails | Passes |
| Front cluster-name resolution | Separate queue-guard failure | Same failure |

The last case reproduces the previously reviewed queue-guard defect discussed on [PR #686](https://github.com/peteromallet/desloppify/pull/686#issuecomment-5805008600): a derived `workflow::run-scan` outranks the persisted front cluster. Combining PR #692 with that focused, previously reviewed queue-prefix patch makes all nine CLI cases pass. This separate fix is not attributed to PR #692.

Validation:

- Baseline CLI fixture suite: 8 failed, 1 passed; PR #692 alone: 8 passed, 1 unchanged cluster failure; combined: 9 passed.
- Existing nearby resolve, workflow gates, strategist and queue-guard tests: 84 passed.
- Full combined upstream suite: **6829 passed, 18 skipped, 2 failed**. The two historical duplicated `test_do_run_batches_dry_run_generates_packet_and_prompts` failures were independently reproduced on pristine `3a7735d` during the preceding Kotlin verification.
- Independent parent review confirmed the real CLI fixtures and guard assertions.
- Ruff and whitespace checks pass. Logs: `/tmp/strategy-resolution-cli-{before,after,combined}.log`, `/tmp/strategy-resolution-nearby.log`, `/tmp/strategy-resolution-combined-full.log`.

No project source, scanner state, installed tooling or git commits were changed. PR #692's existing patch and the added tests remain uncommitted in the isolated verification checkout; the combined check is `/tmp/desloppify-strategy-queue-combined-20260924`. These are retained for review. All owned CLI/test subprocesses exited; no server was started. The parent's successful wildcard recovery remains the supported project workaround pending upstream merge.
