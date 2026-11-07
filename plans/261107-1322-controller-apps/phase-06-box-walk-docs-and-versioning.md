# Phase 06 — Box walk, docs, versioning

**Priority:** proves the spec's success list. **Status:** todo.

**Files:**
- Create: `docs/game-night-controller-apps.md` (household how-to)
- Modify: `docs/project-changelog.md`
- Propose (user sign-off, CLAUDE.md §4): `CLAUDE.md` § Versioning + `## Changelog` line

- [ ] **Step 1: Catch up.** `git merge feat/spieltisch`; resolve; `bun test` (client), `./gradlew :feature:games:testDebugUnitTest :controller:testDebugUnitTest`, `swift test` (ControllerCore) → all PASS. Bump to a version above both `main` and `feat/spieltisch` (by pattern).
- [ ] **Step 2: Box build.** `scripts/build-android-core.sh` (with `ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358`) so the box gets the new phone page; then `ANDROID_SERIAL=<box ip>:5555 ./gradlew :app:installRelease` — box only. (Box address moves; `nmap -p 5555` the LAN if unreachable.)
- [ ] **Step 3: Announcement on the real box.** Start game night on the TV; from this machine `avahi-browse -rt _mediagram-game._tcp` → one `Spieltisch` entry with `url=http://<box ip>:8080/`. Leave game night → the entry disappears. If nothing appears, stop and report before going further.
- [ ] **Step 4: Real walk** (spec § Success), with an Android phone, an iPhone and the iPad mini, plus one phone in a plain browser via QR:
  - app → lobby within a few seconds, no typing or scanning (after the first name);
  - a full Heimlauf or quiz game: screens stay on (phone screen timeout set short), buzz at your turn / buzzer accepted / wrong answer / game won and at no other time;
  - the browser phone behaves as before (QR, hint, no change);
  - drawing check on the iPad mini with the Pencil.
  Record results and device serials in `plans/261107-1322-controller-apps/reports/box-walk-261107-report.md`.
- [ ] **Step 5: Household doc** `docs/game-night-controller-apps.md` (German headings are fine, plain words): installing on Android (APK/adb), installing on iPhone/iPad (xtool or Sideloadly), **the weekly refresh: run `scripts/refresh-ios-controllers.sh` before game night**, what to do when the app says "Warte auf den Fernseher …" (same Wi-Fi, VPN off, QR button).
- [ ] **Step 6: Changelog** entry in `docs/project-changelog.md` for this version.
- [ ] **Step 7: Versioning proposal.** Android's controller derives its version from `Cargo.toml`, so it adds no manifest. The iOS `Info.plist` does. Show the user the diff adding it to CLAUDE.md § Versioning's list (plus a `## Changelog` line) and write it only after sign-off.
- [ ] **Step 8: Commit** docs + report; final review of the branch (code-reviewer agent) before telling the user it is done. Do not merge into `feat/spieltisch` or `main` without the user.

**Success:** every bullet of spec § Success observed on real devices and written in the walk report.
