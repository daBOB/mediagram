# Android magazine parity (tech-debt #13)

Status: planned, awaiting the user's decisions below. Owed under CLAUDE.md § Surface
Parity: the web home (`plans/260925-2014-web-player-magazine-redesign`) is the reference.
Research brief of 2026-09-25 (this session).

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
