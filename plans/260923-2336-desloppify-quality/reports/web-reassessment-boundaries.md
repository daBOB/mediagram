# Web reassessment boundary fixes

Status: all fourteen new findings resolved and verified. The full blind
reassessment scored 92.5 strict; the postflight scan is running.

Catalog compatibility now tolerates only the named absent table or certification
column. Closed databases and unrelated malformed schemas reach the request error
boundary. Static asset reads preserve 404 for missing paths and malformed URLs,
while unexpected filesystem failures propagate. Index installation includes the
database-open/read cause in its contextual error and keeps the installed catalog.
HLS DELETE uses the existing browser-write policy, preserving same-origin and
non-browser requests while rejecting mismatched origins before session deletion.

The boundary regressions exercise actual in-memory SQLite, temporary directories,
an unavailable staged index, a filesystem symlink loop, and real HTTP sockets.
Before the repair, the focused run had 65 passes and ten failures. Afterward,
the expanded six-file gate passed 90 tests with 255 assertions. Logs:
`/tmp/mediagram-web-errors-before.log` and
`/tmp/mediagram-web-errors-after.log`.

Three local simplifications consume the existing collection-write helper result,
document the local-export/own-channel-message distinction accurately, and name
poster-fetch tasks and results directly. Their existing four-file regression gate
passes 30 tests with 112 assertions (`/tmp/mediagram-web-local-cleanup.log`).

The other three completed findings and their failing-before tests are recorded in
[cache/login/refresh verification](web-cache-login-refresh-reassessment.md).
All resolutions used the supported CLI with implementation evidence. No score
or plan-state JSON was edited. Audio probing and listener-address reporting moved
to their owning catalog/application modules, with direct imports and module-map
documentation updated.

Media workers resolve their endpoint from the actual listener address and port.
The [independent endpoint review](web-media-endpoint-review.md) covers actual
IPv4/IPv6 listeners, port zero and all three media collaborators. Restoring the
old guessed URL fails all four integration cases. The
[HTTP integration report](web-http-stream-thumbnail-tests.md) covers genuine
backpressure and router-to-thumbnail publication; four meaningful mutations fail.
The [boundary review](web-boundary-fix-review.md) independently confirms the
database/filesystem/origin changes.

Final whole-web gate: **1,556 passed, zero failed**, 11,320 assertions in 115 files
(`/tmp/mediagram-web-delivery-final.log`). Two initial test-only type diagnostics
were corrected with non-null command argument and runtime JSON narrowing;
TypeScript then passed with no diagnostics (`/tmp/mediagram-web-delivery-types.log`).
