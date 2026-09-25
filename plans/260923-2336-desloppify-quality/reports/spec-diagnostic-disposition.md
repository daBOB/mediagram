# Specification diagnostics and command cleanup review

Status: DONE_WITH_CONCERNS — implementation and normal gates pass; the exact scanner lint command still rejects the documented invariants and broader metadata inventory below. No scanner state or resolutions were changed.

## Command cleanup review

No defects found in the changes to `commands/whoami.rs`, `login_code.rs`, `smoke_upload.rs`, and `serve.rs`. Their fallible work after a successful connection settles before `Tg::shutdown` is awaited, following the neighboring `accept_login` and `edit` pattern. Serve binding and bound-address lookup now happen before opening Telegram. Smoke sends use the documented non-idempotent FLOOD_WAIT-only retry policy; deletion keeps general retry. Review was read-only; no live Telegram commands ran.

## Changes

- Annotated 28 pure return-value APIs with `must_use`, including the nonmutating `Caption::with_part` method. Discarding these results does no useful work.
- Added error documentation for the six affected fallible APIs; retained the existing caption-budget description and IMDb normalization behavior.
- Reject plans whose part count cannot fit the caption's `u32` count before allocating their range vector. Iteration now uses checked `u32` counts and widening offset arithmetic.
- Replace the potentially truncating schema-version conversion with checked conversion. Negative versions still select no migrations; versions beyond known groups select all available migrations, including on narrower hosts.
- Keep private package-date month/day values as `i64`, eliminating unnecessary narrowing while preserving names byte for byte.
- Add the actual TMDB package categories `api-bindings` and `multimedia`. No URLs or publication metadata were invented.
- Move the existing caption-version agreement test into the existing caption codec test module so the documented production module remains below the source-size limit.

Compatibility: wire formats and function signatures are unchanged. `PlanError` gains `TooManyParts(u64)`; external exhaustive matches may need this additional case. No exhaustive `PlanError` match exists in the workspace. Root approved this narrow error extension.

## Validation

- `cargo test -p mlib-spec`: **150 passed**, no failures or ignored tests. Includes golden wire/name cases, the oversized-plan regression, and two schema-range tests.
- `cargo clippy -p mlib-spec --all-targets --all-features -- -D warnings`: passed.
- `cargo clippy --all-targets --all-features -- -D warnings`: passed, including all callers.
- `cargo test -p mediagram --test code_standards`: **2 passed**. Every production source remains within 200 lines.
- Scoped rustfmt and `git diff --check`: passed.

The oversized-plan test was also run before and after the conversion in an owned child process limited to 512 MiB of address space, with core dumps disabled. Before the fix it attempted a **103,079,215,104-byte allocation** and aborted; afterward it rejected both `u32::MAX + 1` parts and the `u64::MAX` total immediately, passing in 0.00 seconds. No enormous allocation was permitted.

Logs: [spec tests](../../../.desloppify/spec-tests-verified.log), [bounded failure](../../../.desloppify/spec-plan-before.log), [bounded success](../../../.desloppify/spec-plan-after.log), [workspace Clippy](../../../.desloppify/spec-workspace-clippy.log), [standards](../../../.desloppify/spec-standards-verified.log).

## Exact scanner diagnostics retained

Both runs used the scanner's exact command, without suppressions:

```sh
cargo clippy --workspace --all-targets --all-features --message-format=json -- -D warnings -W clippy::pedantic -W clippy::cargo -W clippy::unwrap_used -W clippy::expect_used -W clippy::panic -W clippy::todo -W clippy::unimplemented
```

Unique diagnostics, deduplicating the repeated library/test builds, decreased from **76 to 32**. The command still exits 101; it stops at the spec crate, so this is not a complete inventory of downstream pedantic warnings. Evidence: [before JSON](../../../.desloppify/spec-clippy-before.jsonl), [after JSON](../../../.desloppify/spec-clippy-after.jsonl).

| Remaining diagnostics | Evidence and disposition |
| --- | --- |
| Six `unwrap_used` in `filename.rs` at lines 33, 38, 40, 43, 46, 50 | Each compiles a source-literal regex in a `LazyLock`. Filename input never controls the pattern. The filename suites exercise these initializers. Retained; silent fallback or a runtime error API would obscure an invalid program constant. |
| Two `expect_used` and their two `missing_panics_doc` companions | `index_caption.rs:39` serializes three integers; `package/mod.rs:109` serializes fixed integer/string scalar fields. These representations cannot produce serializer errors. Existing comments explain the invariants; byte-exact tests pass. Retained instead of introducing fallible public APIs or custom JSON writers. |
| Six test-only `unwrap_used` | `ids.rs:65,69` and `part_plan.rs:91,100,101,113` assert that literal valid fixtures succeed. Panicking is the intended test failure. |
| One test-only `cast_possible_wrap` | `schema.rs:175` converts the length of the seven-entry static migration array solely for equality with the declared version. No input controls this value. |
| Fifteen `cargo_common_metadata` | The line-1 spec diagnostic aggregates repository/readme/keywords/categories across the whole workspace. TMDB categories were corrected; all four packages still lack repository/readme/keywords metadata, and the other three lack categories. Those manifests or broader publication-policy decisions are outside this edit ownership. |

Eight of the original 48 finding locations are the six regex and two scalar-serialization invariants above. The original metadata message specifically named TMDB categories, which is corrected, but the same line-1 inventory continues to contain the other 15 metadata messages. The remaining original actionable annotations, docs and conversions are corrected. No blanket allows, fallback data, or scanner exclusions were added.
