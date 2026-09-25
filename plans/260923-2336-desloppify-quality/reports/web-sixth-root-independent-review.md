# Independent review of browser initialization and web state diagnostics

Status: DONE

## Result

**PASS — no correctness or contract defect found in the reviewed changes.**
This was a read-only source, caller and evidence review. No tests were rerun and
no production or test files were modified. The concurrent HTTP shutdown work
in `web/src/server.ts` and its new tests was deliberately excluded.

## Findings checked

- `watch-state.js` now commits the profile ID, remembered selection and snapshot
  together after a successful read. HTTP, network, JSON and interrupted-body
  failures retain the previously acknowledged state. The existing selection
  generation still rejects superseded A → B → A responses. A chooser abort
  prevents a response from applying even when the controlled fetch ignores the
  signal.
- `profile-picker.js` keeps unsuccessful selection visible and retryable. Its
  pending-selection guard prevents duplicate requests, and cancellation aborts
  the selection before dismissing the chooser. `app.js` checks the acknowledged
  selection result before permitting startup to render shelves; remembered
  profile failures therefore do not become misleading empty libraries.
- Playback changes are truthful JSDoc contracts and the mechanical
  `trackIndexForLanguage` rename. All authored callers and tests use the new
  name; the implementation still returns an array ordinal or null. The
  transcode options match acquisition/playback consumers, and the collection
  documentation now accurately excludes documents from playable traversal.
- `dirBytes` preserves ENOENT tolerance while rejecting other listing/stat
  failures. The existing status router catches those failures and keeps the
  previous measurement without renewing its freshness, permitting the next
  request to retry. Real ENOTDIR and symlink-loop fixtures exercise both scan
  boundaries; the status test checks retained bytes and immediate recovery.
- Package manifest reads distinguish absence from other filesystem failures.
  Both paths remain inside catalog refresh recovery; the actual archive tests
  verify the previous catalog and database survive and staging is removed.
- `WatchState.importMerged` counts a newly created profile inside the existing
  transaction. Normalized existing names count zero; rollback still covers
  profile creation and all imported rows. The new empty-profile regression
  exercises the previously unreported change. The changed expectations of two
  rows (profile plus progress) and six rows (profile, Kids, progress, completion,
  watchlist and collection) match the fixture operations. They preserve the
  existing rollback and publication assertions. `SyncOutcome` and
  `lists-exchange` documentation match those contracts.

## Evidence reviewed

| Evidence | Result |
| --- | --- |
| `/tmp/browser-profile-initialization-red.log` | 7 intended failures; 29 passing |
| `/tmp/browser-profile-startup-red.log` | 3 actual-entry startup failures |
| `/tmp/browser-fourth-focused.log` | 121 passed, 428 assertions, 8 files |
| `/tmp/browser-fourth-types.log` | Clean TypeScript output |
| `/tmp/browser-fourth-lint.log` | Browser ESLint gate passed |
| `/tmp/web-sixth-files-count-red.log` | 4 intended failures; 87 passing |
| `/tmp/web-sixth-files-count-green.log` | 165 passed, 461 assertions, 8 files |
| `/tmp/web-sixth-types.log` | Clean TypeScript output |

The startup tests import the actual application and drive actual chooser
  callbacks through controlled IO. Their cleanup also releases startup when an
assertion fails. Filesystem tests use temporary real files and SQLite tests
exercise production import transactions, including real trigger refusals.

The later `player-lifetime.test.ts` fixture correction also passes review:
the remembered-language case now supplies a successful empty state response
and asserts selection acknowledgement before writing the preference. Its old
default 404 cannot legitimately establish a selected profile under the repaired
contract. The subsequent episode/audio assertions are unchanged; this corrects
the test setup rather than weakening the playback regression. Root reported
the corrected narrow player gate passing.

## Limits

This review does not claim the final whole-web gate or review of concurrent
HTTP shutdown changes. Root owns those coordinated checks after the HTTP work
is stable. No processes were started for this review.
