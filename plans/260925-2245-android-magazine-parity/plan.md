# Android magazine parity (tech-debt #13)

Status: implemented 2026-09-26 on branch `worktree-agent-a6b17fc3ba5be3a38`, verified on the real
signed-in tablet. Owed under CLAUDE.md § Surface Parity: the web home
(`plans/260925-2014-web-player-magazine-redesign`) is the reference.
Research brief of 2026-09-25 (this session).

## Phase status

1. **Core data and backdrops.** Done. `SetSummary.backdrop_key`/`tagline`/`rating`/`popularity`;
   `resolve_backdrops` takes a width; on-device fetch resolves and downloads backdrops, counted in
   `FetchReport.backdrops_fetched`/`backdrops_already_held`.
2. **Editor's choice in sync.** Done. `editors_choice` table, `editorsChoice` in `SyncRecord`,
   merge and lists exchange; uniffi `editors_choice`/`set_editors_choice`; shared fixtures in
   `web/test/fixtures/watch-state/{record-parse,lists-merge}.json`.
3. **Picks logic.** Done. `EditorialPicks.kt` in `feature:catalog`, pinned against
   `web/test/fixtures/editorial-picks/home-editorial.json` by both `bun test` and
   `EditorialPicksFixtureTest`.
4. **Home screen.** Done. `CoverStory`/`FeatureStrip`/`ResumeStrip`/`PullQuote` in `ui-mobile`,
   assembled by `HomeScreen.kt`; `magazineHomeOf` wires shelves and watch state to it.
5. **Title page.** Done. Backdrop band and "Make editor's choice" (hidden on kids profiles) in
   `TitleDetailScreen.kt`.
6. **Device validation.** Done on the real tablet (`caad49da`): app installed and launched clean,
   no crashes; ran the on-device backdrop fetch (855 backdrops); cover pager, feature strip,
   merged resume strip and pull-quote all render with real data; title page backdrop band and pin
   action render correctly. Screenshots in `visuals/`. Did **not** pin/unpin on the live device —
   the pin is household-wide and syncs to the real account, and something was already pinned
   (Crime 101); toggling it would have been a real, visible change to the user's account made
   without clear authorization. The write path itself is covered by `editors_choice.rs`'s and
   `MagazineHomeTest`'s own tests instead.
7. **TV.** Deferred, as planned — no TV hardware in this session; `ui-tv` untouched.

## Where the phone is today (verified)

- **Home** (`ui-mobile/.../ui/catalog/HomeScreen.kt:41-78`) is one grid of rows from
  `feature/catalog/.../HomeShelves.kt:52`: Continue, Next up, Latest. There is no cover, no
  features, no quote and no landscape cards.
- **DTO:** `SetSummary` (`crates/mediagram-core/src/dto.rs:11-51`) carries `poster_key`,
  `fsk` and `genres`. It has **no** backdrop, tagline, rating or popularity.
- **Artwork:** the on-device fetch (`enrich/fetch.rs:39`) calls only `resolve_posters`,
  deliberately: the user agreed the phone skips backdrops until it has a hero.
  `store::poster_path` would already resolve `-bg` keys.
- **Sync:** the core's `SyncRecord` (`state/record.rs:66-77`) has profiles and kids only.
  It drops `editorsChoice`: the web keeps working, but the phone never sees the pin. The
  Kids mark (table, rows, exchange, uniffi, `WatchStateRepository`) is the model to copy.
- **TV** (`android/ui-tv`) is still an empty placeholder, blocked on having a television.

## Phases

1. **Core data and backdrops.**
   - `SetSummary` gains `backdrop_key` (only when the file exists, as `routes.ts` does),
     plus `tagline`, `rating` and `popularity`, attached in `store::list_sets` from `shows`
     and the sidecar.
   - `resolve_backdrops` takes a width. `fetch.rs` fetches backdrops into `artwork/`, and
     `FetchReport` counts them.
   - Rust tests.
2. **Editor's choice in sync.**
   - An `editors_choice` table in the phone's state schema, and `editorsChoice` in
     `SyncRecord`, the merge and the exchange.
   - The one-pick rule: pinning retires the others, and unpinning retires every live
     mark, exactly as `web/src/state/store.ts`.
   - uniffi `set_editors_choice` / `editors_choice`.
   - Shared JSON fixtures that both `merge.ts` and Rust must pass.
3. **Picks logic.**
   - `EditorialPicks.kt` in `feature:catalog`: a pure port of
     `web/public/lib/catalog/editorial-picks.js` (seeded mulberry32 by day; trending by
     popularity with the "New" fallback; staff top-10 by rating rotated daily; quote ≤ 90
     characters; This month).
   - Shared JSON fixtures with exact seeded outputs, run by both `bun test` and the JVM
     tests, so the two cannot drift.
4. **Home screen.**
   - Compose cover pager (pause, and reduced motion respected); three feature cards;
     Continue Watching as landscape cards with a progress bar over the picture (Continue
     plus Next up, as the web strip does); the pull-quote; the Recently Added row.
   - Dark-first, with the same type roles (a serif display face via downloadable fonts or
     bundled Fraunces/Newsreader).
5. **Title page.** A backdrop band on `TitleDetailScreen`, and a "Make editor's choice"
   action hidden on kids profiles.
6. **Device validation.**
   - Build: `scripts/build-android-core.sh`, then `installDebug`.
   - On a test profile (never the real one, never "Start over"): launch, then check with
     `uiautomator dump` and screenshots.
   - Pin on the phone, see it on the web, and the reverse.
7. **TV.** Stays deferred with the foundation plan's TV phase. The picks live in
   `feature:catalog`, so `ui-tv` only has to draw them.

Build order: 1 and 2 are independent Rust work, 3 depends on 1, 4 and 5 on 1–3, and 6 is
last.

## Decisions for the user

1. **Backdrop width on the phone:**
   - w780 (about 60 MB for the library; may look soft on the 3200×2136 tablet);
   - w1280 (about 137 MB);
   - w780 on phones and w1280 on tablets (recommended).

   And should backdrops be fetched on Wi-Fi only?
2. **The pin on the phone:** on the title page only (recommended, like the web), or also
   in the player's marks row next to Kids?
3. **Cover candidates:** films only, as on the web (recommended for parity), or series too?
4. **Next up:** merge it into the Continue Watching strip, as the web does (recommended),
   or keep the phone's separate row?

## Risks

- **Sync-format change:** install the new builds before relying on phone pins, which is
  the same ordering rule as for the v8 package.
- **The seeded RNG drifting from the web:** mitigated by the shared fixtures with exact
  outputs.
- **Storage on the device:** see decision 1.
- **Kids profiles:** a pinned title may be filtered out there; the lead card then falls
  back to the staff rule, as on the web.

## Decisions (user, 2026-09-25)

- **Backdrops:** w780 on phones, w1280 on tablets (by smallest-width).
- **Match the web:** pin on the title page only, a films-only cover, and Next up merged
  into the Continue Watching strip.
- Wi-Fi-only fetching was not decided; the default follows the existing poster fetch.
