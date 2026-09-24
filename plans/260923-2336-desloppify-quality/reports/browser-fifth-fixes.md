# Browser manual playback and catalog contracts

Status: DONE

## Changes

Manual play-button and keyboard requests now use a small player-owned handler.
The transport still owns their shared toggle action; the player owns the current
source and visible note. A rejected current request shows “Could not start
playback. Press Play to try again.” It leaves the loaded source available and
reports no private media URL or raw rejection text. Successful retry restores
the previous conversion/resume explanation if that note still belongs to the
manual failure.

Each request captures both the current source and a request generation. Pause,
source replacement and teardown withdraw old requests; repeated attempts cannot
overwrite each other's result. AbortError interruptions are handled silently.
Autoplay keeps its existing silent-refusal behavior. Progress saving still
precedes source detachment, and playback session ownership is unchanged.

`CatalogSet` now declares the fields the actual catalog/search projection always
returns: nullable quality/HDR/file-language strings and poster keys, plus the
provider/display fields, offline flag, summary flag and subtitle-language list.
Unknown recorded metadata remains null; JSON file-language lists remain strings.
No server projection or catalog runtime logic was changed.

Catalog view JSDoc now uses `CatalogSet`, `Collection`, `Division` and `ShowMeta`
instead of broad objects or unspecified functions. Pure metadata functions retain
partial `Pick<ShowMeta, ...>` inputs, series summary/header only require the
collection's divisions, and `plateOf` requires only the four fields it uses.
`flattenCollection` likewise accepts only the collection's divisions, matching
its implementation and the summary caller's narrower input.
Callbacks distinguish a catalog set, a season/collection name and the
`series | tutorials` collection kind. This does not force a full metadata record
on a helper that legitimately accepts a subset.

The related typed tests share a complete browser catalog-row fixture. Existing
fixture defaults and assertions are preserved; absent API fields now have their
real nullable/empty/false values. No casts, changed JSON fixtures or weaker
compiler settings were used.

## Regression evidence

`web/test/player-manual-play.test.ts` loads the actual player and transport.
Only its existing DOM/media/network environment controls browser IO. It covers:

- Button and keyboard rejection, visible sanitized feedback and successful retry.
- Old-title, A → B → A, closed-dialog and same-title audio-source replacement.
- Expected interruption and explicit pause withdrawing a pending request.
- Older failure after newer success, and older success after newer failure.
- Successful retry restoring an existing conversion explanation.

Before the repair, the initial nine cases failed: manual play promises became
unhandled rejections, and current failures did not show feedback.
`/tmp/browser-manual-play-red.log`: **0 passed, 9 failed**. One test initially
looked for the existing control label in `title`; it was corrected to inspect
the actual `aria-label`. The pre-fix failure was already reached earlier at
the missing-feedback assertion, so that fixture correction is not claimed as
production regression evidence.

The final narrow suite passes **11 tests / 33 assertions** in
`/tmp/browser-manual-play-green.log`. A separate temporary source copy removed
only the rejection handler's current-request guard. Six cases then failed in
`/tmp/browser-manual-play-current-guard-mutation.log`, proving stale title/source,
pause and newer-request protection. Original source hashes remained unchanged
and the copied workspace was removed after the check.

The added grouping/traversal case accesses the five previously missing fields
through the actual declared `CatalogSet` result and verifies their preserved
values. Existing summary cases continue checking absent metadata, mixed quality,
HDR, language lists and partial provider facts.

## Validation

From `web/`:

```sh
bun test test/player-manual-play.test.ts test/player-lifetime.test.ts \
  test/player-features.test.ts test/player-keys.test.ts test/transport.test.ts \
  test/browser-html-player.test.ts test/library.test.ts \
  test/library-documents.test.ts test/home-shelves.test.ts \
  test/series-summary.test.ts test/shelf-view.test.ts \
  test/shared-watch-state-fixtures.test.ts
bunx --no-install --package typescript tsc --noEmit --pretty false
bun run lint
```

- `/tmp/browser-fifth-focused.log`: **289 passed, 0 failed, 668 assertions,
  12 files**. Includes shipped-HTML application mounting and existing playback
  lifetime/feature coverage.
- `/tmp/browser-fifth-types.log`: TypeScript passed, empty diagnostic output.
- `/tmp/browser-fifth-lint.log`: browser ESLint passed.
- The existing TypeScript configuration still has `checkJs: false`; this gate
  checks declarations/JSDoc at typed callers, not every JavaScript implementation
  expression. Browser lint and behavior tests provide separate evidence.

## Ownership and handoff

Eight production files: `web/public/lib/playback/{transport,player}.js`,
`web/public/lib/library.d.ts` and
`web/public/lib/catalog/{series-header,series-summary,home-view,shelf-view,plate}.js`.

Eight test/support files: `web/test/player-manual-play.test.ts`,
`web/test/support/catalog-set.ts`, and existing
`browser-html-player`, `home-shelves`, `library-documents`, `library`,
`series-summary`, `shared-watch-state-fixtures` test files.

All 16 exact tested-source SHA-256 values, keyed by root-relative `web/...`
paths, are recorded in ignored
`web/.desloppify/browser-fifth-tested-source-sha256.json` at the parent's request.
Root owns the combined full-web gate, independent review and finding resolution.
Root reports the coordinated gate passed **1,707 tests**. Its independent scout
found that `flattenCollection` still required a full collection while summary
accepts only divisions; the declaration was broadened to the actual required
fields and TypeScript passed again. No runtime source changed after that gate.

No app.js, server, Android, dependency, configuration, scanner-state or git
mutation was made. All owned Bun processes exited; temporary mutation files were
removed. No live Telegram, user media or user database was accessed.

Concerns/Blockers: None found in the owned scope. Independent review and the
coordinated whole-web result remain parent-owned follow-up checks.
