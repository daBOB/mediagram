# Metadata, event delivery and speculative-read shutdown

Status: implementation, focused gates and independent review pass. The combined
web gate passes 1,707 tests, 12,082 assertions across 127 files
(`/tmp/web-seventh-full-tests.log`). See `web-seventh-root-independent-review.md`.

Metadata reads now propagate unexpected SQLite failures. Only a genuinely absent
metadata table is treated as a fresh database during schema discovery, so a failed
version read cannot replay migrations. Failed opening/migration closes its handle
before retaining the existing unavailable-storage startup behavior. The adjacent
export-record comment now accurately distinguishes UI timestamps from sync lists
and tombstones.

Three regressions use real SQLite databases, injecting only one query failure.
Before the fix, a failed identity read silently replaced the device ID; failed
version reads replayed migrations and reported a duplicate-column error instead
of their original cause. Afterward the original identity, schema version, profile
and saved progress survive recovery. Both SQLITE_IOERR and an unrelated
SQLITE_ERROR are covered.

Telegram callback diagnostics use the existing safe failure formatter. Symbol
and unprintable-object failures previously escaped the timer and dropped other
due callbacks; the two regressions now prove delivery of both due and later events.
The cache put contract explicitly states its existing best-effort persistence and
quota behavior, without changing playback-friendly failure handling.

CachedReader now has an idempotent stop operation that immediately closes
speculative admission and awaits its existing warming tasks. Foreground reads
remain usable while the other media workers shut down. Production startup owns
the reader in ApplicationResources, and shutdown begins its stop before the first
wait and awaits it before disconnecting Telegram. One regression checks late read
completions cannot restart warming; a real application/listener/cache/TelegramSource
integration reproduces a speculative download outliving shutdown before the fix,
then proves upstream cleanup precedes disconnection afterward. Only the external
Telegram download boundary is controlled; no live account is used.

Validation:

- `/tmp/web-seventh-metadata-events-red.log`: five expected regressions fail.
- `/tmp/web-seventh-metadata-events-green.log`: 49 pass, zero fail across five files.
- `/tmp/web-seventh-cache-shutdown-red.log`: both new cases fail, including the
  production startup/shutdown behavior.
- `/tmp/web-seventh-cache-shutdown-green.log`: 39 pass, zero fail across five files.
- `/tmp/web-seventh-types.log`: TypeScript passes after retaining the generic
  signature of the injected query boundary.

These checks cover deterministic local failure and lifecycle behavior; they do
not exercise live Telegram connectivity or actual disk hardware errors.
