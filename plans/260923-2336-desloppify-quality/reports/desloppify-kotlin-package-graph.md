# Kotlin package graph repair

Status: DONE_WITH_CONCERNS

Published [upstream PR #782](https://github.com/peteromallet/desloppify/pull/782), commit `a71a7fe22e3c091609fa8b61687c38444dd4eaf6`, under the authorized [Desloppify upstream bug workflow](../../../.agents/skills/desloppify/SKILL.md). No application source or scanner state was changed. The installed CLI was not changed.

## Verified cause and scope

The prior resolver maps `data.Record` to a file named `data/Record.kt` below fixed source roots. Android's authored sources use package headers across flat Gradle module directories and often declare several symbols per file. [Kotlin's package/import rules](https://kotlinlang.org/docs/packages.html) permit this. PR #739 discovers more module roots but retains the same filename assumption; PR #782 complements it without including or duplicating that patch.

The fix indexes only the already discovered files' parsed package/declaration ownership. It resolves explicit aliases, top-level functions/properties, operator imports and members through outer type owners. It also infers same-package/star edges for unique bare type/constructor references. Local names, type parameters and explicit bindings suppress inference; private declarations and ambiguous owners are not credited across files. Paths retain the discovered graph keys. No additional source discovery, blanket package edges, hardcoded project roots or score overrides were introduced.

On the same **238 discovered files**, PR #781's normalized graph still had **0 edges**. The package patch yields **455 edges**, **95 imported files**, and **196 edges originating in `/test/` files**. Source hashes are unchanged. Recovered direct examples:

- `core/data/src/test/kotlin/WatchStateRepositoryTest.kt` → `core/data/src/main/kotlin/WatchStateRepository.kt`.
- `feature/catalog/src/test/kotlin/CatalogViewModelTest.kt` → `feature/catalog/src/main/kotlin/CatalogViewModel.kt`.

## Validation

- Fifteen real parser regressions pass. Five initial cases failed before the implementation. A further ambiguous-star fallback test failed before its targeted correction.
- Shared parser tests at the initial eight-case checkpoint: 468 passed, 2 skipped.
- Independent parent source review passed before publication.
- Combined upstream suite with PRs #777, #778, #781 and #782: **6900 passed, 23 skipped, 2 failed**. Both failures are the duplicated `test_do_run_batches_dry_run_generates_packet_and_prompts` historical prompt-contract cases. Both reproduce on pristine base `3a7735d` (2 failed).
- Ruff and whitespace checks pass; publication and combined worktrees are clean.
- Actual CLI `scan` then `next` completed on a disposable copy of the same authored inventory/configuration. Zones remain 136 production / 88 test / 14 config. The graph feeds the normal pipeline; it reports 53 orphan candidates and 88 test-gap findings. These are candidates, not a blanket adjudication. The displayed “1223 production files” denominator is weighted potential, not the file count.
- Original Android scanner JSON hashes were identical before/after the read-only CLI attempt. Kotlin has no `detect deps` command; the isolated full scan was used instead.

Evidence retained in `/tmp`: `kotlin-package-graph-{red,ambiguity-red,focused,common,upstream-full,baseline-failures}.log`, `kotlin-package-graph-android-{before,after,combined}.json`, `kotlin-package-combined-{full,cli-scan,cli-next}.log`.

## Combined CLI and limitations

**Executable:** `/tmp/desloppify-kotlin-combined`. It preserves the caller's working directory and imports `/tmp/desloppify-kotlin-package-combined-20260924` through an isolated uv invocation. This includes only verified PRs #777/#778/#781 plus #782; installed tooling remains untouched. Parent owns any actual project rescan. Its git backing checkout is `/tmp/desloppify-kotlin-package-20260924` and must remain while the linked combined checkout is used.

This is not compiler/type resolution. Implicit same-package functions/properties, implicit extension operators, overload selection, fully qualified uses, inherited bindings and Gradle source-set visibility remain limited. Whole-file shadow suppression can omit valid edges. Connectivity demonstrates a static dependency, not behavioral test coverage. The isolated CLI invocation could not find ktlint, so that scan validates graph integration rather than full Android lint health. Existing remaining candidates still need source evidence.

All owned test/CLI processes exited. The pristine-baseline worktree and disposable scan copy were removed; the published source and combined CLI checkouts remain for the parent's controlled rescan.
