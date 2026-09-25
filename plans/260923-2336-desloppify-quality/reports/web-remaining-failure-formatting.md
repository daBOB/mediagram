# Remaining web failure formatting

Status: DONE

The remaining sync, catalog-follow and poster-start catch paths now reuse the
existing [failureMessage](../../../web/src/failure-message.ts). Arbitrary rejection
values can no longer make these formatters throw or silently lose their diagnostic.
The public result shapes, import/publication order and retry semantics are unchanged.

## Confirmed repairs

- [StateSync](../../../web/src/state/sync.ts) no longer keeps an unsafe local
  `describe`. An unprintable list rejection resolves a failed outcome, and its
  queued follow-up still imports real remote progress and publishes it. Every
  caller waits for the drain; later calls continue. An unprintable send rejection
  preserves the committed pulled count and allows the next round to publish.
- [CatalogFollower](../../../web/src/application/catalog-follow.ts) safely reports
  an injected router-swap failure. The prior database remains served through a
  real local HTTP listener, the rejected database closes, and cache expectations,
  status and events remain unchanged until the same installed timestamp is
  successfully retried. The retry reuses the already downloaded snapshot.
- [Poster fetch](../../../web/src/channel-index/fetch-posters-for-index.ts) safely
  returns a spawn-failure reason for null, undefined, strings and unprintable
  values. Tests control only `Bun.spawn`'s throwing boundary, restore it in
  `finally`, then run an actual temporary executable successfully.

The two sync review IDs describe the same defect; they are not counted as two
independent repairs. The unsafe Error assertions in the latter two callers form
the separate imported type-safety finding.

## Approved adjacent scope

The nearby catch audit found the same confirmed cause in
[installChannelIndex](../../../web/src/channel-index/install-channel-index.ts).
A real async chunk generator can throw an unprintable value after partially
writing staging; the old formatter then rejects instead of returning `kept`.
After the parent extended ownership, this catch and its validation wrappers now
reuse `failureMessage`. Regression matrices verify the prior current pointer,
timestamp and SQLite bytes remain intact, staging is removed, and a valid retry
at the same newer timestamp installs a readable catalog.

In that same file, the approved naming repair changes private `prove` to
`validateAndCountPlayableSets`: its name now states both validation and the
returned count. Its behavior, error causes and database cleanup are unchanged.

The parent also authorized narrow defensive reuse in
[WatchState.open](../../../web/src/state/store.ts). Its catch contained the same
unguarded conversion, but normal filesystem/SQLite errors are Error objects.
No artificial SQL seam or failing-before claim was invented for that defensive
change. Existing storage-failure/state tests pass. The audit found no remaining
local `describe`, `String(error)` or `(error as Error).message` catch formatters in
`state/`, `application/` or `channel-index/`; nearby raw-error console calls do
not perform the confirmed unsafe conversion.

## Regression and validation evidence

Tests exercise actual owning code with temporary SQLite, actual catalog files
and a local listener, controlled channel/iterator failures, and temporary poster
executables. They do not duplicate the formatter implementation.

| Owning test file | Rejections covered | Failing-before evidence |
| --- | --- | --- |
| [state-sync-coalescing.test.ts](../../../web/test/state-sync-coalescing.test.ts) | Error, null, undefined, string, unprintable list failures; queued remote import and later calls | Unprintable value rejected the shared drain. |
| [state-sync.test.ts](../../../web/test/state-sync.test.ts) | Error and unprintable send failures after a committed import | Unprintable value rejected instead of reporting pulled rows. |
| [application-catalog.test.ts](../../../web/test/application-catalog.test.ts) | Error, null, undefined, string, unprintable swap failures | Four failures: null/undefined produced secondary TypeErrors; string/unprintable values lost the diagnostic. |
| [fetch-posters-for-index.test.ts](../../../web/test/fetch-posters-for-index.test.ts) | Error, null, undefined, string, unprintable spawn failures | The same four secondary-error/diagnostic failures. |
| [install-channel-index.test.ts](../../../web/test/install-channel-index.test.ts) | Error, null, undefined, string, unprintable partial-download failures | Unprintable value rejected the promised kept result. |

- Before production changes: **45 passed, 11 failed** across five files;
  `/tmp/web-remaining-failure-red.log`. All 11 failures match the causes above.
- After changes, the same five files: **56 passed, 0 failed**, 303 assertions;
  `/tmp/web-remaining-failure-green.log`.
- Broader state/channel/catalog/startup/lifecycle gate: **281 passed, 0 failed**
  across 21 files, 833 assertions; `/tmp/web-remaining-failure-scoped.log`.
- Whole-web typecheck: `bunx --no-install --package typescript tsc --noEmit
  --project tsconfig.json` passes from `web/`;
  `/tmp/web-remaining-failure-tsc.log`. Its earlier unrelated audio-test index
  narrowing diagnostic was fixed by that file's owner before the final pass.
- Scoped whitespace checks pass. Only test descriptions changed after the
  scoped runtime gate; final typechecking includes those descriptions.

The controller coordinates the full Bun gate and scanner resolutions; neither
was run here. No scanner/git/global configuration changes or live service/user
database operations occurred. Existing unrelated edits were preserved. Test
listeners, spawned commands and temporary files are owned by fixture cleanup;
all invoked commands exited, with no background process left running.

Concerns/Blockers: none in this scope. Whole-suite integration remains with the
controller, and the state-database startup formatter change is defensive rather
than a reproduced SQLite failure.
