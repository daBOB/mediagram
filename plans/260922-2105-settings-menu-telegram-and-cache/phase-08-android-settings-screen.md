# Phase 08 — Android Settings screen

## Context links
- Menu: `android/ui-mobile/src/main/kotlin/AppChrome.kt:35-41` (`Destination`), `:102-114` (`MenuActions`), `:176-196` (items: System, Update library, TMDB key…, Start over)
- `android/ui-mobile/src/main/kotlin/LibraryFlow.kt` (`CatalogAndPlayer`, `MenuScreen` overlay), `MobileApp.kt` (setup states)
- Reusable today: `TelegramApplicationScreen.kt` (id/hash form), `LoginScreen.kt` + `login/LoginViewModel.kt` (phone/code/2FA), `LibraryScreen.kt` + `setup/Libraries.kt` (list/install/chosen), `SystemRows.kt`
- `android/feature/setup/src/main/kotlin/SetupViewModel.kt:124-132` (`startOver`: storage → library → TMDB → identity)
- `android/core/data/src/main/kotlin/CoreProvider.kt:95-102` (`supply` does **not** close a previous core), `:104-115` (`forget` closes)
- `android/core/data/src/main/kotlin/CoreStorage.kt` (deletes session + catalog + libraries together)
- `android/core/playback/src/main/kotlin/CacheProvider.kt:32` (`CACHE_MAX_BYTES` 2 GiB const), `:83` (occupancy), `:92` (`LeastRecentlyUsedCacheEvictor`, fixed)
- `android/feature/catalog/src/main/kotlin/CatalogViewModel.kt:43` (`reload`)
- Phase 06 (web) — the same sections, rows and wording; phase 07 core calls

## Overview
Priority P2. Status: **done 2026-09-23** (uncommitted). Built as specified with these differences:
the cache row is its own `CacheBudgetViewModel` in `feature/system` (already depends on `core:playback`), so
`feature/setup` gains no playback dependency; `CoreProvider.replace` validates a new identity by asking `account()`
(building a core never talks to Telegram, so a mistyped hash would otherwise pass) and on failure closes the candidate
and leaves the stored identity untouched, so the next `awaitCore` rebuilds the old one — no eager rebuild, which would
have put two cores on one data directory. Size choices: 512 MB, 1, 2 (default), 4, 8 GB.
Device check (tablet, read-only as required): menu shows System · Settings · Update library · TMDB key… · Start over;
rows "Serien Junkies" / Mediagram / DC 4 / signed in, Telegram answered; cache 896 MB of 2.0 GB → chose 512 MB →
510 MB of 512 MB (disk 511 MB) → back to 2.0 GB (persisted `cache_budget_bytes`); Change library → Mediagram reinstalled
and returned to the rows. Sign out and id/hash not pressed on the device (tests cover them). A "Settings" menu item opening a `MenuScreen` with the same
two sections as the web page. Almost every part exists already; this composes them.

## Key insights
- Android already decided sign-in, library choice and app identity; Settings reuses those
  screens/ViewModels rather than duplicating them (DRY, Surface Parity).
- Switching library while Ready: `Libraries.install(handle)` installs then writes the handle
  (never forgets first) → `CatalogViewModel.reload()`. No setup-state detour.
- Changing app id/hash: auth key binds to the DC, not the api id (`SetupViewModel` KDoc), so
  the session survives — but the old core must be **closed before** the new one is built
  (one data dir, one auth key). `supply` today never closes; add `replace`.
- Sign out ≠ Start over: keep app identity + TMDB key, drop session + catalog + library
  choice, call core `sign_out` (07) for server-side logout → `recheck()` lands on NeedsSignIn.
- Cache: `LeastRecentlyUsedCacheEvictor` has a fixed max; a live budget needs an evictor
  whose budget can change and which evicts on change. Persist in plain prefs (not secret).
- Player is a full-screen destination without the menu, so settings changes never happen mid-playback on the phone.

## Requirements
- F: menu item "Settings" (after System). `Destination.Settings`, title "Settings".
- F: Telegram rows: Account (name @username via `account()`, "—" on failure), Library (chosen title),
  Datacenter (`dcId()`), Session (signed in; connection answered / did not answer).
- F: actions: Change library (reuse `LibraryScreen` inline), Application id/hash (reuse
  `TelegramApplicationScreen`, prefilled id), Sign out (confirm dialog, same sentence as web).
