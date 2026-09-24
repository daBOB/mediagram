# Desloppify ktlint integration fix published

Status: DONE_WITH_CONCERNS

The first Kotlin import/advice fix is published as [upstream PR #777](https://github.com/peteromallet/desloppify/pull/777), commit `84822cc`, after controller review. This separate patch repairs ktlint detection and safely limits formatting to selected eligible files.

- Worktree: `/tmp/desloppify-ktlint-fix-NTbCJf`
- Branch: `fix/kotlin-ktlint-integration`
- Independent base: `3a7735d531a96b6a226bfbdc9fd662b14195f857`; does not include PR #777.
- Published commit: `c2e324b`; four source/test files only. [Patch](/tmp/desloppify-ktlint-fix-NTbCJf/ktlint-integration.patch), [PR body](/tmp/desloppify-ktlint-fix-NTbCJf/PR-body.md).
- [Upstream PR #778](https://github.com/peteromallet/desloppify/pull/778): `fix(kotlin): parse ktlint reports and scope formatting to eligible files`
- Controller reviewed all four files and independently reran the 18 Kotlin tests with ktlint 1.8.0 before approving publication.

Changes:

- Added a nested ktlint JSON parser; malformed output is an error. Rule/column details preserve separate finding identities for same-line violations.
- Kotlin's command suppresses log preambles with `--log-level=none`.
- Kotlin's fixer now supports the CLI's no-path invocation. It formats only selected files eligible under existing discovery and exclusion rules. stdin avoids glob expansion, so bracketed filenames cannot format siblings.
- Formatting output is linted before writing; malformed Kotlin, tool failure, malformed reports, and concurrent source edits preserve the original. Dry runs do not write. Writes are atomic and permissions retained. Uncorrectable remaining violations are not auto-resolved.
- The generic runner, factory, and other languages' fixer behavior are unchanged.

Validation:

- Initial new tests: **9 failed, 2 passed** before production edits ([log](/tmp/desloppify-ktlint-fix-NTbCJf/regression-before.log)).
- Final focused generic-plugin/Kotlin/autofix suites: **120 passed**, including **18 new cases**, with actual **ktlint 1.8.0** available ([log](/tmp/desloppify-ktlint-fix-NTbCJf/focused-tests.log)). Five tests use the real executable; the remaining cases run without requiring ktlint.
- Full upstream suite with ktlint available: **5,826 passed, 5 skipped, 2 failed** ([log](/tmp/desloppify-ktlint-fix-NTbCJf/full-tests.log)). Both failures are the duplicated existing review-prompt assertion for `Previously flagged issues`; both reproduced with original upstream parser/registration restored ([baseline](/tmp/desloppify-ktlint-fix-NTbCJf/baseline-failures.log)).
- Ruff on all four files and staged diff-check passed.
- Read-only Android lint: **2,572 findings / 190 eligible files / 0 excluded findings**. Hashes of **228 Kotlin/KTS files and 7 scanner JSON files** were unchanged ([proof](/tmp/desloppify-ktlint-fix-NTbCJf/android-read-only-proof.json)). This scans the full Android tree; the earlier 544-error capture covered only 48 files.

No mediagram source, scanner state, or installed tool was modified. Formatting tests wrote only temporary test fixtures. No test runners remain active.

## Combined local environment

Both fixes are available in `/tmp/desloppify-combined-fix-NTbCJf`, branch `fix/kotlin-local-validation`, commit `9636aafd5a9231a203d7a39d702c8c59a37e76f7`. It contains `84822cc` and a clean cherry-pick of `c2e324b`. A separate Python 3.13 virtual environment is already prepared there. Combined validation: **167 focused tests passed** with actual ktlint 1.8.0 ([log](/tmp/desloppify-combined-fix-NTbCJf/combined-tests.log)). CLI `--version` resolves to that temporary environment.

Run the temporary CLI from the intended project directory; this command only prints its version:

```sh
mise exec ktlint@1.8.0 -- /tmp/desloppify-combined-fix-NTbCJf/.venv/bin/desloppify --version
```

To reinstall only this already-created temporary environment:

```sh
uv pip install --python /tmp/desloppify-combined-fix-NTbCJf/.venv/bin/python --editable '/tmp/desloppify-combined-fix-NTbCJf[full]'
```

If the controller later chooses to replace the installed scanner, this non-editable install is pinned to the reviewed combined commit. **Not executed:**

```sh
uv tool install --force --python 3.13 'desloppify[full] @ git+file:///tmp/desloppify-kotlin-fix-NTbCJf@9636aafd5a9231a203d7a39d702c8c59a37e76f7'
```
