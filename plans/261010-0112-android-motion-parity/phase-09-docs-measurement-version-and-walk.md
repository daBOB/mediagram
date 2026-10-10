# Phase 09 — Docs, measurement, version and the tablet walk

**Spec:** `../spec.md` §3 (Speed, Tests, Delivery). **Status:** pending. **Depends on:** 01–08.

## Context

- **`benchmark` build type** (`app/build.gradle.kts:61-75`): minified and not debuggable, so ART compiles it the way a release runs. It's signed with the debug key and so installs over the tablet's debug install, keeping the Telegram session.
- **Device rules:**
  - always `ANDROID_SERIAL=caad49da`;
  - check that the avatar shows T (PIN 1357) before playing anything;
  - never tap "Start over";
  - change only the two settings this phase names (animator scale, backdrop), and restore each to the value read before.
- **Version:** main is at 0.119.2 as this is written. `feat/watch-together` (0.120.0) and the TV and glass branches may merge first. Take the next minor above whatever main carries at merge, and bump by pattern, not by an exact old string: another session commits to main.

## Task 9.1: Measure frame times and settle the Blurred risk

**Files:**
- Create: `plans/261010-0112-android-motion-parity/reports/frame-times-261010-motion-report.md`
- Modify (only if the fallback is needed): `ui-mobile/src/main/kotlin/ui/catalog/home/CoverSlide.kt`

- [ ] **Step 1: Read the two settings you may change, so you can restore them**

```bash
export ANDROID_SERIAL=caad49da
adb shell settings get global animator_duration_scale   # note it; usually 1.0 or null
```

On the tablet, note the current Settings › Appearance › backdrop, and leave it as you found it at the end.

- [ ] **Step 2: Baseline on main.** From the main checkout (`/home/andre/Workspace/mediagram/android`), install the benchmark build and measure Home.

```bash
cd /home/andre/Workspace/mediagram/android && ./gradlew :app:installBenchmark
adb shell am start -n com.mediagram.android/.MainActivity
# Check the avatar (T). Wait on Home 20 s, cover visible, then:
adb shell dumpsys gfxinfo com.mediagram.android reset
sleep 30
adb shell dumpsys gfxinfo com.mediagram.android | grep -E 'Total frames|Janky|50th|90th|99th'
adb shell dumpsys gfxinfo com.mediagram.android reset
for i in 1 2 3; do adb shell input swipe 1200 1500 1200 300 120; sleep 1; done
for i in 1 2 3; do adb shell input swipe 1200 300 1200 1500 120; sleep 1; done
adb shell dumpsys gfxinfo com.mediagram.android | grep -E 'Total frames|Janky|50th|90th|99th'
```

Do this with the backdrop the tablet already has, then once more with Blurred if that isn't it.

- [ ] **Step 3: The same on `feat/motion`.** Run the same commands from `../mediagram-motion/android`, with Default and with Blurred.

- [ ] **Step 4: Write the report.** One table, with rows for each combination of build (main, motion), backdrop (as found, Blurred) and walk (idle cover, scroll), and columns for total frames, janky %, and the 50th, 90th and 99th percentiles.

- [ ] **Step 5: Rule on Blurred.** The cover drift janks under Blurred if, on the idle walk, the motion build's 90th percentile exceeds 16 ms where main's does not, or its janky share rises by more than 10 points. If it does:
  - In `CoverSlide.kt`, apply the drift only when the backdrop isn't Blurred: `if (LocalBackdrop.current == Backdrop.BLURRED) Modifier else Modifier.drift(1.06f, 1f, 14_000, Motion.EaseOut)`. Add a comment saying the blur re-renders under a scaling layer on every frame, citing the measurement report.
  - Rebuild, re-measure that one row, and record it.
  - Add the difference to Task 9.2's list.
  - Commit: `fix(motion): the cover holds still under the Blurred backdrop`.

- [ ] **Step 6: The frosted masthead's fair measurement**, while the tablet is set up. Install the benchmark build from `../mediagram-glass/android` (`feat/frosted-masthead`) and run the scroll walk once. Add the row to the same report. This only records numbers; that branch's merge is decided separately.

- [ ] **Step 7: Leave the tablet on `feat/motion`'s benchmark build** for the walk, and commit the report:

```bash
git add plans/261010-0112-android-motion-parity/reports/frame-times-261010-motion-report.md
git commit -m "docs(plan): frame times on the tablet with the web's motion"
```

