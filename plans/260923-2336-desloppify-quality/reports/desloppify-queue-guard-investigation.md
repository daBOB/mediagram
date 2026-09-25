# Desloppify queue guard investigation

Status: DONE_WITH_CONCERNS. Investigation and isolated fix verified; upstream already has the same repair in PR #686, so no duplicate PR was opened.

## Cause and recovery

The resolve guard uses a default subjective queue threshold of 100, while `next` uses the configured strict threshold (85 here). A stale dimension at 87 therefore appears only to the guard. Cluster expansion also adds the cluster name to its live issue IDs, excluding the cluster from the existing persisted-prefix allowance for exact IDs.

Supported recovery, verified on copied state: reorder the cluster to the front, then resolve its exact live issue IDs with the usual note and confirmation. No force flag, score override, or JSON editing is required. The lead agent owns any application of this recovery to the project.

## Verification

- Isolated checkout: `/tmp/desloppify-queue-guard-RIE8kF`, branch `fix/resolve-queue-target`, based on upstream `3a7735d531a96b6a226bfbdc9fd662b14195f857`. Only the queue guard and its tests differ. Retained uncommitted at the lead agent's request.
- Three new cases failed before the fix; all 20 queue-guard tests pass after. The real execution queue/next path covers targets 85/90, one- and two-member front clusters, fixed/missing members, and later/noncontiguous member rejection.
- The installed CLI rejects the front cluster on an original-state copy; the changed CLI resolves exactly its one intended issue on an equivalent copy. The project's original state and scores were untouched.
- Ruff on both changed files and `git diff --check`: pass.
- Full upstream core suite: **5,814 passed, 5 skipped, 2 failed**. Both failures are existing review prompt assertions in `TestCmdReviewPrepare::test_do_run_batches_dry_run_generates_packet_and_prompts` (normal and integration collections); both reproduced with the original queue guard restored.
- The lead agent independently reviewed the production diff and tests and approved them. No test runner remains active.

Logs: `/tmp/desloppify-queue-guard-full.log`, `/tmp/desloppify-queue-baseline-failures.log`, `/tmp/desloppify-queue-verify-before.log`, `/tmp/desloppify-queue-verify-after.log`.

## Upstream follow-through

Existing [PR #686](https://github.com/peteromallet/desloppify/pull/686) already contains threshold propagation and cluster-prefix matching inside a broader change. Posted [reproduction and validation evidence](https://github.com/peteromallet/desloppify/pull/686#issuecomment-5805008600), with no project state or source attachments. This is separate from issues #701 (unrelated action-step completion) and #779 (selected-dimension import).

The comment was authorized by the lead agent and the [Desloppify skill's upstream issue workflow](../../../../.agents/skills/desloppify/SKILL.md). No product code, project scanner state, commits, or installed CLI changes were made in this task.

For browser JavaScript assessment isolation, `--state PATH` is supported by scan/review/plan/next/status and changes the state-file location. It does not relocate plan/config/artifacts. No `--state-dir` option was found.
