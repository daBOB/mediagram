# Browser state availability and server diagnostics

Profile selection now applies and remembers a choice only after its state read
succeeds. A failed same-profile read preserves acknowledged data. The chooser
stays open with retry feedback, admits one selection request, and aborts that
request when dismissed; obsolete replies cannot publish. Remembered-profile
startup waits for successful recovery before rendering saved-state shelves.
Seven state/chooser regressions failed before repair; three real bundled-app
startup regressions also failed with the old startup behavior. Focused browser
validation passes 121 tests across eight files. Logs:
`/tmp/browser-profile-initialization-{red,green}.log`,
`/tmp/browser-profile-startup-red.log`, `/tmp/browser-fourth-focused.log`.

Playback boundary JSDoc now names CatalogSet, shared PlayerOptions, typed callback
arguments, AbortSignal and HTMLVideoElement; transcode warming/playback declares
its release function. The audio language lookup is named trackIndexForLanguage.
The collection flattener explicitly documents playable sets and excluded
documents. Existing HTML fixture records now include the required catalog fields.
TypeScript and browser lint pass (`/tmp/browser-fourth-{types,lint}.log`).

Directory measurements ignore only ENOENT. Unexpected root or nested failures
reject the scan; a real filesystem/status-route regression proves the previous
total is retained and a later successful scan recovers. Manifest reads translate
only a missing file to the missing-manifest sentence. A real encrypted archive
containing a directory at manifest.json now reports its EISDIR cause while
keeping the prior catalog and removing staging.

Sync import counts newly created profiles as changed rows, preserving the
documented meaning that a zero count indicates no imported change and matching
the native implementation. This fixes the cause rather than weakening the
documented return value to exclude profile creation. The profile-only regression
requires one change on first import and zero for repeated/normalized identity.
Existing mixed-row and coalescing expectations now include the actual profile
insert. List-exchange docs distinguish null storage from throwing SQLite errors.
Four filesystem/count regressions failed beforehand; the focused final gate
passes 165 tests and TypeScript (`/tmp/web-sixth-files-count-{red,green}.log`,
`/tmp/web-sixth-types.log`).

Independent review passes (see `web-sixth-root-independent-review.md` and
`web-http-shutdown-drain.md`). The final shared web gate passes 1,688 tests,
12,012 assertions across 124 files, TypeScript and browser ESLint. Logs:
`/tmp/web-sixth-full-{tests,types,lint}.log`. Six focused commits through
`b05844a3` match all 27 tested path hashes; see `git-manager-web-sixth-commits.md`.
