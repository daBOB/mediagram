# Spike — can the app replace itself silently? (2026-10-02)

Probe `com.mediagram.updateprobe`, release build (not debuggable), signed with the release key (cert `5840181d…f576`), chain v1→v5 each embedding the next. PackageInstaller session, `USER_ACTION_NOT_REQUIRED`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION` + `REQUEST_INSTALL_PACKAGES` declared. Probe built in a throwaway worktree (`spike/self-update-probe`).

## Devices
- TV box `192.168.0.35:5555`: Skyworth HPR302 Google TV, **Android 14 (SDK 34)**
- Tablet `caad49da`: Redmi Pad Pro, HyperOS, **Android 16 (SDK 36)**

## Results

| Device | Case | `REQUEST_INSTALL_PACKAGES` app-op | Commit from | Status | versionCode | Seen |
|---|---|---|---|---|---|---|
| TV | A | default | foreground | -1 pending user action | 1 → 1 | "can't install unknown apps from this source" + Settings/Cancel |
| TV | B | default | onStop | -1 pending | 1 → 1 | nothing (confirm screen blocked in background) |
| TV | C | allow | foreground | **0 INSTALL_SUCCEEDED** | 1 → 2 | nothing |
| TV | D | allow | onStop | **0 INSTALL_SUCCEEDED** | 2 → 3 | nothing |
| Tablet | A | default | foreground | -1 pending | 1 → 1 | same "unknown apps from this source" dialog |
| Tablet | B | default | onStop | -1 pending | 1 → 1 | nothing (`BAL_BLOCK` in logcat) |
| Tablet | C | allow | foreground | no result in 15 s | 1 → 1 | **Google Play Protect: "App blocked to protect your device — Play Protect hasn't seen an app from this developer before."** More details → "Install anyway" |
| Tablet | D | allow | onStop | -1 pending | 1 → 1 | nothing (`BAL_BLOCK`) |
| Tablet | C2 (after dismissing C's dialog with OK) | allow | foreground | 3 `INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed` | 1 → 1 | nothing |
| Tablet | D2 | allow | onStop | -1 pending | 1 → 1 | nothing |
| Tablet | C3–C5 (retries, ~2 min apart) | allow | foreground | 3 `INSTALL_FAILED_ABORTED: Permission denied` | 1 → 1 | the same Play Protect block when it appeared at all (pre-expanded, closes by itself within seconds); refused without a dialog in between |
| Tablet | C6 | allow | foreground | 3 `Permission denied` | 1 → 1 | dialog caught and **"Install anyway" tapped** (user-approved) — dialog closed, install still refused |

## Answers
1. **Silent self-update of an adb-installed app:** yes on the TV box, once the install app-op is allowed. Not on the tablet: Play Protect verifies every app-driven session install and blocks an app from a developer (signing key) it has never seen.
2. **From onStop (background):** works on the TV box. When anything needs the user (permission, Play Protect), the confirm screen cannot appear from the background (`Background activity launch blocked`).
3. **`REQUEST_INSTALL_PACKAGES` is needed** on both devices on top of `UPDATE_PACKAGES_WITHOUT_USER_ACTION`; `adb shell appops set <pkg> REQUEST_INSTALL_PACKAGES allow` grants it.
4. **Tablet:** HyperOS itself showed no scanner of its own; the block is Google Play Protect. HyperOS also rejects `adb install` of a *new* package unless Developer options → "Install via USB" is on (user enabled it for this spike; updates of an installed package were never affected).
5. TV box: Android 14 / SDK 34.

## Why adb installs never met Play Protect
`settings get global verifier_verify_adb_installs` = `0` on the tablet: adb installs are not verified, app-driven session installs are. Every Mediagram install so far came by adb, so Play Protect has never seen the release key.

## Recommendation
On phones/tablets with Google Play Protect, a silent self-update is not reachable for an app whose signing key Google has never seen: every session install is verified, the block cannot be shown from the background, and in the foreground even "Install anyway" did not let it through here. Pending the user's decision on phones/tablets. TV boxes: the plan works as written, with the app-op granted by adb at setup. Task 4.6 as written (SecurityException → NeedsPermission) does not match what devices do: a missing app-op returns `STATUS_PENDING_USER_ACTION`, which the plan's confirm-intent path already handles.

## Unresolved
- Why "Install anyway" did not take (tap missed a span, or Play Protect refuses it for app-driven session installs) — not pursued further; repeated attempts made Play Protect refuse without a dialog.
- Does registering the key with Google (developer verification / Play Protect submission) make it trusted?
