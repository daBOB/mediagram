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
    let Ok(source) = std::fs::read_to_string(&path) else {
        // The player is a separate deliverable; its absence is not a failure
        // of the uploader, but its divergence would be.
        eprintln!("skipping: {} is not present", path.display());
        return;
    };

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
}
