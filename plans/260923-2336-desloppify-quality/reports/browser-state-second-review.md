# Browser state and playback follow-up

Status: implementation and focused verification complete; coordinated whole-web
validation and snapshot refresh remain with the controller.

The browser remembers normalized shelf choices for the page when storage reads
or writes fail, including a failed write followed by a readable stale value.
Codec capabilities are probed at explicit startup, before the optional network
request, rather than during module import. Profile/list controls report failed
acknowledgements and retain their view for retry. State documentation describes
acknowledged management operations separately from immediate local writes whose
persistence is best-effort and does not roll back local state.

Catalog annotations reuse the existing Library and HomeShelvesInput declarations;
nullable progress, duration and schema values match their callers. The internal
division helper states its creation behavior in its name.

Evidence:

- `/tmp/browser-settings-codec-red.log`: four intended failures before repair.
- `/tmp/browser-management-red.log`: five intended missing-feedback failures.
- `/tmp/browser-state-controls-green.log`: 82 passing catalog/control tests.
- `/tmp/browser-state-contract-green.log`: 21 passing asynchronous state tests.
- `/tmp/web-fourth-types.log`: TypeScript passes after the fixture corrections.

The controller independently reviewed the player/application lifecycle changes
recorded in [the implementation report](browser-player-second-review.md). Request
generations discard superseded and dismissed opens; the active conversion state
governs the single runtime calculation; shared teardown saves before detaching;
the concrete next-title owner retains warming until the new source joins. No
additional defect was found. The integration work in
[the coverage report](browser-integration-second-review.md) parses the actual
served HTML, rejects missing controls, and exercises real player subtitle
selection and timing through controlled media boundaries. This verifies markup
and orchestration, not a hardware decoder.

Notes loading/parsing/rendering now live together in `playback/notes/`;
adaptation/HLS resources live in `playback/streaming/`. All production imports and
test imports, including the cache-busting dynamic HLS test import, were updated.
The architecture map names these owners. The notes stage passes 27 tests, and
the streaming/markup stage passes 92 tests, including real static module serving.
The first streaming check caught a missed dynamic test import; that import was
corrected and the entire stage passed. No wrappers, compatibility barrels or
scanner exclusions were added for this organization change.

## Final integration checkpoint

The complete source tree passes **1,608 tests**, 11,542 assertions across 121
files, and TypeScript checking (`/tmp/web-fourth-full-tests.log`,
`/tmp/web-fourth-full-types.log`). All sixteen second-review findings are resolved
through the CLI. The ignored browser review snapshot now contains 288 verified
source/context hashes, including moved modules and new integration tests.
The corrected postflight reports 81.9 strict / 90.8 objective; strict penalties
for automatically disappeared findings remain intact. Changed subjective
dimensions still need reassessment. An initial refresh used the wrong working
directory and failed before copying; its accidental unchanged-snapshot scan is
superseded by the successful hash-verified refresh and postflight scan.
