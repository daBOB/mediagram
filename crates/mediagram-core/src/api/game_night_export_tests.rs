use super::*;
use crate::api::test_support::index_at;

/// A catalog with one film, Dune (TMDB 550), as set `m1`.
fn core_with_dune(dir: &std::path::Path) -> Arc<Core> {
    let core = Core::at(dir);
    index_at(dir)
        .execute(
            "INSERT INTO sets(set_id, kind, title, tmdb, container, total, part_count, status, created_at, spec_version)
             VALUES ('m1', 'movie', 'Dune', 550, 'mkv', 0, 0, 'complete', 0, 1)",
            [],
        )
        .unwrap();
    core
}

#[tokio::test]
async fn the_titles_come_as_json_under_the_callers_rules() {
    let dir = tempfile::tempdir().unwrap();
    let core = core_with_dune(dir.path());
    let json = core.clone().game_night_titles(config(&["m1"], &["m1"], false)).await.unwrap();
    let titles: serde_json::Value = serde_json::from_str(&json).unwrap();
    assert_eq!((titles[0]["key"].as_str(), titles[0]["kind"].as_str()), (Some("m1"), Some("film")));
    assert_eq!(titles[0]["kids_ok"], true);

    let hidden = core.game_night_titles(config(&[], &[], false)).await.unwrap();
    assert_eq!(hidden, "[]", "a set in a hidden section is not handed over");
}

#[tokio::test]
async fn a_kids_night_hands_over_only_kid_safe_titles() {
    let dir = tempfile::tempdir().unwrap();
    let core = core_with_dune(dir.path());
    let grown_up = core.clone().game_night_titles(config(&[], &["m1"], false)).await.unwrap();
    assert!(grown_up.contains("\"kids_ok\":false"), "an unlocked night still sees the title, marked: {grown_up}");
    let locked = core.clone().game_night_titles(config(&[], &["m1"], true)).await.unwrap();
    assert_eq!(locked, "[]", "a title that isn't kid-safe never leaves this app on a kids night");
    let safe = core.game_night_titles(config(&["m1"], &["m1"], true)).await.unwrap();
    assert!(safe.contains("\"key\":\"m1\""), "{safe}");
}

fn config(kids: &[&str], visible: &[&str], kids_locked: bool) -> NightTitlesConfig {
    NightTitlesConfig {
        kids_set_ids: kids.iter().map(|s| s.to_string()).collect(),
        visible_set_ids: visible.iter().map(|s| s.to_string()).collect(),
        kids_locked,
    }
}

#[tokio::test]
async fn a_textless_poster_needs_a_tmdb_key() {
    let dir = tempfile::tempdir().unwrap();
    let core = core_with_dune(dir.path());
    std::fs::create_dir_all(dir.path().join(CACHE_DIR)).unwrap();
    std::fs::write(dir.path().join(CACHE_DIR).join("tmdb-movie-550.jpg"), b"jpg").unwrap();
    assert_eq!(core.clone().textless_poster("m1".into(), None).await, None);
    assert_eq!(core.textless_poster("m1".into(), Some(" ".into())).await, None);
}

#[tokio::test]
async fn a_cached_textless_poster_is_found_by_its_title_key() {
    let dir = tempfile::tempdir().unwrap();
    let core = core_with_dune(dir.path());
    let file = dir.path().join(CACHE_DIR).join("tmdb-movie-550.jpg");
    std::fs::create_dir_all(file.parent().unwrap()).unwrap();
    std::fs::write(&file, b"jpg").unwrap();
    let found = core.clone().textless_poster("m1".into(), Some("key".into())).await;
    assert_eq!(found.as_deref(), file.to_str());
    assert_eq!(core.textless_poster("unknown".into(), Some("key".into())).await, None);
}
