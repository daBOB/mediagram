# Independent metadata, event and cache-shutdown review

Status: DONE

Verdict: **PASS**. No actionable defect found in the assigned changes.

## Source review

- [State storage](../../../web/src/state/store.ts): metadata query failures now propagate before `deviceId()` can mint and overwrite an identity. Schema discovery checks for the metadata table separately; an unexpected version-query failure exits opening before migration statements run. The opening failure path attempts to close its database handle and retains the original failure diagnostic even if close also fails. Startup's unavailable-storage fallback remains intact. The updated export comment matches snapshot timestamps and the additional sync list/tombstone fields.
- [CachedReader](../../../web/src/cache/reader.ts): `stop()` immediately establishes a persistent admission barrier and returns one shared settlement promise. The set of previously admitted warming tasks is captured before yielding; `warm()` cannot add work once stopping is set. Those tasks include their upstream fetch and cache writes, remove themselves only in `finally`, and retain the established best-effort handling of speculative failures. Foreground reads and explicit fills remain usable so other media workers can finish.
- [Startup](../../../web/src/index.ts) registers the reader immediately after construction, before subsequent fallible work. [Application shutdown](../../../web/src/application/lifecycle.ts) starts reader shutdown synchronously with the other media stops, awaits it before server/Telegram teardown, and preserves the existing aggregate-failure cleanup sequence. This covers both normal shutdown and partial startup cleanup.
- [Channel events](../../../web/src/telegram/channel-events.ts) uses the existing nonthrowing formatter inside the per-callback catch. A Symbol or unprintable rejection therefore cannot escape the release loop or skip rearming later events. Event classification, debounce ordering and listener cleanup are unchanged.
- [Cache storage](../../../web/src/cache/store.ts) changes only its `put` documentation. The wording accurately describes ignored write failures, logged eviction failures and the absence of a persistence/quota guarantee; playback behavior is unchanged.

## Evidence inspected

Read the new metadata and shutdown suites, changed callback cases, nearby source/callers, existing migration/cache/lifecycle tests, and [the implementation report](web-seventh-root-fixes.md). No duplicate or broad tests were run by this reviewer.

- `/tmp/web-seventh-metadata-events-red.log`: five behavioral regressions fail, covering identity replacement, both SQLite version-read error classes, and both unprintable callback values.
- `/tmp/web-seventh-metadata-events-green.log`: **49 passed, zero failed, 123 assertions across five files**, including existing migrations, write failures and cache persistence/eviction behavior.
- `/tmp/web-seventh-cache-shutdown-red.log`: the new direct stop test initially fails because that API is absent; separately, the real application/listener/cache/TelegramSource test fails the behavioral assertion that shutdown must remain pending. The latter establishes the premature-disconnect defect independently of the new method's existence.
- `/tmp/web-seventh-cache-shutdown-green.log`: **39 passed, zero failed, 107 assertions across five files**. The direct case checks repeated stop calls, draining an admitted fetch, foreground reads during/after stop, and exclusion of subsequent warming. The application case verifies upstream generator cleanup precedes Telegram disconnect after foreground HTTP work has closed.
- `/tmp/web-seventh-types.log`: empty successful TypeScript output. Scoped diff whitespace check passed.

The new SQLite tests inject one query failure while preserving real storage, migration, recovery, profile and progress behavior. Callback tests use a controlled clock and restore their warning spy/listener. Cache tests release their gates and settle owned work in cleanup; the application test also closes its player and removes temporary files.

## Limits

Review covers only the six assigned production files and associated tests, not concurrent browser or Android changes. Failure injection does not claim real disk hardware failure, and Telegram download fixtures do not contact a live account. Speculative shutdown waits for admitted work rather than forcibly interrupting it. Root owns the shared full web gate.

Only this report was written. Concerns: None within scope.
