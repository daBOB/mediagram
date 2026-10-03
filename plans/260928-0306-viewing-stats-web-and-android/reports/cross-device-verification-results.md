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

## Still to do

- TV box `192.168.0.35:5555`: install the release APK (adb, or a channel release — the user's call), Stats walk, Back from a **Ready** Stats page lands on the Stats rail row, dot on the collapsed rail, TalkBack reads "Stats, New achievement".
- Web: the live player on 0.99.2 (needs a restart of the real player), then cross-device: minutes recorded on the tablet appear on the web for "test" after a sync round and stay the same after two more rounds; a finish on one surface shows "Finished", a restart "Watched again".
- Web vs Android achievements for the same profile (should match exactly).
