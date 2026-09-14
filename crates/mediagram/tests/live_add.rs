//! Live integration test: uploads a real 3 MiB file to the configured
//! Telegram channel via `commands::add::run` with `--manual` metadata, and
//! asserts it lands as 3 `done` parts.
//!
//! Ignored by default. To run it against a real channel:
//!
//! ```text
//! MEDIAGRAM_LIVE=1 MEDIAGRAM_PART_SIZE=1048576 \
//!     cargo test -p mediagram --test live_add -- --ignored --nocapture
//! ```
//!
//! Requires a working `config.toml` (`api_id`/`api_hash`/`channel`) and an
//! already-authorized session (run `mediagram login` once, interactively,
//! beforehand). `--manual` prompts on the real terminal, so this test is
//! meant to be run by hand, not in CI.

use mediagram::commands::add;
use mediagram::commands::args::AddArgs;
use mediagram::index::db;

#[tokio::test]
#[ignore = "hits the real Telegram API; run with MEDIAGRAM_LIVE=1 --ignored"]
async fn add_uploads_a_three_part_file() {
    if std::env::var("MEDIAGRAM_LIVE").as_deref() != Ok("1") {
        eprintln!("skipping add_uploads_a_three_part_file: set MEDIAGRAM_LIVE=1 to run");
        return;
    }

    // SAFETY: this is the only test touching the process environment and
    // `cargo test` runs each integration test binary as its own process.
    unsafe {
        std::env::set_var("MEDIAGRAM_PART_SIZE", "1048576");
    }
    let cfg = mediagram::config::load(None).expect("config.toml with api_id/api_hash/channel");

    let dir = tempfile::tempdir().unwrap();
    let file = dir.path().join("live-smoke.bin");
    let data: Vec<u8> = (0..3 * 1024 * 1024u32).map(|i| (i % 256) as u8).collect();
    tokio::fs::write(&file, &data).await.unwrap();

    let args = AddArgs {
        file: file.clone(),
        tmdb: None,
        tvdb: None,
        imdb: None,
        season: None,
        episode: None,
        abs_no: None,
        variant: None,
        manual: true,
        no_remux: true,
        alang: Some(vec!["en".to_string()]),
        slang: None,
        hdr: Some("SDR".to_string()),
        no_push: true,
    };

    add::run(&cfg, args).await.expect("add should complete");

    let conn = db::open(&cfg.data_dir().unwrap()).unwrap();
    let set_id: String = conn
        .query_row(
            "SELECT set_id FROM sets ORDER BY created_at DESC LIMIT 1",
            [],
            |r| r.get(0),
        )
        .expect("the set just added should be in the index");

    let status: String = conn
        .query_row(
            "SELECT status FROM sets WHERE set_id = ?1",
            [&set_id],
            |r| r.get(0),
        )
        .unwrap();
    assert_eq!(status, "complete");

    let done_parts: u32 = conn
        .query_row(
            "SELECT COUNT(*) FROM parts WHERE set_id = ?1 AND status = 'done'",
            [&set_id],
            |r| r.get(0),
        )
        .unwrap();
    assert_eq!(done_parts, 3);
}
