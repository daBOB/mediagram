# JavaScript discovery of TypeScript tests

Status: DONE_WITH_CONCERNS

Published [upstream PR #783](https://github.com/peteromallet/desloppify/pull/783), focused commit `b3f272a01ec2c7e674afbae2381c7ba166fbffcf`, under the authorized [Desloppify upstream bug workflow](../../../.agents/skills/desloppify/SKILL.md). An upstream search found no existing patch for this defect. No application source, scanner state, assessments, installed CLI or original combined checkout was changed.

## Cause and repair

JavaScript registration did not set `test_file_extensions`, so it inherited only the JavaScript production extensions. The supplemental test finder therefore missed the browser project's TypeScript tests. A second boundary affected project-root scans: the helper skipped test directories inside the scan path even when their test extensions were absent from production discovery.

The focused patch adds `.ts`, `.tsx`, `.mts` and `.cts` to JavaScript test extensions while retaining `.js`, `.jsx`, `.mjs` and `.cjs` as the production scope. For test directories inside the scanned path, the shared helper now collects only extensions omitted by the production finder. Already-scanned JavaScript tests are not collected twice. Import resolution and static test-mapping heuristics are unchanged.

Four upstream files changed: the JavaScript registration, shared phase helper, a new real-phase regression matrix, and one existing mock fixture's missing production-extension field. Independent parent source review passed for all four.

## Evidence

- Initial external TypeScript cases: **8 failed, 8 JavaScript controls passed** before registration changed.
- Project-root TypeScript cases: **8 failed, 24 passed** before the shared-helper correction.
- Final matrix: **32 passed**, covering eight test extensions and four scan modes (relative/absolute subdirectory and project root). Each case exercises production discovery and the real test-mapping phase, verifies JavaScript-only production scope, keeps TypeScript context excluded, and retains a finding for unrelated untested JavaScript.
- Focused new/existing discovery tests: **37 passed**. JavaScript/shared framework suite: **538 passed, 2 skipped**. Registry reset-order regression: **42 passed** at that checkpoint.
- Full combined suite: **6932 passed, 23 skipped, 2 failed**. Both failures are the duplicated historical `test_do_run_batches_dry_run_generates_packet_and_prompts` cases independently reproduced on pristine base `3a7735d` during prior verification.
- Ruff and whitespace checks pass. The broad suite caught an existing test-order leak that clears language hooks but restores cached configs; the new integration fixture initializes the real JavaScript plugin for each case. No production workaround was added for that test isolation problem.

Read-only validation used `web/.desloppify/browser/workspace` with PR #781 path normalization held constant:

| Observation | Before | After |
| --- | ---: | ---: |
| JavaScript production files | 55 | 55 |
| Production dependency edges | 132 | 132 |
| External TypeScript candidates | 0 | 117 |
| Test-gap findings | 50 | 17 |

The 117 candidates contain 111 named tests and six support files. Remaining findings comprise 12 untested-module and five transitive-only candidates. Source and scanner JSON hashes remained identical during validation. These counts establish corrected discovery, not that every remaining finding is genuine or every behavior is tested.

Evidence logs: `/tmp/js-ts-test-discovery-{red,root-red,focused,nearby,registry-order}.log`, `/tmp/js-test-discovery-combined-full.log`, `/tmp/js-test-discovery-browser-{before,after}.json` and matching phase logs.

## Combined CLI and limits

**Executable:** `/tmp/desloppify-browser-tests-combined`.

It preserves the calling directory and uses the independent checkout `/tmp/desloppify-js-test-combined-20260924`, commit `798cf3f`, containing verified PRs #777, #778, #781, #782 and #783. The focused PR checkout is `/tmp/desloppify-js-test-discovery-20260924`. The existing `/tmp/desloppify-kotlin-combined` wrapper and its checkout remain untouched. The new wrapper's CLI startup was verified; parent owns the browser rescan.

Discovery still uses configured test directories. Colocated TypeScript tests elsewhere, runtime-generated bundles/import strings, query-suffixed imports and CommonJS mappings retain their existing limitations. The remaining browser application/player findings therefore need source-level adjudication. No scores or dispositions were overridden.

All owned pytest/CLI processes exited. The two new checkouts and wrapper remain available for the parent's rescan; no server was started, and unrelated root review/scan processes were left running.
