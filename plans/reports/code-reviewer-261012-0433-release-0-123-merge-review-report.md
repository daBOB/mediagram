# Release 0.123.0 merge review (2026-10-12)

Scope: hand-resolved hunks of the three merges into `release/0.123.0` (feat/motion `dde68da6`, feat/frosted-masthead `9a402c83`, feat/watch-together `49943a17`) and watch together's last fix round `96f5c846..4b608b9b`, never re-reviewed before. Reviewer: code-reviewer subagent, read-only.

## Verdict
No blockers at `7a0a7c26`. Merges keep both sides' intent; the three gaps they opened were already fixed in `7a0a7c26` (TV notice read `Palette.Text`; Home's Join a bare Material `TextButton`; `PlayerCardBoundsTest` missing `together`). Notes 6 and 8 below fixed in the follow-up commit. Published as 0.123.0 with 4 and 5 open.

## Open (should-fix, watch together, only while a screen loses Telegram)
4. **A retried act re-sends a 30–60 s old snapshot over newer edits.** `DefaultTogetherLive.kt:44-45,146`, `TogetherSender.kt:91-95,104,153-159`; web `web/src/together/hub.ts:123-127`, `web/src/together/sender.ts:77-98`. Act moves overwrite set/playing/pos/rate/at/by wholesale and the drain applies them to the newest session without checking another screen edited meanwhile. Scenario: Tablet offline pauses ep 1 at 42 s; TV's up-next opens ep 2; Tablet's retry lands and pulls TV back to ep 1, paused. Fix: on retry, if the session was replaced by another screen's edit, give up the playback part (the lost path) and still send join/leave/end; same on the web.
5. **A given-up bundle drops its leave/end.** `TogetherSender.kt:59,140-148`, `web/src/together/sender.ts:114-122`. Failures count per message, so a leave queued after two failures gets one try. Offline > ~60 s and close the player → departed screen stays listed ("Together · TV, Tablet"); if the other leaves, the session is offered as Join until stale (end + 2 min playing, 3 h paused). Tests only bring Telegram back within 30 s. Fix: never give up leave/end (or re-queue just the leave). `docs/system-architecture.md` now says this is a known gap.

## Notes
6. Duplicate `import player.createListAndAdd` in `PlayerCardBridge.kt` — fixed.
7. A given-up join leaves the screen "inside" a session it is not a member of: `acted` never re-adds membership (`core/model/.../together/TogetherMoves.kt:47-60`); the lost path adopts the session as is (`feature/player/.../together/TogetherFollowing.kt:34`).
8. Taking main's side on `TvDepartmentsBar.kt` dropped the doc line on why the TV bar is unfrosted — restored.

## Checked and fine
Retry: one drain at a time on both surfaces; nothing sooner than 30 s; a failure reverts only if no other edit replaced the session; identical-text retry ("not modified") counts as success; re-reads skipped while busy; own echoes ignored while a move is queued; cancellation rethrown. Merges: PlayPill (motion's button + nudge) used by title, course and series pages; CoursePage/SeriesPage carry list state and Join; card layer keeps fade, PiP return and tap pass-through; TvLibrary menu branch intact inside the together provider; TvToolGroup Right from Framing reaches Watch together and ends there (`TvPlayerRowEndsTest`); row fits beside the episode list; TvHome focus map covers TOGETHER; no frost in ui-tv.

## Unresolved questions
- Fix 4 and 5 before the next channel release, or with the next watch together round?
