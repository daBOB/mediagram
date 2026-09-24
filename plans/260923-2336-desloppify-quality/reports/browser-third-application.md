# Browser profile discovery and route lifetimes

Status: DONE

## Changes

`loadProfiles()` now returns whether discovery succeeded. HTTP refusal, network
rejection, unreadable JSON and interrupted response bodies return false and keep
the last known profiles. Successful empty and remember-disabled responses return
true. Startup checks that result before trusting a remembered profile and passes
failure to the actual profile picker.

The picker displays “Could not load profiles” and “Retry profiles” on discovery
failure, without showing an empty collection of profiles or claiming storage is
disabled. A retry admits one pending request, redraws the recovered profiles and
management controls, and remains retryable after another failure. Cancelling a
chooser while its request is pending prevents a late redraw or profile selection.
Existing profile selection and refresh generation checks remain intact.

Collection deletion now captures the route visit and hash. Successful completion
can return to the Collections shelf only during that visit. Navigation elsewhere,
including leaving and returning to the same list, invalidates that callback.
Ordinary state redraws do not invalidate the visit: deletion emits a state change
before its promise resolves, and the successful same-visit redirect still works.

The private count refresher is named `refreshShelfCounts`. Held profile and
collection arrays, and creation results, reference the server `Profile` and
`Collection` types rather than `any[]` or incomplete copies of those records.
Fixtures now include the required creation timestamp where statically checked.

Production changes are limited to `web/public/app.js`,
`web/public/lib/watch-state.js` and `web/public/lib/profile-picker.js`.
The existing collection view already delegates navigation to the application,
so it needed no edit. Status production modules needed no edit.

## Regression evidence

Before production changes:

```sh
cd web
bun test test/watch-state-async.test.ts test/browser-application.test.ts
```

`/tmp/browser-third-application-before.log`: **44 pass, 11 fail**. Failures prove
the missing discovery result contract, the misleading rendered picker for HTTP,
network and JSON failure, and both late-delete navigation cases. The successful
same-visit deletion and genuine empty-profile cases already passed.

The application tests bundle and execute the real browser entry and modules.
They replace only DOM/media, HTTP, event-stream and clock boundaries. New System
route tests exercise initial rendering, a visible 503 polling failure followed
by recovery, stopped polling after navigation, return to System, and isolation of
late responses, late response bodies and rejected requests. They retain the old
panel as well as checking the current page, detecting writes into detached DOM.

Two isolated source-copy mutations verify this coverage without modifying the
working tree:

- Removing the application's `stopStatus()` call fails all **4 System tests**
  because requests continue after leaving;
  `/tmp/browser-third-status-disposal-mutation.log`.
- Removing stopped-state response guards fails all **3 late-response tests** at
  the detached-panel assertion, including asynchronous body decoding and the
  error path; `/tmp/browser-third-status-late-mutation.log`.

Both temporary copied projects were removed after their test processes exited.

Final focused gate:

```sh
bun test test/browser-application.test.ts test/watch-state-async.test.ts \
  test/watch-state-refresh.test.ts test/management-controls.test.ts \
  test/status-lines.test.ts test/collection-add.test.ts \
  test/collection-add-async.test.ts
```

**103 pass, 0 fail, 303 assertions across 7 files**, 1.75 seconds;
`/tmp/browser-third-application-verified.log`. This includes the existing stale
profile A→B→A protections and new retry admission/cancellation checks.

Whole-web TypeScript passed:
`bunx --no-install --package typescript tsc --noEmit --project tsconfig.json`;
`/tmp/browser-third-application-types.log`.

Scoped ESLint passed with zero warnings for app, watch-state, profile-picker and
collections-view; `/tmp/browser-third-application-lint.log`. Scoped
`git diff --check` also passed.

## Handoff

No whole-Bun run, scanner state, snapshots, manifests or commits were changed.
No live network or user data was used, and no owned process remains running.
Root owns independent review and the coordinated integration gate. No known
remaining concern in this slice.
