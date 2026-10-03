# Cross-device verification — viewing stats and achievements (in progress)

Build: `main` 8e76b9e3, release 0.99.2. Started 2026-10-03 11:49.

## Tablet `caad49da` (Redmi Pad Pro, Android 16, HyperOS) — debug build

| Check | Result |
|---|---|
| Native core rebuilt, `:app:installDebug` | ✅ 0.92.2 (vc 18) → 0.99.2 (vc 99002), data kept, launches, no crash / UnsatisfiedLinkError |
| Rail | ✅ "Stats" between Genres and Settings; new-achievement dot lit on profile "andre" (left unseen — that profile's Stats page was not opened) |
| Stats page, profile "test", before playing | ✅ totals "under a minute"; 30 weekday initials (expanded width); Achievements: "First film · 25 Sep"; Next: "5 genres 3 of 5 genres", "10 films 1 of 10 films", "A whole series 5 of 162"; History: "Finished · Justice League · 25 Sep", "Finished · Geldhochschule 1 · 25 Sep" |
| Recording (test play 11:53:41–11:55:12, "All Inclusive", 85 s wall, ~40–60 s of position after initial buffering) | ✅ This week / month / all time "1 min"; today's bar "3 Oct · 1 min" at full height; History "Started · All Inclusive · today 11:53 · 1 min"; Next gains "7-day streak 1 of 7 days"; Continue watching 7 → 8 |
| Back from Stats | ✅ lands on Home |
| `RealCoreContractTest` (`:core:rust:connectedDebugAndroidTest`) | ✅ 34/34 against the real core (incl. stats and achievements contract cases), 12:08 — after the user turned on "Install via USB" (HyperOS had refused the test package with `INSTALL_FAILED_USER_RESTRICTED`); test package uninstalled afterwards |

Screenshots: session scratchpad `tab-stats-test-1.png` (before), `tab-stats-test-2.png` (after the play).
Left behind: one test play of "All Inclusive" on the "test" profile's Continue watching; the tablet is back on profile "andre".

## TV box `192.168.0.35:5555` (Google TV, Android 14) — release build by adb

Built from `main` 96d883d0 (0.99.3 — the merged code plus another session's web-only fix; Android only got the version bump). Release key verified (`5840181d…f576`), arm64 + armv7 only. Installed by `adb install -r` over 0.95.2 — **nothing published to the channel**; `REQUEST_INSTALL_PACKAGES` re-granted, `compile -m speed`. The self-updater sees the channel's pinned 0.95.2 below the installed 0.99.3, so it stays put.

| Check | Result |
|---|---|
| Launch | ✅ no crash / UnsatisfiedLinkError, profile "TV test", catalog loads |
| Collapsed rail | ✅ Stats (bars) between Genres and Settings; no dot on "TV test" — correct, that profile has nothing earned |
| Stats page, "TV test" | ✅ totals "under a minute"; 30 weekday initials; Achievements: nothing earned, Next "A whole series 7 of 162", "5 episodes in a day 0 of 5 episodes", "10 documentaries 0 of 10 documentaries" (ties at 0 ordered by id, per contract); the "7 of 162" bar now visible at full height (the TV progress-rule fix) |
| D-pad through the page | ✅ six presses down reach the history: "Finished · Geldhochschule 6 · 25 Sep" |
| Back from a **Ready** Stats page (focus deep in the history) | ✅ focus lands on the Stats rail row |
| Dot on the collapsed rail, profile "andre" (Stats page not opened, so it stays unseen) | ✅ green dot on the Stats icon; accessibility nodes "Stats" + "New achievement" |

Left behind: box back on profile "TV test", on its launcher home. No playback on the box.

## Channel release 0.99.6 (user's go-ahead, 2026-10-03)

`scripts/release-android.sh` from `main` 1b283ff6: published 0.99.6 (versionCode 99006, 32,728,871 bytes) as channel message 17956, pinned, 17:52. The TV box picked it up after an app restart (21:37:29), downloaded within ~100 s, and installed 4 s after Home (21:39:19) — no prompt, launcher in front, relaunch loads the catalog on "TV test". 0.99.4–0.99.6 (web tokens, TV feature-card corners) were not walked on a device separately.

## Still to do

- TV: TalkBack announcement with the screen reader actually on (uiautomator shows the two nodes; the merged spoken text was not heard).
- Web: the live player on 0.99.2 (needs a restart of the real player), then cross-device: minutes recorded on the tablet appear on the web for "test" after a sync round and stay the same after two more rounds; a finish on one surface shows "Finished", a restart "Watched again".
- Web vs Android achievements for the same profile (should match exactly).
