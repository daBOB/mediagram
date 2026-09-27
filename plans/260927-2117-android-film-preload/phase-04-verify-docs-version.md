# Phase 04 — Verify on tablet + TV box, docs, version

## Steps

1. Full sweep: `cargo test -p mediagram-cache`, `cd android && ./gradlew
   testDebugUnitTest lint :app:assembleDebug`.
2. Deploy the cache server to the home box with the user (build for its OS/arch, copy
   binary + unit; never install on the dev box). Confirm `GET /v1/sets/{id}`.
3. Tablet (`ANDROID_SERIAL=caad49da`, test profile) and TV box
   (`192.168.0.35:5555`, `installBenchmark` + `compile -m speed`): preload a mid-size
   film end to end; background the app mid-way; play something mid-way; cancel;
   remove; a film larger than the budget (NeedsSpace). Then play the preloaded film with
   the network off and confirm it plays (no Telegram reads).
4. Docs: `docs/project-changelog.md`, DESIGN.md (the control, Android-only note),
   the cache server's route docs, `docs/system-architecture.md` (preload lane).
5. Report in `reports/`: what was verified, timings (MB/s on each device), anything
   evicted, follow-ups (pinning, persisting the queue across process death, a season
   preload).
6. Version bump if anything changed; merge only when the user says.

## Success criteria

A film preloaded on the TV box plays with the network off; the page's bar matched the
real held bytes throughout.
