# Merge main into feat/cast-to-tv — 2026-10-20

Worktree `/home/andre/Workspace/mediagram-cast`, branch `feat/cast-to-tv`.
Merged `main` at 9a34a7b3 (0.136.1) into 144501f7 with `git merge main`. No rebase, no stash.
Merge commit: **8a2d8ce0** `Merge branch 'main' into feat/cast-to-tv`, with parents 144501f7 and 9a34a7b3.
The branch is now 0 behind and 64 ahead of main. Not merged into main, and the version is not bumped.

## Conflicts (7, as the dry run said)

| File | main side | cast side | Resolution |
|---|---|---|---|
| `android/feature/player/src/main/kotlin/PlayerViewModelTogether.kt` | bfee5ee3: `onThisProfile` also refuses a set in a section hidden from the chosen profile, and became `internal` | 5f2cc9aa: `onThisProfile` judges the caster (`playingAs`) before the TV's chosen profile; `CastTrackSync` import | Kept both. The profile is `playingAs.value ?: repository.chosenProfile.value`, and both checks apply to it: the hidden-section check first, then the kids limit. Kept main's `internal` and merged the two KDocs. All three imports kept. |
| `android/ui-mobile/src/main/kotlin/ui/LibraryTitleBranches.kt` | 4b9ee0a8: `CollectionScreen(episodePreload = …)` | cdd3a8dd: `CollectionScreen(playOnTv = …)` | Both arguments passed. |
| `android/ui-mobile/src/main/kotlin/ui/catalog/CollectionScreen.kt` | `episodePreload` param + KDoc; passed to SeriesPage positionally | `playOnTv` param; passed positionally | Both params kept, in the order `episodePreload, playOnTv`. The positional call to SeriesPage now passes `…, state, episodePreload, playOnTv`. Every caller and test uses named arguments, so none had to change. |
| `android/ui-mobile/src/main/kotlin/ui/catalog/SeriesPage.kt` | `episodePreload` (no default) | `playOnTv: PlayOnTv? = null` | Both kept. `episodePreload` comes first and `playOnTv = null` stays last, so the defaulted parameter trails. The file is 193 lines. |
| `android/ui-tv/src/main/kotlin/ui/tv/profile/TvProfileGate.kt` | f3044797, 0221f152, f14210c4, b8004a2a, 40ee8f65: households. Join first, the waiting line, the owner part, `owner.watching(chosen)`, and the reopen after a join that left the chosen profile behind | 0044bd0b: with no profile chosen (or a cast open), `rememberCastOpens` + `TvCastOverPicker` | Main's effects stay unconditional and come first: `owner.watching`, `lastChosen`, and the `join.joined`/`owner.joined` collector. Then `if (joining.needsJoin) TvJoinHousehold; return`. After that comes the cast block, unchanged, and then main's picker/Profiles page/Manage/join-elsewhere `when`. One KDoc sentence was added (see the decision below). The file is 175 lines. |
| `web/public/app.js` | `import { hiddenSectionsOf }` | `import { mountCastBar }` | Both imports kept. The file is 714 lines. |
| `web/test/code-standards.test.ts` | app.js ceiling 712 (+7) and its dated sentences | app.js ceiling 707 (+2) and its 2026-10-18 sentence | Both dated sentences kept, the cast sentence first (oldest first). **The ceiling is 714, not 712.** Both sides added different lines to `app.js` (705 + 2 + 7), so the merged file is 714. "The larger number", 712, would fail the test. Both sentences' "by N" still add up to it. |

## Decisions where the two features meet

