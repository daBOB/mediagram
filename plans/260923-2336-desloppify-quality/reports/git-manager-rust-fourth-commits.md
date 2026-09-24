# Remaining Rust delivery commits

Status: DONE

Seven focused conventional commits contain the completed Rust source/tests,
normal generated Kotlin bindings and binding-generation script changes. Final
HEAD: `3f3a212f12c3385fff1fe34f74700362759dee88` on
`desloppify/quality-20260923`. The index and all owned paths are clean. No push.

## Commit boundaries

| Commit | Scope | Rename-aware files |
| --- | --- | ---: |
| `446c8d0` | Session identity, listener lifecycle, every automatic revocation caller and focused regressions | 26 |
| `5dc0060` | Imported profile creation counts, normalized repeats and failed-publication sync coverage | 4 |
| `ce77060` | Upload implementation names, direct deprecated aliases and all command callers | 6 |
| `3f85ea1` | Unknown compatibility diagnostics, confirmation and actual CLI regressions | 5 |
| `2e0f22d` | Ffmpeg pipe/cancellation cleanup and real-process regressions | 2 |
| `dcbbed5` | Removal orchestration seams, destructive guards and remote-before-local regression coverage | 4 |
| `3f3a212` | Core session feature ownership, API contract docs, normal generated bindings and deterministic generation | 4 |

Session helpers and every automatic revocation caller travel together so there
is no intermediate identity-free or missing-helper path. Profile import counting
is independent. The upload aliases retain legacy public paths while all current
callers use the new names in the same commit, avoiding deprecation warnings.

`add_show/mod.rs` contained both upload naming and survey behavior. After reporting
the boundary strategy to the controller, an index-only blob staged just the two
upload import/call replacements first. The remaining survey hunks were staged
with the new Survey contract and tests. No working-tree source byte changed.
The media/removal groups have no dependency on each other's private helpers.
The final generated artifact follows all Rust API/doc changes and the already
committed Android suspend adapter.

## Verification

Read repository instructions, the active plan, code standards, git commit/safety
instructions and the current implementation/independent review reports before
staging explicit paths. The controller supplied the final tested checkpoint:

- `/tmp/rust-fourth-workspace-tests.log`: **1,010 passed, 0 failed, 4 intentional
  ignored tests**, independently summed from the result lines.
- `/tmp/rust-fourth-workspace-clippy.log`: full workspace strict Clippy passed.
- `/tmp/rust-fourth-workspace-doc.log`: workspace rustdoc passed.
- `/tmp/rust-fourth-format.log`: formatting passed, with no diagnostic output.
- `/tmp/rust-fourth-android-bindings.log`: normal generation completed after
  building both shipped Android native targets.
- `/tmp/android-regenerated-binding-gate.log`: regenerated app/instrumentation
  Kotlin compilation passed. Existing Compose configuration/opt-in warnings
  remain visible in the log.
- Independent session review and independent root-contract/second-reviewer CLI
  review both pass. The latter report now records the verified regeneration;
  its prior documentation/regeneration concern is closed.

No duplicate Cargo/Gradle run was started. Commit preparation checked script
syntax, every staged diff's whitespace, actual dependency/import boundaries,
explicit staged path ownership and security matches. The only added credential
literals in authored tests are identified offline fixture values; no real key,
token, dotenv/private file or user data was staged. Generated output is from the
normal script and is not hand-edited.

A SHA-256 checkpoint of all 52 original scoped paths, including deleted old
upload module paths, was taken before staging. After all commits, both working
tree and HEAD match that checkpoint exactly. This also proves the temporary
index-only split left no partial content in the final tree. No per-commit rebuild
is claimed; the existing broad gates cover this exact completed checkpoint.

All project versions remain synchronized at **0.40.2**; no new version bump or
lockfile edit occurred. Normal commit execution succeeded with `.githooks`
unchanged; no hook was disabled or bypassed. There is no pre-commit hook in this
repository. No background process was started.

## Remaining scope

There are no remaining dirty paths in `crates/`, the binding-generation script,
or the generated Kotlin binding. Web, other Android paths, root docs/README,
gitignore, plans, skill metadata and tool state were not staged. The controller
owns scanner commit-log updates, remaining documentation/web delivery and any
later publication.

## Exact commit hashes and paths

