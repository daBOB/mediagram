# Phase 09 — Docs, version, and the three-surface walk

**Spec:** `../spec.md` §6 and Release. **Status:** pending. **Depends on:** phases 01–08.

## Tasks

### Task 9.1: Docs

- [ ] **`docs/mlib-spec.md` §7b, `#mlib-together v=1`.**
  - The marker and JSON, with every field.
  - Unpinned; one message per session, edited in place; the last edit wins.
  - Discovery: the newest 100 messages, never search (and why).
  - Liveness: playing until 2 minutes past the title's end, or 6 hours with no duration; paused for 3 hours.
  - Stale messages are deleted by whoever next reads them; the last screen to leave writes `ended: true` and deletes.
  - Index and state readers skip the marker.
  - Section 8's versioning rule applies: a later change bumps the marker.
- [ ] **`docs/system-architecture.md`: a "Watch together" section after Sync.**
  - The web is the reference, `web/public/lib/together/`, ported to Kotlin in `core/model` and held to `web/test/fixtures/together/`. The Rust core only does I/O.
  - Push: together updates skip the 5 s debouncer on both surfaces.
  - The echo rule.
  - Viewer intent: on the web, the player's call paths; on Android, `ViewerPlayer` plus the speed and restart hooks.
  - **Deliberate differences, written down:**
    - the Android follower lives in `feature/player`, because of the no-feature-to-feature rule;
    - the core keeps no rules.
- [ ] **`docs/web-player.md`:** the tool, the label, the notices, the Home notice, Join on title pages, `/api/together` and the `together` SSE event.
- [ ] **`docs/project-changelog.md`:** a 0.120.0 entry.
- [ ] **Commit:** `docs: watch together`

### Task 9.2: Version

- [ ] **Bump to 0.120.0** in `Cargo.toml` (line 6), `web/package.json` (line 3) and `android/app/build.gradle.kts` (`versionName`), by pattern rather than an exact old string (memory: another session commits to main).
  - If a newer version than 0.119.2 is on main by then, take the next minor above it.
  - Refresh `Cargo.lock` with `cargo metadata`.
- [ ] **Run `scripts/check.sh`.** It covers clippy `-D warnings`, `cargo test --all`, bun lint and test, and the gradle unit tests and lint. Expected: all green. If anything fails, fix it; never skip it.
- [ ] **Commit:** `chore: release 0.120.0 — watch together`

### Task 9.3: Install and walk

- [ ] **Rebuild the native core** (`ANDROID_NDK_HOME=… scripts/generate-android-bindings.sh`), then install debug on the tablet, always with `ANDROID_SERIAL=caad49da`.
- [ ] **The TV box (`192.168.0.46:5555`) updates from the channel.** That needs a release, which is the user's call: **ask before `scripts/release-android.sh`**. Until then, the walk can use the tablet, a browser on the live player (`:8770`), and the TV emulator.
- [ ] **The walk.** Use profile `test` (PIN 1357) on the tablet and "TV test" on the box.
  - On the box: navigate only (arrows, OK on plates, Back). Never change a setting, and never tap "Start over".
  - Never send keys to the box while another session is using it.
  - Chain adb keys in one call: the controls hide.

  The steps:
  1. Start on the browser: the tablet's Home shows the Join row within a few seconds, and the TV's Home shows the plate.
  2. Join on the tablet: within 1 s it is at the same position as the browser.
  3. Pause, play, seek and set 1.5× from each surface in turn: the others follow, and the notices name the right screen.
  4. Let an episode end: every screen moves to the next one.
  5. Join late on the TV.
  6. Leave on the tablet: the label drops "Tablet".
  7. Leave on the others: the last one deletes the message. Check the channel: no `#mlib-together` message is left.
  8. **A stale session.** Start one on a short title, force-stop the app on both Android devices (`am force-stop`), and close the browser tab. Once the expected position is more than 2 minutes past the title's end, the next Home on any surface deletes the message.
  9. **The end of the last title.** Let a film play out on one screen: the session ends on every screen.
  10. Continue: each device has its own position for its own profile.
- [ ] **Write the walk's results** in `plans/261008-2357-watch-together/reports/device-walk-261009-watch-together-report.md`, then add a review section to `plan.md`.

## Todo

- [ ] 9.1 Docs
- [ ] 9.2 Version and check
- [ ] 9.3 Install and walk

## Success criteria

- check.sh is green.
- The walk passes on all three surfaces.
- No `#mlib-together` message is left behind.
- The TV release happens only on the user's say-so.

## Pre-flight rulings (binding; they override the text above where they differ)

- **Walk step 8 doesn't touch the box:** `am force-stop` goes beyond "navigate only" on the box. Run step 8 with the tablet (`ANDROID_SERIAL=caad49da`) and the browser (or the TV emulator), and leave the box out of that session.
- `system-architecture.md`: anchor the section after `### Watch state` (~520), and list every deliberate difference the phases recorded: the follower in `feature/player`, the core keeping no rules, and the web keeping the event stream open while a hidden tab is in a session.