1. **A cast title is judged against the caster's hidden sections, not the TV's chosen profile's** (`onThisProfile`). The cast branch already plays as the caster for the kids limit, the daily limit and positions. Section access follows the same rule. A title the caster cannot see in their own catalogue does not resolve for the TV's watch-together either.
2. **Join comes before the cast on the TV.** A box that has joined no household shows only Join a household and does not listen for casts.
   - Reason: on main the core's `profiles()` is `device::profiles_here`. Before a join it lists nobody, so `playingAs` would be null and the cast would play as no one.
   - This is also what the brief means by "join first".
   - It is written in the gate's KDoc.
   - Once the box is joined, the cast branch's behaviour is unchanged: it plays over the picker with no viewer, and the library listens once one is chosen.
3. The join-left-behind collector and `owner.watching` stay above every early return, including `TvCastOverPicker`. A Switch household heard while a cast plays over the picker still reopens the picker.
4. These needed no code, but I checked them:
   - The TV reads `catalog.unfiltered` while casting (`TvLibraryBranches.kt:50`). That bypasses main's section filter on purpose, because the title came from the caster's own catalogue.
   - The phone's cast bar takes its offers from `CatalogViewModel.state` (filtered), and the web cast bar from `setForTogether` → `byId` (this profile's filtered catalogue). So a hidden-section cast shows no bar on either surface, and the two still match.

## Tests

| Command | Result |
|---|---|
| `cargo test --all` | exit 0; 121 suites, 2239 passed, 0 failed, 4 ignored |
| `bun install --frozen-lockfile` | no changes |
| `bun test` | 3903 pass, 0 fail (264 files) |
| `bun run typecheck` | exit 0 |
| `bun run lint` | exit 0 |
| `./gradlew testDebugUnitTest lint :ui-tv:compileDebugAndroidTestKotlin --continue` | BUILD SUCCESSFUL. 293 tasks executed, 39 from cache, 419 up-to-date. 3095 unit tests ran fresh: 0 failures, 0 errors. All 558 result suites: 3368 tests, 0 failures. |

Nothing broke, so there is no follow-up commit.

## Bindings

`scripts/generate-android-bindings.sh` exited 0. `git diff --stat -- android/core/rust` is empty.
- The cast branch added no uniffi calls: no diff under `crates/mediagram-core/src/api` or `android/core/rust` against the merge base.
- The committed `mediagram_core.kt` is main's, and regenerating it reproduces it exactly.
- The script also rebuilt the gitignored `jniLibs/*.so` in the worktree at 0.136.1. A device build from this worktree will not load a stale core.

## Concerns

1. **Kotlin size rule: three files over 200 lines grew past the larger side.** These came from git's automatic merge, not from a conflict I resolved:
   - `android/feature/catalog/src/main/kotlin/CatalogViewModel.kt`: 391 lines (main 381, cast 354). The cast part is the cast bar's `playPause` and `barScreen`, `unfiltered` made public, and 2 imports.
   - `android/core/testing/src/main/kotlin/testing/FakeCore.kt`: 717 lines (both sides 714).
   - `android/ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt`: 386 lines (main 378, cast 383).

   I left them alone. Getting `CatalogViewModel` back to 381 means moving all 10 cast lines out. That includes making the cast bar a public helper object (for example `castBar.playPause`) and changing `TogetherJoin.kt`, `LibraryFlow.kt` and `CatalogTogetherTest`, which is a refactor rather than a merge fix. Nothing enforces Kotlin file size (no detekt or test ceiling for it). Do it before the final merge if you want the rule applied to auto-merged files as well.
2. **Cross-household casts are unhandled.** The TV plays as `repository.profiles.value.find { it.id == cast.profile }`, and main's core lists only this device's household. A phone in household A that casts to a TV joined to household B gets `playingAs = null`, so the TV plays as its own chosen profile (or no one, over the picker). Nothing stops the phone from offering a TV from another household. This is untested and needs a ruling: limit Play on TV to TVs in the caster's household, or accept the current behaviour.
3. `code-standards.test.ts` uses 714 rather than "the larger number" (see the table above). This was necessary, not a choice.
4. None of this has been walked on a device (as instructed). Worth a look on the box: the households join screen, then a cast over the picker once it has joined.
