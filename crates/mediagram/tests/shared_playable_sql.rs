//! The player asks the same question the uploader answers.
//!
//! "Playable" is defined once, in `mlib_spec::schema::PLAYABLE_SQL`, and the
//! TypeScript player holds a copy because it queries the same database from a
//! different runtime. A copy that drifts is worse than no copy: the catalog
//! would offer titles that stall halfway through, and nothing would fail until
//! someone pressed play.
//!
//! This test fails when the two stop matching. Edit the Rust constant; this
//! points at what else to update.

use std::path::Path;

const PLAYER_CATALOG: &str = "../../web/src/catalog.ts";

#[test]
fn the_player_holds_the_uploader_s_definition_of_playable() {
    let path = Path::new(env!("CARGO_MANIFEST_DIR")).join(PLAYER_CATALOG);
    // The player lives in this repository, so a missing file means it moved:
    // skipping would stop checking at exactly the moment the copy is easiest
    // to lose track of.
    let source = std::fs::read_to_string(&path)
        .unwrap_or_else(|e| panic!("reading {}: {e}; if the player moved, point this test at it", path.display()));

    assert!(
        source.contains(mlib_spec::schema::PLAYABLE_SQL),
        "{} no longer contains PLAYABLE_SQL verbatim.\n\nExpected to find:\n{}\n\nUpdate the copy in that file.",
        path.display(),
        mlib_spec::schema::PLAYABLE_SQL
    );

    // The player opens the index read-only and refuses one older than it
    // understands. That number has to be this one, or it refuses a database
    // it could read, or reads one it cannot.
    let expected = format!("EXPECTED_SCHEMA = {}", mlib_spec::schema::SCHEMA_VERSION);
    assert!(
        source.contains(&expected),
        "{} should declare `{}`; the schema moved and the player was not told.",
        path.display(),
        expected
    );

    // And the oldest it still reads, which is the floor a snapshot from an
    // uploader not yet upgraded is held to on both sides.
    let oldest = format!("OLDEST_READABLE_SCHEMA = {}", mlib_spec::schema::OLDEST_READABLE_SCHEMA);
    assert!(
        source.contains(&oldest),
        "{} should declare `{}`, the floor `mediagram posters --index` also uses.",
        path.display(),
        oldest
    );
}
