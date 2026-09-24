# Web cache, login, and refresh reassessment

Implemented the assigned source/test slice on 2026-09-24 without changing
scanner state, resolving findings, or committing. Existing workspace edits
were preserved.

## Changes

- `cache_reader_positional_numeric_arguments`: `CachedReader.read` and
  `readStream` now share `CachedReadRequest`, with named `setId`, `partIdx`,
  `start`, `length`, `partLength`, and `fetch` fields. Updated the production
  Telegram call and 36 existing test calls. Read, batching, cache, readahead,
  and short-read algorithms are unchanged; no refactor-only tests were added.
- `login_global_console_lifetime`: `runLogin` intercepts `console.log` only
  during synchronous client construction and restores it in `finally`, even
  when construction throws. Each client receives its own error-level logger
  whose output uses the existing redacting stderr reporter. The login IO
  factory now receives this logger as its third parameter; ordinary two-arg
  factory implementations remain assignable. Authentication and cleanup
  never hold or restore a global interception across an await.
- `refresh_catch_assumes_error_instance`: every catch that reports a refresh
  refusal formats an unknown rejection safely. Error messages are preserved,
  primitives are stringified, and an unprintable rejection gets a stable
  description. Formatting cannot mask the refusal or interrupt retention of
  the installed catalog.

## Verification

- Before the bug fixes, login/refresh regressions produced **52 passed and
  nine failed** tests. The failures covered pending authentication, concurrent
  login completion, constructor failure during delayed cleanup, and null,
  string, or unprintable-object rejection at both pointer and package fetch.
- Final focused run: **118 passed, zero failures**, 472 assertions across
  cache-source, cache-store, series-preload, application-media-shutdown,
  login-setup, and package-refresh suites. This includes both concurrent login
  completion orders and a client-logger diagnostic/redaction test.
- Whole-web TypeScript check passed with zero diagnostics:
  `bunx --package typescript tsc --noEmit --pretty false`.
- `git diff --check` passed. Source changes and evidence were sent to the root
  agent for independent review. Root owns the final whole-web checks.

Logs: `/tmp/mediagram-cache-login-refresh-before.log`,
`/tmp/mediagram-cache-login-refresh-after.log`, and
`/tmp/mediagram-cache-login-refresh-types.log`.

The refresh regressions use encrypted package fixtures installed on temporary
filesystems and verify the held identity and library bytes after failure.
Login tests use controlled client/prompt IO without a Telegram connection.
All owned test commands and their import-check subprocess exited. No project
servers or persistent user data were touched.

## Remaining concerns

None found in this slice. `AGENTS.md` is absent from the filesystem; the
session-supplied development instructions, current `CLAUDE.md`, and relevant
code standards were followed. The root agent owns finding disposition and
integration.
