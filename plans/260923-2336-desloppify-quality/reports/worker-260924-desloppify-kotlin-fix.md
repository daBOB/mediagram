# Desloppify Kotlin import fix published

Status: DONE_WITH_CONCERNS

The isolated upstream patch fixes Kotlin delegate/operator unused-import false positives and prevents `next` clusters from recommending a fixer unavailable to their members. No mediagram source, scanner state, or installed tool was modified. Controller approved publication after reviewing all five files; [upstream PR #777](https://github.com/peteromallet/desloppify/pull/777) is open.

- Clone: `/tmp/desloppify-kotlin-fix-NTbCJf`
- Branch: `fix/kotlin-implicit-import-usage`
- Base: `3a7735d531a96b6a226bfbdc9fd662b14195f857`
- Commit: `84822cc`; five source/test files only, using the authenticated account's public GitHub noreply author.
- Reviewable [patch](/tmp/desloppify-kotlin-fix-NTbCJf/kotlin-imports-and-autofix.patch) and [draft PR title/body](/tmp/desloppify-kotlin-fix-NTbCJf/PR.md).

The detector now recognizes parsed property delegates, indexing, calls, and assignment-plugin syntax. It preserves possible implicit extension uses, handles Kotlin aliases, and still reports ordinary unused imports and operators without corresponding syntax. Cluster advice reuses each member's existing capability-validated command. Mixed or unknown language support suppresses the autofix hint and retains cluster drill-down.

Evidence:

- Initial fixtures failed before production edits: 24 failed, 13 passed. Fixtures were then expanded; the final 47 regression cases pass.
- Focused upstream suites: **177 passed** ([log](/tmp/desloppify-kotlin-fix-NTbCJf/focused-tests.log)). Ruff on all five changed files and staged diff-check passed.
- Read-only detector comparison over **227 Kotlin/KTS files**: upstream **47 findings**, patched **0**. Breakdown: 24 `getValue`, 20 `setValue`, one each `get`, `invoke`, `assign` ([JSON](/tmp/desloppify-kotlin-fix-NTbCJf/android-detector-proof.json)). Root independently proved the Gradle imports required by removing them and observing compiler failures, then restoring them; see Android `.desloppify/gradle-operator-proof.log` and `gradle-operator-restored.log`.
- Full upstream suite: **5,855 passed, 5 skipped, 2 failed** ([log](/tmp/desloppify-kotlin-fix-NTbCJf/full-tests.log)). Both failures are duplicate review-prompt tests asserting `Previously flagged issues`. Both also fail with the original upstream production files ([baseline log](/tmp/desloppify-kotlin-fix-NTbCJf/baseline-failures.log)).

The syntax is supported by Kotlin's [delegate conventions](https://kotlinlang.org/docs/delegated-properties.html), [operator conventions](https://kotlinlang.org/docs/operator-overloading.html), and Gradle's [assignment extension API](https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.kotlin.dsl/assign.html). Without receiver types, the detector conservatively retains potential uses; it does not prove which overload resolves. This patch covers the affected conventions, not every Kotlin operator.

Separate concern: ktlint 1.8.0 output is incompatible with the registered flat JSON parser, and the bare formatter ignores scan exclusions. Local reproduction: WARN-prefixed output raises a parse error; removing the preamble yields 544 nested errors across 48 files but zero parsed findings. A bounded [issue draft](/tmp/desloppify-kotlin-fix-NTbCJf/ktlint-issue.md) records the parser and formatter owners; a separate implementation is now under review in `/tmp/desloppify-ktlint-fix-NTbCJf`. No formatter was run on mediagram.
