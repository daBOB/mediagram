# Fifth web root repairs

Status: DONE; full web gate and independent reviews pass.

## Cache inventory and idle cleanup

Explicit cache inventory and eviction now propagate unexpected filesystem failures,
while a missing root or entry remains empty. Automatic post-write maintenance
continues to warn without discarding downloaded playback bytes. Idle cleanup
rechecks candidate identity and last activity after earlier cleanup awaits; a
newly touched, acquired or replaced session is retained, and the returned count
reflects actual stops.

Real temporary-file/symlink/permissions and deterministic gated-reaper regressions
failed before the repair: 53 passed, six failed in
`/tmp/web-cache-reaper-red.log`. The same suites now pass 59 tests and 133
assertions in `/tmp/web-cache-reaper-green.log`. The non-root run exercised the
permission case. [Independent review](web-fifth-lifecycle-independent-review.md)
passes; its notes preserve the existing status-route fallback limitation.

## Subtitle selection and browser contracts

The actual bundled application, served HTML and keyboard handler exposed the
subtitle defect: after choosing German at ordinal two, pressing c twice selected
English. The new regression fails before the fix (seven pass, one fail), then
passes while also checking picker-Off restoration and a subsequent title with
a different track list. The transport retains the last enabled ordinal only
within the current title; rebuilding the menu resets it.

Audio chooser JSDoc now imports the real server AudioTrack type and describes
parameters/results including nullable metadata. The legacy test fixture now
supplies the actual nullable title field. The HLS starter documents the existing
playlist/copied result instead of promising a bare URL.

The five-suite focused gate passes 65 tests and 232 assertions:
`/tmp/browser-third-playback-green.log`. Whole-web TypeScript and browser ESLint
also pass. [Independent review](web-fifth-boundaries-independent-review.md) passes.

## Terminal prompt review

Root independently inspected the public Writable gate, shared readline reader,
finally-restored echo and owned resize/stream cleanup against the real PTY test
and failing suppression-removal mutation. No additional defect was found.
See [adapter evidence](web-hidden-prompt-integration.md): 29 focused login tests
pass on Linux/Bun 1.4.2; no other platform result is claimed.

## Browser lint coverage

The scanner's ESLint attempt returned no usable output because configuration was
missing, not because the binary was absent. Added declared ESLint, @eslint/js and
globals development dependencies, a flat config for authored public JavaScript,
and the lint command in the existing check script. Recommended rules with browser
globals pass all 56 modules. A no-file stdin probe containing an undeclared call
fails no-undef, proving the gate is active. Official configuration references:
https://eslint.org/docs/latest/use/configure/language-options and
https://eslint.org/docs/latest/use/getting-started. The corrected snapshot scan
remains pending while the current browser queue is being repaired.

The final whole-web gate passes **1,664 tests, 11,896 assertions, 123 files**,
plus TypeScript and browser ESLint. Logs: `/tmp/web-fifth-full-{tests,types,lint}.log`.
Root independently reviewed the application retry/navigation/state types and
actual System integration/mutation tests; no defect found. The browser snapshot
now contains 291 hash-verified authored source/context files, including ESLint
configuration. The corrected scan executes ESLint successfully, scans exactly
56 production JavaScript files and reports full detector coverage. Its printed
coverage opportunity count is not a count of source modules.

Postflight strict scores: TypeScript92.4, browser84.8. New blind reviews are
running against stable tested sources; no review-score improvement is presumed.
Focused commits/push are delegated, with a tested source hash manifest retained
at `web/.desloppify/fifth-tested-source-sha256.json`.

A verified concise-arrow parser reproduction was added to existing upstream
[issue761](https://github.com/peteromallet/desloppify/issues/761#issuecomment-5806683177).
The extractor incorrectly attributes the following function's body to an awaited
expression arrow. Authentication code was not rewritten to evade that warning.