## Task 9.2: Docs

**Files:**
- Modify: `docs/system-architecture.md` (§ "Parity with the web player", lines 693-706 as of writing)
- Modify: `web/DESIGN.md:229` and `:292`
- Modify: `docs/project-changelog.md` (a new entry at the top)

- [ ] **Step 1: `docs/system-architecture.md`.** After the § "Parity with the web player" paragraph, add:

```markdown
**Motion.** The phone and tablet move as the web player does: the web's tokens
(`--ease`, `--ease-out`, `--hover`) and its entrances, drifts, hover lifts and
nudges live once in `core/designsystem` (`Motion.kt`, `Entrance.kt`, `Drift.kt`,
`HoverMotion.kt`, `CatalogueIndication.kt`), and each screen applies them at its
own call sites. Ripple is gone: `MediagramTheme` provides the web's press
feedback (a quiet tint, a 2dp focus ring, a snapped 0.98 press), and Material's
buttons are drawn through `ui.chrome`'s `Catalogue*Button` wrappers, since they
hard-code their ripple. "Remove animations" is the web's reduced motion. What
differs on purpose:

- **The player's controls linger 4 s, not the web's 2.6 s.** A finger has to
  reach the controls; a mouse is already there.
- **The phone's compact header is quick-return.** It hides on scroll down and
  returns on any scroll up; the web's phone masthead only reappears at the top.
  The phone's screen is short.
- **The cover holds still for TalkBack** (`HomeCover.kt`).
- **The player says "Loading…" while a title opens**, where the web shows
  nothing: a big file can take seconds to start over Telegram, and a silent
  black stage reads as broken.
- **The first page after a cold start rises in**, where the web's first draw
  is still.
```

Append any difference that phases 03–08 or Task 9.1 recorded: the Blurred cover, and dialogs if their window animation couldn't be removed. Each gets one line with its reason.

- [ ] **Step 2: `web/DESIGN.md`.** Line 229 currently reads `- Reduced motion: all transitions collapse to 1ms`. Change it to:

```markdown
- Reduced motion: none at all — every animation and transition is off (`theme.css:180-182`)
```

Line 292 currently reads ``- **Do** honour `prefers-reduced-motion` by collapsing all motion to 1ms``. Change it to:

```markdown
- **Do** honour `prefers-reduced-motion` by turning all motion off
```

- [ ] **Step 3: `docs/project-changelog.md`.** Add a new top entry in the house style (see the 0.119.x entries). Use the version from Task 9.3:

```markdown
## 0.N.0 — the phone and tablet move like the web player

**Changed** (Android phone and tablet)

- **The web's motion, on the web's clock.** Pages rise in as they open, Home's
  sections rise as they scroll into view (once a visit), the cover fades up from
  black and slowly settles, and hero art eases in with its pull-quote after it.
  The Featured poster drifts once per slide and holds when paused, instead of
  looping. Arrows nudge toward where they lead; with a mouse or stylus, posters
  lift and artwork zooms.
- **Presses answer as on the web: a quiet tint and a snapped 0.98, never a
  ripple.** Menus fade in and drop 4dp and close at once; in the player they
  open and close without motion. Tabs fade their underline instead of sliding
  one. Loading shows still text, not a spinner, and dialogs open without the
  window animation.
- **The player's controls fade in and out over 260 ms**, and ignore taps once
  they start to fade.
- **"Remove animations" stops all of it**, as reduced motion does on the web.
  The TV is unchanged; it adopts the same module in its own parity work.
```

Add the frame-time result in one line under it, with the report path.

- [ ] **Step 4: Commit**

```bash
git add docs/system-architecture.md web/DESIGN.md docs/project-changelog.md
git commit -m "docs: the phone and tablet move like the web player"
```

## Task 9.3: Version and check

- [ ] **Step 1: Bump by pattern** to the next minor above main's current version, in all three manifests at once:

```bash
cd /home/andre/Workspace/mediagram-motion
cur=$(git show main:Cargo.toml | sed -n 's/^version = "\(.*\)"/\1/p' | head -1)
next=$(echo "$cur" | awk -F. '{print $1"."$2+1".0"}')
sed -i "0,/^version = \".*\"/s//version = \"$next\"/" Cargo.toml
sed -i "0,/\"version\": \".*\"/s//\"version\": \"$next\"/" web/package.json
sed -i "s/versionName = \".*\"/versionName = \"$next\"/" android/app/build.gradle.kts
cargo metadata --format-version 1 >/dev/null
git diff --stat   # Cargo.toml, Cargo.lock, web/package.json, android/app/build.gradle.kts
```

