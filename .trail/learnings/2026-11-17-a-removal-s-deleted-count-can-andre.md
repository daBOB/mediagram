---
title: "A removal's deleted count can be lower than the messages it planned, so verify the removed set instead"
author: Andre
date: 2026-11-17
branch: main
cost: { minutes: 4, tokens: 52705 }
id: l-e9b17c87
scope: repo
status: active
src: agent
touches:
  - Cargo.lock
  - Cargo.toml
  - android/app/build.gradle.kts
  - docs/project-changelog.md
  - docs/system-architecture.md
  - web/package.json
  - crates/mediagram/src/remove/apply.rs
  - crates/mediagram/src/channel_index/live.rs
---

## Decided
`mediagram remove` prints "deleted N message(s)" from what Telegram reports as affected. On 2026-11-17 removing two duplicate film copies planned 7 messages but printed "deleted 5", even though all 7 were gone. A lower count is not proof that messages survived. Check the removed set instead.

## Watch out
`verify <id>` cannot check a removed set because its rows are already gone from library.db. To check it, run the uploader against a scratch config whose data_dir holds an older index backup that still lists the set, plus a copy of session.sqlite. Delete the scratch directory afterwards, because it holds the auth key. If every part says "message not found on the channel", the removal is complete.
The merge line "N channel set(s) no longer exist in the channel, skipped" only proves that at least one part of each set is missing, not all of them.

In the code: crates/mediagram/src/remove/apply.rs (delete_messages sums Telegram's affected count), crates/mediagram/src/channel_index/live.rs (live_sets drops a set if any part is missing). Older index backups are ~/.local/share/mediagram/library.before-channel-merge-*.db.
