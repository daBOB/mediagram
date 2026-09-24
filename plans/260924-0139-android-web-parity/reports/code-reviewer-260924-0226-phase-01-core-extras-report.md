# Code review: phase 01 core extras (index genres/subtitles/summary, preferences, profile delete)

Severity: 0 Critical, 0 High, 2 Medium, 2 Low. All fixed in this pass.

## Medium

- **M1** `api/store.rs` `list_sets`: the fetched-genre merge used `?` on
  `crate::shows::genres(&fetched)`, so a broken or empty `details.db` (e.g.
  a partial file a rolled-back migration left behind) failed the whole
  catalog listing instead of just skipping the fallback — inconsistent with
  `enrich::details.rs`'s existing tolerance for the same store. Fixed: logs
  and continues, matching `fetched()`'s existing pattern. Covered by
  `a_broken_fetched_sidecar_does_not_take_the_catalog_down`
  (`tests/index_extras_in_catalog.rs`).
- **M2** Missing coverage. Added:
  - `index_genres_are_preferred_to_a_fetched_value_for_the_same_title` — pins
    that the merge is `or_insert`, not `insert` (index wins on collision).
  - `a_v6_index_still_fills_genres_subtitles_and_has_summary` — a v6 index
    (no `shows.certification`) still lists a set with genres/subtitles/
    summary filled and `fsk: None`.
  - `an_episode_inherits_its_shows_genres_through_the_series_poster_key` — an
    `ep` set's `tmdb-tv-*` key finds the show's row.

## Low

- **L1** `api/store.rs`'s comment overstated the precedence: it claimed a
  genre shelf lists "exactly what the title page shows," but `title_info`
  picks an index row whole (even with `genres` NULL) while `list_sets` falls
  back to the fetched sidecar per field. Reworded to state the real,
  coarser-grained rule; no behaviour change.
- **L2** Documented in phase-01's Key insights: the core lets a broken or
  missing `assets`/`shows` table in the *index* propagate as a `CoreError`
  (unlike the web's `tolerate()`), while the same table missing from the
  device's own fetched sidecar is tolerated and logged. Acceptable because
  `OLDEST_READABLE_SCHEMA = 6` guarantees both index tables exist in
  anything this build will open.

No unresolved questions.
