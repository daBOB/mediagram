# Android Settings and configuration boundaries

Status: verified; 2026-09-24.

Settings row fallbacks now rethrow cancellation. The action wrapper clears busy
state in `finally`, including cancelled account/title reads, without publishing
an error notice for cancellation.

Successful actions enter a retained completion queue with monotonic IDs. The
library screen acknowledges each ID only after handling it; repeated or delayed
acknowledgements preserve newer completions. Refreshing Settings retains both
pending completions and the success marker. Forms record the marker when they
open and close after a later success, independently of navigation acknowledgement.
This covers collector gaps during profile picking and Activity recreation within
the existing Activity ViewModel lifetime. It does not introduce process-death
persistence for UI events.

Four regression tests failed before repair: cancelled account/title lookups
continued reading, and both library-change/sign-out completions disappeared before
a late subscriber. The final 14 Settings tests also cover repeated acknowledgement,
later pending actions, refresh, refused installs and accepted application changes
with unavailable metadata. The full setup module tests and mobile production
compilation pass. Evidence: `/tmp/android-settings-red.log` and
`/tmp/android-settings-release-green.log`. Independent final source review passes:
[Settings review](android-settings-review.md). Scoped ktlint passes.

The Kotlin JVM convention now configures its concrete extension directly, with
unchanged target, opt-ins and warning property. Its compilation and the three
affected module suites pass. The Kids wall uses typed entry and media-set lists,
preserving headings, item keys and callbacks; the 112-test mobile gate passes.

The shared Compose bundle no longer includes developer tooling in implementation.
The existing debug dependency remains. Gradle dependency insight confirms release
contains only tooling-preview while debug contains `ui-tooling-android:1.11.4`.
Logs: `/tmp/android-settings-release-green.log` and
`/tmp/android-compose-debug-dependency.log`. No dependency versions changed.

The proposed removal of unused Kotlin package provisioning is pending a user
decision because the prior accepted Android design explicitly retains those
components. No package APIs or stores were removed while that question is open.
