# Web boundary fix review

Status: DONE — PASS

Reviewed 2026-09-24. Scope was the six specified source files, three boundary test files, the HLS DELETE tests, and the relevant code standards. No concrete regression or blocking defect found. No source or scanner state was changed.

## Findings

- **Catalog fallbacks are limited to supported omissions.** `catalog/assets.ts:16` returns defaults only for the exact missing `assets` table. `catalog/shows.ts:48` preserves the missing `shows` table fallback. `providerFactsByShow` retries without certification only for the exact missing certification column; the retry can still expose another broken column. Closed databases and incomplete schemas therefore propagate errors instead of appearing empty. An additional real SQLite probe confirmed all five missing-table defaults and rejection of a present but incomplete shows table.
- **Static filesystem errors retain their meaning.** `http/static-files.ts:47` handles malformed escapes and null bytes before reading, retains the path containment check, and maps only ENOENT, ENOTDIR, and EISDIR to a missing file. The real symlink-loop regression proves ELOOP propagates; the directory probe confirms an ordinary directory remains 404. Installed HLS dependency reads also remain outside the public-file fallback.
- **Index validation preserves diagnostic context and staged cleanup.** `channel-index/install-channel-index.ts:113` retains both the original message and the original error as `cause` for open/read failures, and closes an opened database in `finally`. The public kept outcome intentionally exposes the contextual reason string, preserving its existing shape. The real open-failure fixture verifies the previous timestamp and database bytes survive. The existing refused-pointer and failed-cleanup fixtures pass, including retention of the original download reason when cleanup itself logs ENOTDIR. Validation still precedes rename/publication.
- **HLS DELETE uses the existing browser-write boundary.** `routes.ts:63` validates the session route, applies `refuseUnsafeBrowserWrite`, and only then calls `hls.end`. Listener tests prove a foreign host gets 403 without an end call, while matching-host and absent-Origin clients retain 204 and bodyless DELETE. Invalid session IDs still do not reach the registry. This retains the guard's existing Origin-host comparison; it does not introduce authentication or scheme-based origin policy.

## Independent validation

From `web/`:

```text
bun test test/catalog-read-failures.test.ts test/static-file-failures.test.ts test/install-channel-index.test.ts
19 passed, 0 failed, 46 assertions

bun test test/http.test.ts --test-name-pattern 'DELETE on a session|cross-origin deletion|same-origin deletion|session id that is not one'
4 passed, 0 failed, 52 filtered out, 10 assertions
```

A direct Bun probe added seven assertions against actual SQLite readers and the static response function: missing-table defaults, rejection of an incomplete shows table, and directory 404 behavior. All passed. The author's earlier ten failing regressions and 90-test run were supplied context, not rerun against historical source during this read-only review.

No full suite was repeated. Both test processes exited successfully; test-created filesystem fixtures and HTTP listeners were cleaned up by their existing teardown. No background process was started.

Concerns/Blockers: None within the requested boundary changes.
