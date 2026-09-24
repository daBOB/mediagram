# Browser fixes: independent quality review

Status: DONE

Verdict: **PASS**. No supported correctness, regression, contract, or blocking
test finding in the assigned scope.

Reviewed the pending changes against `67ed2bce` in the eight production files
and eight test/support files listed by
`web/.desloppify/browser-fifth-tested-source-sha256.json`. The preceding
[spec and edge-case scout](browser-fifth-spec-scout.md) and
[implementation evidence](browser-fifth-fixes.md) were read before this review.
The requested `flattenCollection` declaration correction is present.

## Findings and verification

- `web/public/lib/playback/transport.js:185` routes both manual input paths
  through the player's handler. Repository search found one production
  `mountTransport` caller, supplied with both new callbacks at
  `web/public/lib/playback/player.js:369`.
- `player.js:382` consumes play rejection and uses controlled retry text.
  Source identity, the source's abort signal and request generation guard
  both success and failure. `player.js:172` invalidates pending requests on
  source replacement/teardown; the pause callback invalidates the current
  manual request. Expected `AbortError` interruptions remain silent.
- `player.js:390` restores prior feedback only while the note still contains
  the manual failure. A note replaced by conversion/adaptation remains intact.
  Repeated failures retain the original notice; source stop discards it.
  Conversion fatal/startup failure aborts its source before a late manual
  completion can replace that feedback (`player.js:228`, `player.js:246`).
- `web/public/lib/library.d.ts:23` matches nullable media fields in
  `web/src/catalog.ts:73`; presentation fields at declaration line 33 match
  the shared catalog/search projection in `web/src/catalog/routes.ts:38`.
  File-language metadata remains a nullable JSON string; provider genres and
  served subtitle languages remain arrays. No storage identifiers were added.
- Catalog callback parameters match `app.js` and `course-view.js` callers.
  Metadata JSDoc uses the provider's `ShowMeta`; helpers retain bounded partial
  metadata inputs. `flattenCollection` now accepts the divisions-only input
  used by summary/header, matching `library.js:202`'s implementation. The five
  catalog JavaScript diffs contain only JSDoc changes.
- `web/test/player-manual-play.test.ts:36` mounts the actual player and
  transport and controls browser IO at the existing environment boundary.
  Its checks cover sanitized button/keyboard feedback, retry, obsolete titles
  and sources, teardown, interruptions, request ordering, pause and conversion
  notice restoration. Fixture consolidation preserves existing overrides and
  assertions; the grouping test reads added fields through the declared type.

Fresh review checks: all **16 SHA-256 values match** the current files;
scope-limited `git diff --check 67ed2bce` exits 0. Existing evidence logs were
inspected directly: initial manual regression 0/9, final regression 11/11,
guard-removal mutation 6 failures, focused suite 289/289, and parent full-web
suite 1,707/1,707. Typecheck output is empty and lint output has no diagnostics;
their successful exit status and the post-correction typecheck are recorded by
the implementer/parent, not independently rerun here.

## Limits

This was a scoped source/evidence review, with no broad suite rerun. The test
environment controls DOM, media and network behavior; it does not establish
real-browser codec playback. `checkJs: false` means TypeScript validation does
not check every JavaScript implementation expression. Runtime catalog/server,
Rust, Android and unrelated concurrent changes were outside this review.

Only this report was written. No source, test, configuration, git or scanner
state was modified; no background process was started.

Unresolved questions: None.
