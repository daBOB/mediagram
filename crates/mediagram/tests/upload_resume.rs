//! Bulk resume continues past local source failures, retaining failed sets.

use mediagram::index::set_row::SetRow;
use mediagram::index::status::SetStatus;
use mediagram::index::{db, parts, sets};
use mediagram::upload::resume;

mod support;
use support::upload::{FakeTransport, sample_caption};

#[tokio::test]
async fn completed_work_survives_a_source_metadata_cleanup_failure() {
    let dir = tempfile::tempdir().unwrap();
    let mut conn = db::open(dir.path()).unwrap();
    let set = plan(&mut conn, "01J0000000000000000000RES6", 4, 1);
    let source = dir.path().join("movie.mkv");
    std::fs::write(&source, b"data").unwrap();
    db::set_meta(
        &conn,
        &db::source_key(&set.set_id),
        source.to_str().unwrap(),
    )
    .unwrap();
    conn.execute_batch(
        "CREATE TRIGGER refuse_source_cleanup BEFORE DELETE ON meta
        WHEN OLD.key LIKE 'source:%' BEGIN SELECT RAISE(ABORT, 'cleanup failed'); END;",
    )
    .unwrap();

    let summary = resume::pending(
        &conn,
        &FakeTransport::new(),
        0,
        &sets::list_pending(&conn).unwrap(),
        dir.path(),
        |_, _| {},
    )
    .await;

    assert_eq!(summary.completed, 1);
    assert!(format!("{:#}", summary.stopped.unwrap()).contains("cleanup failed"));
    assert_eq!(
        sets::get_set(&conn, &set.set_id).unwrap().unwrap().status,
        SetStatus::Complete
    );
    assert!(
        db::get_meta(&conn, &db::source_key(&set.set_id))
            .unwrap()
            .is_some()
    );
    assert!(source.exists());
}

fn plan(conn: &mut rusqlite::Connection, id: &str, total: u64, created: i64) -> SetRow {
    let part_size = 1024 * 1024;
    let set = SetRow::from_caption(
        &sample_caption(id, total, total.div_ceil(part_size) as u32),
        created,
    );
    let tx = conn.transaction().unwrap();
    sets::insert_set(&tx, &set).unwrap();
    parts::insert_parts(&tx, id, &mlib_spec::plan_parts(total, part_size).unwrap()).unwrap();
    tx.commit().unwrap();
    set
}

#[tokio::test]
async fn a_transport_failure_stops_the_queue_without_losing_completed_work() {
    let dir = tempfile::tempdir().unwrap();
    let mut conn = db::open(dir.path()).unwrap();
    let first = plan(&mut conn, "01J0000000000000000000RES3", 4, 1);
    let failed = plan(&mut conn, "01J0000000000000000000RES4", 2 * 1024 * 1024, 2);
    let later = plan(&mut conn, "01J0000000000000000000RES5", 4, 3);
    for set in [&first, &failed, &later] {
        let source = dir.path().join(&set.set_id);
        std::fs::write(&source, vec![7u8; set.total as usize]).unwrap();
        db::set_meta(
            &conn,
            &db::source_key(&set.set_id),
            source.to_str().unwrap(),
        )
        .unwrap();
    }
    let transport = FakeTransport::new();
    transport.set_fail_on_part(1);
    let mut attempted = Vec::new();

    let summary = resume::pending(
        &conn,
        &transport,
        0,
        &sets::list_pending(&conn).unwrap(),
        dir.path(),
        |id, result| attempted.push((id.to_string(), result.is_ok())),
    )
    .await;

    assert_eq!(summary.completed, 1);
    assert_eq!(summary.blocked, 0);
    assert!(format!("{:#}", summary.stopped.unwrap()).contains("injected failure"));
    assert_eq!(
        attempted,
        vec![(first.set_id.clone(), true), (failed.set_id.clone(), false)]
    );
    assert_eq!(
        sets::get_set(&conn, &first.set_id).unwrap().unwrap().status,
        SetStatus::Complete
    );
    assert_eq!(
        sets::get_set(&conn, &failed.set_id)
            .unwrap()
            .unwrap()
            .status,
        SetStatus::Pending
    );
    assert_eq!(
        sets::get_set(&conn, &later.set_id).unwrap().unwrap().status,
        SetStatus::Pending
    );
    assert_eq!(transport.send_count(), 2);
    assert!(
        db::get_meta(&conn, &db::source_key(&failed.set_id))
            .unwrap()
            .is_some()
    );
}

#[tokio::test]
async fn blocked_oldest_sources_do_not_starve_a_later_valid_set() {
    for source_state in ["unrecorded", "missing", "changed"] {
        let dir = tempfile::tempdir().unwrap();
        let mut conn = db::open(dir.path()).unwrap();
        let blocked = plan(&mut conn, "01J0000000000000000000RES1", 4, 1);
        let valid = plan(&mut conn, "01J0000000000000000000RES2", 4, 2);
        let blocked_path = dir.path().join("blocked.mkv");
        if source_state != "unrecorded" {
            db::set_meta(
                &conn,
                &db::source_key(&blocked.set_id),
                blocked_path.to_str().unwrap(),
            )
            .unwrap();
        }
        if source_state == "changed" {
            std::fs::write(&blocked_path, b"changed").unwrap();
        }
        let valid_path = dir.path().join("valid.mkv");
        std::fs::write(&valid_path, b"data").unwrap();
        db::set_meta(
            &conn,
            &db::source_key(&valid.set_id),
            valid_path.to_str().unwrap(),
        )
        .unwrap();
        let transport = FakeTransport::new();
        let mut attempted = Vec::new();

        let summary = resume::pending(
            &conn,
            &transport,
            0,
            &sets::list_pending(&conn).unwrap(),
            dir.path(),
            |id, result| attempted.push((id.to_string(), result.is_ok())),
        )
        .await;

        assert_eq!(summary.completed, 1, "{source_state}");
        assert_eq!(summary.blocked, 1, "{source_state}");
        assert!(summary.stopped.is_none());
        assert_eq!(
            attempted,
            vec![
                (blocked.set_id.clone(), false),
                (valid.set_id.clone(), true)
            ]
        );
        assert_eq!(
            sets::get_set(&conn, &blocked.set_id)
                .unwrap()
                .unwrap()
                .status,
            SetStatus::Pending
        );
        assert_eq!(
            sets::get_set(&conn, &valid.set_id).unwrap().unwrap().status,
            SetStatus::Complete
        );
        assert_eq!(transport.send_count(), 1);
        assert_eq!(
            db::get_meta(&conn, &db::source_key(&valid.set_id)).unwrap(),
            None
        );
        assert!(valid_path.exists());
    }
}