If the branch is rebased onto a main that has moved by merge time, redo this step on top of it.

- [ ] **Step 2: Run the full check**

Run: `scripts/check.sh`
Expected: every step green: clippy, cargo test, bun lint and test, gradle unit tests, lint, detekt. If anything fails, fix it; never skip it.

- [ ] **Step 3: Commit**

```bash
git add Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts
git commit -m "chore: release $next — the phone and tablet move like the web player"
```

## Task 9.4: The tablet walk

**Files:**
- Create: `plans/261010-0112-android-motion-parity/reports/device-walk-261010-motion-report.md`
- Modify: `plans/261010-0112-android-motion-parity/plan.md` (Review section)

- [ ] **Step 1: Install the debug build** from the worktree (`./gradlew :app:installDebug` with `ANDROID_SERIAL=caad49da`). Open the app and check the avatar shows T.

- [ ] **Step 2: Walk with animations on.** For each line, record pass or fail and what you saw.

1. **Home:**
   - the cover's new slide fades up from black and then slowly settles;
   - a tap mid-fade does nothing;
   - the pause button holds it.
2. **Scroll down Home:** each section rises in once. Scroll back up and none replays. A pull-to-refresh or sync landing replays nothing.
3. **Continue strip:** a fling ends on a card's start. "See all →" nudges right while pressed.
4. **Press feedback:** a pill, a plate, a dialog's button, and a radio row in Settings each show the quiet tint and a 0.98 snap, and no ripple anywhere. Links (see-all, a genre tile, a person) tint but don't shrink.
5. **A title:**
   - the hero art eases in, and the pull-quote follows;
   - "▶ Play" nudges on press;
   - the back arrow nudges left;
   - switching tabs fades the underline (no slide) and the content rises 8dp;
   - ⋯ opens with a fade and a 4dp drop, and closes at once.
6. **A department:** the hero eases in, each row has its "→", and the "All N →" pill nudges.
7. **Movies' Featured reel:**
   - slides cross-fade;
   - the poster drifts once and holds when paused;
   - the copy rises in a stagger.
8. **The player:**
   - the controls fade in and out (260 ms);
   - a tap on fading-out controls does nothing;
   - the marks menu opens and closes with no motion;
   - the tint over video is white;
   - nothing else moves over the picture.
9. **Dialogs and loading:** a dialog (e.g. Settings › Sign out, then Cancel) opens without the window animation. The library load shows "Loading your library…" as still text.
10. **Light theme, briefly:** the press tint is visible on a pill. Restore the theme you found.
11. **Hover:** with a mouse or stylus, a poster lifts and a feature's art zooms. If neither is at hand, write "not checked on device; covered by unit tests".

- [ ] **Step 3: Walk with "Remove animations" on**

```bash
adb shell settings put global animator_duration_scale 0
adb shell am force-stop com.mediagram.android && adb shell am start -n com.mediagram.android/.MainActivity
```

Check that:
- nothing fades, rises, drifts or nudges;
- no content is left invisible or half-moved;
- the reel's poster sits at its normal size;
- the masthead over the cover is solid;
- the cover doesn't rotate.

Then restore the value read in Task 9.1 Step 1 (`adb shell settings put global animator_duration_scale <value>`, or `settings delete global animator_duration_scale` if it was null).

- [ ] **Step 4: Write the walk report** at the path above: one line per check, the build version, and the restored settings.

- [ ] **Step 5: Fill in `plan.md`'s Review section.** Cover:
  - what shipped;
  - every ruling made during execution;
  - the differences written down;
  - the frame-time headline;
  - anything left for the TV batches 3 and 4.

- [ ] **Step 6: Commit**

```bash
git add plans/261010-0112-android-motion-parity
git commit -m "docs(plan): the motion walk on the tablet"
```

## Success criteria

- `scripts/check.sh` is green on `feat/motion` at the bumped version.
- The walk passes with animations on and off, and every device setting is restored.
- The frame-time report exists, and the Blurred ruling is recorded either way.
- Merging and pushing are the user's call. Release to the TV channel isn't part of this plan; phones and tablets install by adb.