### 446c8d053614e77839e5528632d15c1d1c7b23f4 — fix(core): bind session revocation and listeners to their connection

```text
M	crates/mediagram-core/src/api/account/auth_tests.rs
M	crates/mediagram-core/src/api/account/profile.rs
M	crates/mediagram-core/src/api/account/revoked.rs
M	crates/mediagram-core/src/api/account/session.rs
A	crates/mediagram-core/src/api/account/session_fixture.rs
A	crates/mediagram-core/src/api/account/session_updates.rs
M	crates/mediagram-core/src/api/account/subscribe.rs
A	crates/mediagram-core/src/api/channel/download_tests.rs
M	crates/mediagram-core/src/api/channel/install.rs
M	crates/mediagram-core/src/api/channel/mod.rs
A	crates/mediagram-core/src/api/channel/responses.rs
A	crates/mediagram-core/src/api/channel/search.rs
A	crates/mediagram-core/src/api/channel/search_tests.rs
A	crates/mediagram-core/src/api/channel/tests.rs
M	crates/mediagram-core/src/api/events.rs
A	crates/mediagram-core/src/api/events/listener.rs
A	crates/mediagram-core/src/api/events/open.rs
A	crates/mediagram-core/src/api/events_lifecycle_tests.rs
M	crates/mediagram-core/src/api/events_tests.rs
M	crates/mediagram-core/src/api/read.rs
M	crates/mediagram-core/src/api/read_tests.rs
M	crates/mediagram-core/src/api/state_sync.rs
M	crates/mediagram-core/src/api/state_sync/publish.rs
M	crates/mediagram-core/src/api/state_sync/publish_tests.rs
M	crates/mediagram-core/src/api/state_sync/telegram_channel.rs
M	crates/mediagram-core/src/api/state_sync/telegram_channel_tests.rs
```

### 5dc0060f5bdade5a663b7dc8ba01f712db972dbd — fix(sync): count newly imported profiles before reporting changes

```text
M	crates/mediagram-core/src/state/exchange.rs
M	crates/mediagram-core/src/state/exchange_tests.rs
M	crates/mediagram-core/src/state/profiles.rs
M	crates/mediagram-core/src/state/sync_tests.rs
```

### ce770606e83b88a27fea35216c3a3aabb6ef250a — refactor(upload): name preparation and recording side effects

```text
M	crates/mediagram/src/commands/add.rs
M	crates/mediagram/src/commands/add_course.rs
M	crates/mediagram/src/commands/add_show/mod.rs
M	crates/mediagram/src/upload/mod.rs
R093	crates/mediagram/src/upload/plan_set.rs	crates/mediagram/src/upload/prepare_set.rs
R090	crates/mediagram/src/upload/plan_document.rs	crates/mediagram/src/upload/record_document.rs
```

### 3f85ea1fcb140a2a5415260ff89e8f5ad261673e — fix(cli): report unknown compatibility when media probes fail

```text
M	crates/mediagram/src/commands/add_show/mod.rs
M	crates/mediagram/src/commands/add_show/survey.rs
M	crates/mediagram/src/commands/add_show/survey_tests.rs
M	crates/mediagram/src/commands/args.rs
A	crates/mediagram/tests/add_show_survey.rs
```

### 2e0f22d3d61cae7b9f9ff74ed05ba17a5cf82126 — fix(media): reap ffmpeg when progress reading fails or is cancelled

```text
M	crates/mediagram/src/media/ffmpeg_progress.rs
M	crates/mediagram/src/media/ffmpeg_progress_tests.rs
```

### dcbbed59a4a234a7af9ec7856b0fd604f294a052 — test(cli): verify removal guards and remote-first deletion

```text
M	crates/mediagram/src/commands/remove.rs
A	crates/mediagram/src/commands/remove_tests.rs
M	crates/mediagram/src/remove/apply.rs
A	crates/mediagram/src/remove/apply_tests.rs
```

### 3f3a212f12c3385fff1fe34f74700362759dee88 — build(core): align session features and regenerate Android bindings

```text
M	android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt
M	crates/mediagram-core/Cargo.toml
M	crates/mediagram-core/src/api/mod.rs
M	scripts/generate-android-bindings.sh
```

Concerns/Blockers: none in the authorized commit scope. No source edits, scanner
mutations, or push were performed.
