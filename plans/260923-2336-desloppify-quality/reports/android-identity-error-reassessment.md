# Android identity replacement and storage contracts

Status: implemented and independently reviewed; final Android integration gate pending.

`SettingsViewModel` no longer infers credential rejection from every local error,
or network unreachability from the broad `CoreException.Network` category. The
native boundary has no explicit application-identity rejection variant. Failures
now use a neutral retry sentence; the wrapper retains the original exception,
including any suppressed resource-cleanup failure. Cancellation still propagates
without a notice. Successful completion and row refresh behavior are unchanged.

Four new tests use the real `StoredCoreProvider`: previous-core close failure,
replacement construction failure, encrypted-setting write failure after account
verification, and a broadly classified RPC failure. Each verifies the notice,
retained credentials, settled busy flag and absence of a success completion.
The prior unverified-account test now checks the truthful notice as well.

- `/tmp/android-identity-errors-red.log`: all five message cases fail before repair.
- `/tmp/android-identity-errors-green.log`: all 18 Settings cases pass.
- `/tmp/android-identity-errors-format.log`: scoped ktlint formatting passes.
- [Independent review](android-storage-identity-review.md): PASS; original causes
  remain available internally and raw messages never reach the UI.

`CoreStorage.clear` KDoc now states sequential, non-atomic deletion, immediate
failure propagation, possible partial completion and retained retry/recovery
obligations. Removed the obsolete claim that the core cannot delete a sign-in
key and the inaccurate two-delete description. Implementation and public
signatures are unchanged. Existing setup/sign-out failure callers were inspected;
no additional behavior test is needed for this documentation correction.
