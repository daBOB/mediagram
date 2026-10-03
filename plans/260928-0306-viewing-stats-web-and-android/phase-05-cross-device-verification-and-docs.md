# Phase 05 — Cross-device verification and docs

**Goal:** prove on real devices that minutes watched on one surface show on the others, that
nothing double-counts after sync rounds, and record the feature in the docs.

**Context:** [shared-contract.md](shared-contract.md) §1, §3, §4, §6 · phases 02 (web) and 04
(Android) merged into `feat/viewing-stats` · memory rules: pin every adb command to a serial
(tablet `caad49da`, TV box `192.168.0.35:5555`); device walks navigate only, never change a
setting; test plays use the tablet's "test" and the box's "TV test" profile (they land in
Continue — note them for the user); never start the real web player for UI work (stub harness
`cd web && bun run preview`), but this phase needs the real one because it tests sync.

**Bump:** none (no code). If a defect turns up, fix it in the owning phase's files with its own
test and a patch bump.

## Global constraints

Copied from contract §8: no plan references in code/tests/commits; web CEILINGS never raised;
Rust ≤ 200 lines per non-test file; versions bumped by pattern; branch `feat/viewing-stats`.

## Review focus

1. A title watched on the TV for ~3 min appears on the web's Stats page for the same profile
   after one sync round, with ~3 min — not 6 after a second round (gossip re-export must not
   double-count).
2. The same profile watching on two devices on the same day: the day bar is the sum.
3. A title finished on the tablet shows "Finished" on the web; started again on the web it
   shows "Watched again".
4. An older build reading a document with `titleStats`/`dayStats` keeps working (drops the keys).
5. A server restart mid-title: the first write after restart counts 0 s, the rest count.

## Task 5.1: Build and install the branch

- [ ] **Step 1:** `scripts/check.sh` on the branch — expected: all green.
- [ ] **Step 2:** Web: restart the real player from the branch (`docs/running-the-player.md`).
- [ ] **Step 3:** Tablet: `scripts/build-android-core.sh` then
  `cd android && ANDROID_SERIAL=caad49da ./gradlew :app:installDebug`.
- [ ] **Step 4:** TV box: it runs the self-updating release build. Either publish the branch's
  version with `scripts/release-android.sh` **only after asking the user** (a channel release
  reaches every TV), or install the release APK by adb for the test:
  `adb -s 192.168.0.35:5555 install -r android/app/build/outputs/apk/release/app-release.apk`
  (needs a versionCode ≥ the installed one).

## Task 5.2: Record on each surface, read on the others

- [ ] **Step 1:** TV box, "TV test": play a film 3 min, stop. Settings › System › force a sync
  is not available — wait for the round (or relaunch, which syncs at start).
- [ ] **Step 2:** Web, same profile name: Stats page → "This week" ≥ 3 min, today's bar
  non-zero, history "Started · <film> · today hh:mm · 3 min".
- [ ] **Step 3:** Wait for two more sync rounds (or relaunch twice); reload the web Stats page —
  expected: the same minutes, not doubled. Record the numbers.
- [ ] **Step 4:** Tablet, "test": play an episode to its end (seek near the end, let it finish).
  Web: "Finished · <episode>". Then start it again on the web for 30 s: web history
  "Watched again · <episode>".
- [ ] **Step 5:** Tablet and TV Stats pages (rail → Stats): same totals and history as the web,
  within one sync round. Screenshot each (`adb exec-out screencap -p`).

## Task 5.3: Old reader and restart

- [ ] **Step 1:** Old readers drop the keys: download one real `watch-state.json` from the
  channel that carries `titleStats`/`dayStats`, and in a scratch worktree of `main`
  (`git worktree add ../mediagram-old-reader main`) parse it with the old code —
  `cd web && bun -e 'import {parseRecord} from "./src/state/sync-record.ts"; const r = parseRecord(await Bun.file(process.argv[1]).text()); console.log(r === null ? "REJECTED" : JSON.stringify(Object.keys(r.profiles[0])))' <file>`
  — expected: keys without `titleStats`/`dayStats`, not `REJECTED`. Remove the worktree.
- [ ] **Step 2:** Restart the web player mid-title (play, restart server, keep playing 1 min):
  Stats shows ~1 min for that stretch, not the downtime.

## Task 5.4: Docs and close

- [ ] **Step 1:** `docs/system-architecture.md` § sync: the two new keys, gossip with per-device
  rows, why totals cannot double-count. § Android/web player: the Stats page.
- [ ] **Step 2:** `docs/web-player.md`: the Stats rail item and `GET /api/profiles/{p}/stats`.
  The same commit adds `"#/stats": "stats"` to the `kinds` map in `web/test/address.test.ts`
  (that test checks every route the doc names); run `cd web && bun test test/address.test.ts`.
- [ ] **Step 3:** `docs/project-changelog.md`: entries for the phase releases (02, 04, 06, 07).
  `docs/development-roadmap.md`: a "Viewing stats" section.
- [ ] **Step 4:** Write `reports/cross-device-verification-results.md` (per device: numbers
  observed per step, sync rounds, screenshots taken, the test plays left in Continue).
- [ ] **Step 5:** Update `plan.md` statuses. Commit `docs: viewing stats across web, phone and TV`.

## Success criteria

Minutes and history recorded on any one surface appear on the other two for the same profile
within one sync round, unchanged by later rounds; finish and watched-again lines match; nothing
on an older reader breaks.
