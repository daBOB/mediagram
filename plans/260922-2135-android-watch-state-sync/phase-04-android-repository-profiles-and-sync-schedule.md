# Phase 04 — Android repository, profiles, sync schedule

## Context links

- `android/core/data/src/main/kotlin/CoreClient.kt:17-85`, `DefaultCoreClient.kt`, `CatalogRepository.kt:71-114` (pattern: map UniFFI records to `core:model`)
- `android/core/data/src/main/kotlin/CoreStorage.kt:62-82` (Start over deletes `session.key`, `catalog/`, `libraries.json`)
- `android/feature/setup/src/main/kotlin/SetupViewModel.kt:126-129` (Start over order)
- `android/app/src/main/kotlin/com/mediagram/android/MainActivity.kt:28-46` (single activity)
- `web/public/lib/profile-picker.js:1-102`, `web/public/app.js:633-636` (remembered profile, else ask), `web/public/index.html:48-50` (who button)
- `web/src/index.ts:196-203,328` (start awaited, timer, stopping)

## Overview

- Priority: P1. Status: pending. Blocked by 03.
- Kotlin gets a `WatchStateRepository` over the core, a `WatchSync` scheduler, and the "Who's watching?" picker. After this phase the phone syncs and has a viewer; nothing yet records or shows positions.

## Key insights

- **Profiles:** port the picker. A name is the cross-device identity (`sync-record.ts:56-67`); after the first sync the web's viewers already exist locally (`store.ts:442`), so the picker usually offers the right name with zero typing. A default "Everyone" would silently merge two people's Continue shelves on the phone.
- Rename/delete are **deliberately not ported now**: sync cannot express either (a renamed viewer is a new viewer on every other device; a deleted one comes back on the next merge). Written down in 09's parity note.
- The phone is killed, not closed. Web's "stopping" becomes activity `onStop` (best effort) plus a sync after leaving the player (phase 05 calls it).
- Chosen profile is stored in the core (`state_meta`), so it goes with `state.db` rather than living in a second place.

## Requirements

- Functional:
  - Repository: `snapshot: StateFlow<WatchSnapshot>` for the chosen profile; `profiles()`, `choose()`, `create(name)`; write methods (`setProgress`, `clearProgress`, `setWatched`, `setWatchlisted`, `setKids`, list ops) update the flow immediately and persist on `Dispatchers.IO`.
  - `WatchSync`: `onForeground()` → one round now, then every 5 min; `onBackground()` → cancel timer, one last round in the app scope; `soon()` → one round (after playback). Rounds are serialized; after a round with `pulled > 0` the repository reloads its snapshot.
  - Runs only when a library handle is chosen and the core is authorised.
  - Picker: shown after setup when no profile is chosen; waits for the first round up to 5 s (spinner) so synced viewers appear; lists viewers, "Add a viewer" with a name field. Bar action shows the current name; tapping re-opens the picker.
- Non-functional: every core call wrapped `runCatching` (rethrow `CancellationException`); a failed round logs `Log.w("sync", …)` and changes nothing. Files < 200 lines.

## Architecture

```
MainActivity.onStart/onStop ─► WatchSync ─► CoreClient.syncState(handle) ─► core (03)
                                   │ pulled>0
                                   ▼
PlayerVM / CatalogVM ─► WatchStateRepository ─► CoreClient state calls ─► core (02)
                           └─ StateFlow<WatchSnapshot> (chosen profile)
ProfilePickerScreen ─► ProfileViewModel ─► WatchStateRepository
```

Lifetime: repository and `WatchSync` are `@Singleton` (process). Snapshot is per chosen profile; switching profile reloads it.

## Related code files

- Create: `core/model/src/main/kotlin/WatchSnapshot.kt` (Progress, Watched, ListOfSets, Profile), `core/data/src/main/kotlin/WatchStateRepository.kt`, `core/data/src/main/kotlin/WatchSync.kt`, `feature/catalog/src/main/kotlin/ProfileViewModel.kt`, `ui-mobile/src/main/kotlin/ProfilePickerScreen.kt`, tests `core/data/src/test/kotlin/WatchSyncTest.kt`, `WatchStateRepositoryTest.kt`, `feature/catalog/src/test/kotlin/ProfileViewModelTest.kt`.
- Modify: `core/data/src/main/kotlin/CoreClient.kt`, `DefaultCoreClient.kt` (new methods), `core/data/src/main/kotlin/di/DataModule.kt` (bindings), `core/data/src/main/kotlin/CoreStorage.kt` (`STATE_FILE`, only if **Q4** = yes), `ui-mobile/src/main/kotlin/StartOverAction.kt:40-44` (name what goes, if Q4), `app/src/main/kotlin/com/mediagram/android/MainActivity.kt` (lifecycle hooks), `ui-mobile/src/main/kotlin/LibraryFlow.kt` (picker gate), `ui-mobile/src/main/kotlin/AppChrome.kt` (who action).
- Coordination: `AppChrome.kt` is also touched by `260922-2105-settings-menu` phase 08. Not parallel; whichever lands second rebases.

## Implementation steps

1. Rebuild the core: `ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/build-android-core.sh`.
2. `CoreClient` additions mirror 02/03 names in camelCase; `DefaultCoreClient` delegates.
3. `WatchSnapshot` + mapping; repository with in-memory flow, IO writes, `reload()`.
4. `WatchSync` with injected `CoroutineScope`, clock-free `delay`, and a `handle` provider (`LibrarySettings.read()`); tests with a fake `CoreClient` (virtual time: foreground → round; +5 min → round; background → cancel + final round; failed round → no reload; `pulled>0` → reload).
5. `MainActivity`: `onStart` → `onForeground()`, `onStop` → `onBackground()`.
6. Picker VM + screen; `LibraryFlow` shows it before the catalog when `chosenProfile == null`.
7. Q4: add `state.db` (+ `-wal`, `-shm`) to `CoreStorage.clear`; dialog text adds "and where you left off on this device".
8. `./gradlew testDebugUnitTest lint`; `./gradlew :app:installDebug` on the tablet.

## Todo

- [ ] core `.so` rebuilt
- [ ] CoreClient + DefaultCoreClient
- [ ] WatchSnapshot + repository + tests
- [ ] WatchSync + tests
- [ ] MainActivity lifecycle hooks
- [ ] picker VM/screen + LibraryFlow gate + bar action
- [ ] Start over (per Q4)
- [ ] tablet: picker lists the web's viewers after first sync

## Success criteria

- Tablet, first launch after install: picker appears, then lists the web's viewer names within 5 s (with Q2 on).
- Airplane mode: picker still works (create a viewer), app fully usable, logcat shows one `sync` warning per round.
- Unit tests cover schedule and failure paths.

## Risks

| Risk | L×I | Mitigation |
|---|---|---|
| Sync round on main thread | L×H | Core calls only from `Dispatchers.IO`; lint rule-by-review |
| Picker blocks a user offline | M×M | 5 s cap, then show whatever is local |
| Viewer created on phone with a different spelling | M×L | Normalised match (NFC, trim, case) already merges them |
| Final round on `onStop` killed mid-upload | M×L | Next start re-pushes; an edit is idempotent |

## Security

Profile names are not secrets. No new credentials. Sync uses the existing signed-in session and the library handle, never a raw chat id (`library.rs:1-10` handle rule preserved).

## Next steps

05 records positions through the repository and calls `WatchSync.soon()` on leave.