- F: Cache row: "Held X of Y" (existing occupancy) + size choice; applies immediately, evicts down;
  default 2 GiB, floor 512 MiB (parity with web).
- NF: new files < 200 lines; `AppChrome.kt` (232) and `LibraryFlow.kt` (234) must not grow —
  extract the menu into `OverflowMenu.kt` first.

## Architecture
```
SettingsScreen (ui-mobile) ── SettingsViewModel (feature/setup)
   ├─ CoreProvider.core → account(), dcId()
   ├─ Libraries.list()/install()/chosen()  → CatalogViewModel.reload()
   ├─ CoreProvider.replace(id, hash)       (close old → build new → write; rollback)
   ├─ signOut(): core.signOut() → CoreStorage.clear() → Libraries.forget() → SetupViewModel.recheck()
   └─ CacheBudget (core/playback): prefs + AdjustableLruEvictor.setBudget(n, cache)
```

## Related code files
Create: `android/feature/setup/src/main/kotlin/SettingsViewModel.kt`, `SettingsUiState.kt`;
`android/core/playback/src/main/kotlin/AdjustableLruEvictor.kt`, `CacheBudgetSettings.kt`;
`android/ui-mobile/src/main/kotlin/SettingsScreen.kt`, `SettingsRows.kt`, `OverflowMenu.kt`;
tests `android/feature/setup/src/test/kotlin/SettingsViewModelTest.kt`,
`android/core/playback/src/test/kotlin/AdjustableLruEvictorTest.kt`, `android/core/data/src/test/kotlin/CoreProviderReplaceTest.kt`.
Modify: `CoreProvider.kt` (`replace`), `CoreClient.kt` + `DefaultCoreClient.kt` (+ test fakes) for `account/dcId/signOut`,
`CacheProvider.kt` (evictor + budget from prefs; `occupancy` reports live budget), `AppChrome.kt`
(Destination + use `OverflowMenu`), `LibraryFlow.kt` (route Settings like System/TmdbKey), `LibraryPositions.kt` (menu screen enum).
Delete: none.

## Implementation steps
1. Extract overflow menu to `OverflowMenu.kt` (no behaviour change); add Settings item + destination.
2. `CoreProvider.replace`: under mutex close previous → build new → write; on build failure rebuild previous and rethrow. Test with fake builder recording order.
3. `AdjustableLruEvictor` (implements media3 `CacheEvictor`, LRU `TreeSet` like the stock one, `@Volatile budget`, `setBudget(n, cache)` evicts). Unit test with fake spans.
4. `CacheBudgetSettings` (plain SharedPreferences `playback_settings`), `CacheProvider` reads it at build.
5. `SettingsViewModel`: state = rows + busy/error per action; every call guarded like `SetupViewModel.settle` (own sentences, never exception text).
6. `SettingsScreen`/`SettingsRows`: reuse `SystemRows` styling; inline library list and id/hash form.
7. Sign out wires `SetupViewModel.recheck()` via callback from `MobileApp`.
8. `./gradlew test detekt spotlessCheck`; build + install debug APK.

## Todo
- [x] overflow menu extraction + Settings item
- [x] CoreProvider.replace + test
- [x] AdjustableLruEvictor + prefs + test
- [x] core client additions + fakes
- [x] SettingsViewModel + test
- [x] SettingsScreen/Rows
- [x] device check (read-only, see below)

## Success criteria
- Unit tests: replace order/rollback; evictor shrinks to budget; sign-out clears session+catalog+library but keeps app identity + TMDB key; library switch writes handle only after install.
- Real phone (memory rule): open Settings, read rows, change cache 2 GiB → 1 GiB and see Held drop, switch library to the same `Mediagram` channel. **Never** press Sign out or Start over on it; do not edit id/hash on it (same risk). Those are covered by tests.

## Risks
| Risk | L×I | Mitigation |
|---|---|---|
| Two cores on one data dir during replace | M×H | close-before-build + test |
| New api hash rejected → no core | M×H | rollback to previous identity |
| Custom evictor diverges from media3 LRU contract | M×M | mirror `LeastRecentlyUsedCacheEvictor` logic; media3 version pinned (`CacheProvider.kt` header) |
| Validating sign-out on the real phone costs an SMS login | H×M | not done on device; tests only |

## Security
id/hash stay in encrypted prefs; api hash field never prefilled; confirm dialogs for destructive actions; no admin gate (device-local — deliberate difference, recorded in 09).

## Next steps
09.
