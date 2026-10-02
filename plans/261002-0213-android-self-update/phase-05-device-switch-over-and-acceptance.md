# Phase 05 — Device switch-over and acceptance

Needs the user present: the TV box on and reachable, the tablet connected. Memory rules apply: every command pinned to a serial; TV key scripts stop at the first unexpected screen; device walks never change settings; test plays land in Continue — use the tablet's "test" profile and the box's "TV test" profile.

## Context
- Spec § Rollout 3–4. Devices: tablet `caad49da` (debug build today), TV box `192.168.0.35:5555` (benchmark build today). Both signed with the release key, so a release build installs over either keeping data and the Telegram session.

## Task 5.1: Smoke-test the release build before anything is published

- [ ] **Step 1: Build** `scripts/build-android-core.sh` then `cd android && ./gradlew -q :app:assembleRelease`.
- [ ] **Step 2: Switch the tablet** (debug → release, same key, higher or equal versionCode → install keeps data):

```bash
adb -s caad49da install -r android/app/build/outputs/apk/release/app-release.apk
adb -s caad49da shell appops set com.mediagram.android REQUEST_INSTALL_PACKAGES allow   # only if Phase 00 found it needed
adb -s caad49da shell monkey -p com.mediagram.android -c android.intent.category.LAUNCHER 1
```
Expected: launches signed in, the catalog loads, a title plays (test profile), Settings › System shows `Updates · up to date · checked just now` (or `not checked yet` until the first check finishes). If the install fails with `INSTALL_FAILED_VERSION_DOWNGRADE`, the debug build on the device is newer: ask the user before `install -r -d`.

- [ ] **Step 3: Switch the TV box** the same way with `-s 192.168.0.35:5555`, then `adb -s 192.168.0.35:5555 shell cmd package compile -m speed -f com.mediagram.android` (as the benchmark build had). Expected: launches on "TV test", plays, the Updates row is on the System section.

## Task 5.2: A real release reaches both devices on its own

- [ ] **Step 1: Make a release to publish.** Bump patch by pattern (Phase 01 Task 1.2 Step 5 block), commit `chore: release $V`, then:

```bash
scripts/release-android.sh
```
Expected: the certificate check passes, `published 0.x.y (versionCode …) as message …, pinned`.

- [ ] **Step 2: Each device picks it up.** For each serial: bring the app to the front (it checks on foreground; the hourly throttle resets on a fresh process — `adb shell am force-stop` then launch if the last check was under an hour ago), wait for `ready · installs when you leave the app` on Settings › System, then press Home:

```bash
adb -s $S shell input keyevent KEYCODE_HOME
sleep 20
adb -s $S shell dumpsys package com.mediagram.android | grep -m2 -E 'versionName|versionCode'
```
Expected: the new versionName/versionCode, no prompt on screen (screenshot), and on relaunch the app is still signed in with its catalog.

- [ ] **Step 3: Nothing happens during playback.** Publish one more patch release; on the tablet start a title (test profile) *before* the app's next check, force a check by relaunch while it plays, and confirm via the Updates row that it does not say `downloading`; stop playback, background the app, confirm it then downloads on the next foreground and installs on the next background.

## Task 5.3: Record and close

- [ ] **Step 1:** Write `plans/261002-0213-android-self-update/reports/device-acceptance-results.md`: per device — Android version, switch-over result, each publish observed (version, minutes from publish to installed), playback check, any prompt or HyperOS dialog seen.
- [ ] **Step 2:** Update `plan.md` phase statuses; `docs/development-roadmap.md` "Android: shipping" (`:159`) — self-update done; memory note: devices now self-update, release via `scripts/release-android.sh`, family devices need the release build installed once by adb.
- [ ] **Step 3:** Commit `docs(plan): Android self-update verified on the tablet and the TV box`.

## Success criteria
Both devices moved from one published release to the next with no cable, no prompt and no new login; nothing downloaded or installed during playback.
