use super::*;
use crate::index::sets;
use crate::test_fakes::session::config_in;
use crate::test_fakes::upload::sample_caption;
use match_source::Verdict;
use mlib_spec::caption::Kind;

fn insert(conn: &Connection, set_id: &str, kind: Kind, status: SetStatus, total: u64) {
    let mut caption = sample_caption(set_id, total, 1);
    caption.t = kind;
    let mut row = SetRow::from_caption(&caption, 1_700_000_000);
    row.status = status;
    sets::insert_set(conn, &row).unwrap();
}

/// Every complete movie, episode and documentary is a candidate — one that
/// already has a bundle included, so a re-run's pool is the same as the
/// first run's and a fallback cannot drift onto another title.
#[test]
fn candidates_are_every_complete_movie_episode_and_documentary() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    insert(&conn, "s1", Kind::Movie, SetStatus::Complete, 10);
    insert(&conn, "s2", Kind::Ep, SetStatus::Complete, 10);
    insert(&conn, "s3", Kind::Docu, SetStatus::Complete, 10);
    insert(&conn, "s4", Kind::Tut, SetStatus::Complete, 10);
    insert(&conn, "s5", Kind::Doc, SetStatus::Complete, 10);
    insert(&conn, "s6", Kind::Movie, SetStatus::Pending, 10);
    conn.execute(
        "INSERT INTO subtitle_files(set_id, chat_id, message_id, bytes, sha256, uploaded_at)
         VALUES ('s1', 777, 4001, 1000, 'aa', 1700000000)",
        [],
    )
    .unwrap();

    let ids: Vec<String> = load_candidate_sets(&conn)
        .unwrap()
        .into_iter()
        .map(|s| s.set_id)
        .collect();

    assert_eq!(ids, ["s1", "s2", "s3"]);
}

/// Folders that overlap list a file once; a file is matched by its size,
/// and one ffprobe cannot read is still listed, with nothing probed.
#[tokio::test]
async fn a_survey_lists_each_file_once_and_matches_it_by_size() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    insert(&conn, "s1", Kind::Movie, SetStatus::Complete, 10);
    let media = dir.path().join("media");
    std::fs::create_dir_all(media.join("extra")).unwrap();
    std::fs::write(media.join("Film.mkv"), [0u8; 10]).unwrap();
    std::fs::write(media.join("extra").join("Other.mkv"), [0u8; 7]).unwrap();
    std::fs::write(media.join("notes.txt"), [0u8; 10]).unwrap();

    let found = survey(&conn, &[media.clone(), media.join("extra")])
        .await
        .unwrap();

    let paths: Vec<&PathBuf> = found.files.iter().map(|f| &f.path).collect();
    assert_eq!(
        paths,
        [
            &media.join("Film.mkv"),
            &media.join("extra").join("Other.mkv")
        ]
    );
    assert_eq!(found.files[0].size, 10);
    assert!(found.probes.iter().all(Option::is_none));
    assert!(found.files.iter().all(|f| f.duration.is_none()));
    let verdicts: Vec<&Verdict> = found.matches.iter().map(|m| &m.verdict).collect();
    assert_eq!(
        verdicts,
        [&Verdict::Matched("s1".to_string()), &Verdict::Unmatched]
    );
}

#[tokio::test]
async fn a_folder_that_cannot_be_walked_is_named_in_the_error() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let missing = dir.path().join("no-such-folder");

    let Err(err) = survey(&conn, std::slice::from_ref(&missing)).await else {
        panic!("walking a missing folder should fail");
    };

    assert!(
        format!("{err:#}").contains(&format!("walking {}", missing.display())),
        "{err:#}"
    );
}

/// Measuring needs an index to measure against, and never creates one.
#[tokio::test]
async fn a_dry_run_without_an_index_fails_and_creates_none() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config_in(dir.path());

    let err = run(&cfg, &[dir.path().to_path_buf()]).await.unwrap_err();

    assert!(err.to_string().contains("nothing to measure"), "{err}");
    assert!(!dir.path().join(mlib_spec::schema::INDEX_FILE).exists());
}
