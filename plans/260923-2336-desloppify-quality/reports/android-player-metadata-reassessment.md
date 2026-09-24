# Android player mutations and optional metadata reassessment

Status: DONE

## Findings and changes

Player Watchlist, Kids, membership, and list creation writes previously ran
unguarded in `viewModelScope`. Exceptions escaped, membership `false` and
creation `null` were discarded, and viewers received no recoverable feedback.

`PlayerViewModel` now uses a local cancellation-preserving write handler and a
separate `actionNotice` flow. It checks refusal results, never substitutes a
playback failure, and never changes acknowledged marks or retries a write.
The wording says the update could not be **confirmed**, because a write can
commit before its snapshot reload fails. Successful creation remains visible
if its subsequent membership write fails. A successful retry clears its own
notice; a successful unrelated action does not clear it. Leaving or reopening
a title clears the notice and invalidates late notice updates, including
leaving A for B and returning to A.

`PlayerScreen` renders a dismissible notice independently of fading controls.
`PlayerMarks` also forwards it into `AddToListDialog`, so a failure remains
visible while that modal is open. Playback keeps running in both cases.

The `rememberTitleInfo` and `rememberPosterPath` effects now catch ordinary
lookup exceptions locally and emit operation-specific `CatalogMetadata`
diagnostics with their causes. Cancellation is rethrown. The existing nullable
contracts, playable title detail, show-poster/initials fallback, and season
navigation remain intact. A changed key or remount performs the normal lookup
again. No repository, provider, navigation, or CatalogViewModel API changed.

## Failure evidence

Before changing production code:

- `PlayerActionFailureTest`: **10 tests, 5 failures**. Each ordinary exception
  escaped for Watchlist, Kids, existing membership, creation, or membership
  following successful creation. Cancellation cases passed and remain covered.
- `PlayerLifecycleTest` plus `OptionalMetadataTest`: **9 tests, 4 failures**.
  Refused membership and creation produced no notice, and title and season
  metadata exceptions escaped their Compose effects.

Logs:

- `/tmp/android-player-metadata-before.log`
- `/tmp/android-player-metadata-ui-before.log`

## Regression coverage

Added 21 cases: 10 parameterized failure/cancellation cases, five action-notice
cases, two actual player-screen refusal cases, and four actual Compose metadata
effect cases. They exercise the production ViewModel, real coroutine jobs,
real Compose effects and UI, and the existing player lifecycle fixture. Only
media decoding and repository/lookup boundaries are controlled; there is no
live Telegram or native core activity.

The tests verify cancellation remains cancellation without a notice/diagnostic,
playback is not stopped, UI failures are sanitized, snapshots are not invented,
no automatic write retry occurs, partial list success remains acknowledged,
manual retry can succeed, late failures are ignored after title replacement,
and notices are visible and dismissible in the actual UI.

## Verification

From `android/`:

```sh
./gradlew :feature:player:testDebugUnitTest :ui-mobile:testDebugUnitTest
```

**169 tests passed: 57 player and 112 mobile UI; zero failures, errors, or skips.**
The run completed successfully in 12 seconds and also compiled the controller's
KeptWall changes. Log: `/tmp/android-player-metadata-green.log`.

Scoped `mise exec ktlint@1.8.0 -- ktlint --format <owned files>` and
`git diff --check` passed. Log: `/tmp/android-player-metadata-format.log`.
The final formatting correction only parenthesized a test condition; it did
not change behavior. Existing build warnings remain for the absent Compose
stability configuration file and deprecated adaptive window-size APIs.

Gradle runs were coordinated sequentially with the core worker. Plain wrapper
invocations reused daemon 3092229; every owned wrapper process exited. The
daemon was explicitly handed back for the controller/core/cache workers. No
other process was stopped, no scanner state was edited, and no commit was made.

## Integration

Production files are stable for controller review. Ownership stayed within
`PlayerViewModel`, player notice presentation, `TitleDetailScreen`, `SeasonWall`,
and focused tests. No `CoreClient.refreshCatalog` override or new fake core
implementation was introduced. The controller owns independent final review,
queue resolution, and broader Android delivery gates.
