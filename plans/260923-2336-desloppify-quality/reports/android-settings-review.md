# Android Settings completion and cancellation review

Status: DONE — PASS

Read-only independent review on 2026-09-24 of `SettingsViewModel`, `SettingsUiState`, `Libraries`, mobile `SettingsOutcomes`/`SettingsScreen`, and the setup ViewModel tests/core fixture. No source/state edits or Gradle invocation were made.

## Review result

No unresolved defect found in the requested completion-retention and cancellation changes.

- **Cancellation stays cancellation.** All three formerly broad `runCatching` paths use the same `optionalRow` helper, which rethrows `CancellationException` before handling ordinary failures. A cancelled account or title lookup cannot continue into later row reads or produce an ordinary failure notice. The optional row refresh after an accepted application change also preserves cancellation.
- **Busy state is released.** The first inspected revision rethrew cancellation before its busy-reset statement. This was reported to root; the final source puts the reset in `finally`, and both cancellation regressions now assert `busy == false`. This closes the concrete disabled-actions regression exposed by propagating cancellation.
- **Successful effects survive collector gaps.** The pending `StateFlow` list retains each completion until its ID is acknowledged. Publication follows successful library installation/persistence, identity replacement, or all sign-out cleanup steps. Failed installation cannot increment the success marker or enqueue navigation. A later optional read failure cannot undo an already committed completion.
- **Acknowledgement does not erase newer work.** ID allocation and append have no suspension within the ViewModel's main-scope execution. Filtering the acknowledged ID preserves other completions, including repeated events with different IDs. Repeated/old acknowledgement is harmless. `SettingsOutcomes` handles each entry before acknowledging it and retains current callback references through `rememberUpdatedState`.
- **Form closure has an independent durable marker.** The form records the action marker when opened; a subsequent successful action closes it even when the library handler has already consumed the queue entry. `refresh` retains the marker, and queue acknowledgement does not reset it. This also avoids treating a previously completed action as a newly opened form's success.

The queue and marker solve distinct consumers' needs without a new event framework or persistence layer. Their lifetime remains the existing Activity-owned ViewModel lifetime; no process-death persistence claim is introduced.

## Evidence and validation limits

Inspected the final regression sources for account/title cancellation, no-subscriber library/sign-out completion, old/repeated acknowledgement with newer work pending, refresh preservation before/after acknowledgement, refused installation, and accepted identity with unavailable rows. They exercise the production ViewModel through its existing core/settings fixtures.

Root supplied the four failing-before regressions and owns the final focused/broad test runs. This independent review deliberately did not run Gradle while another worker held its execution slot, and does not claim a separately executed Compose lifecycle test.

Concerns/Blockers: None remaining in this review slice. Final test execution remains with root.
