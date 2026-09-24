use super::*;
use crate::index::{db, parts, sets};
use std::cell::RefCell;

fn seed(conn: &Connection, id: &str, messages: std::ops::Range<i64>) -> Removal {
    let count = messages.clone().count();
    conn.execute(
        "INSERT INTO sets(set_id, kind, container, total, part_count, created_at, spec_version)
         VALUES (?1, 'movie', 'mp4', ?2, ?2, 0, 1)",
        rusqlite::params![id, count],
    )
    .unwrap();
    for (idx, message) in messages.enumerate() {
        conn.execute(
            "INSERT INTO parts(set_id, idx, byte_offset, byte_length, message_id, status)
             VALUES (?1, ?2, ?2, 1, ?3, 'done')",
            rusqlite::params![id, idx, message],
        )
        .unwrap();
    }
    let row = sets::get_set(conn, id).unwrap().unwrap();
    crate::remove::plan::plan_removal(&row, &parts::all_parts(conn, id).unwrap())
}

#[tokio::test]
async fn all_remote_batches_finish_before_their_local_rows_are_deleted() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let removals = [
        seed(&conn, "first", 1..206),
        seed(&conn, "second", 301..303),
    ];
    let batches = RefCell::new(Vec::new());
    let affected = apply_removals(&conn, &removals, async |removal| {
        delete_messages_with(removal, async |batch| {
            assert!(sets::get_set(&conn, &removal.set_id).unwrap().is_some());
            assert_eq!(
                parts::all_parts(&conn, &removal.set_id).unwrap().len(),
                removal.message_ids.len()
            );
            if removal.set_id == "second" {
                assert!(sets::get_set(&conn, "first").unwrap().is_none());
            }
            batches.borrow_mut().push(batch.to_vec());
            Ok(batch.len())
        })
        .await
    })
    .await
    .unwrap();
    assert_eq!(affected, 207);
    assert_eq!(
        *batches.borrow(),
        [
            (1..101).collect::<Vec<_>>(),
            (101..201).collect(),
            (201..206).collect(),
            vec![301, 302]
        ]
    );
    for id in ["first", "second"] {
        assert!(sets::get_set(&conn, id).unwrap().is_none());
        assert!(parts::all_parts(&conn, id).unwrap().is_empty());
    }
}

#[tokio::test]
async fn partial_remote_failure_retains_recovery_rows_and_stops_later_sets() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let removals = [
        seed(&conn, "first", 1..2),
        seed(&conn, "failed", 101..306),
        seed(&conn, "later", 401..402),
    ];
    let batches = RefCell::new(Vec::new());
    let error = apply_removals(&conn, &removals, async |removal| {
        delete_messages_with(removal, async |batch| {
            assert!(sets::get_set(&conn, &removal.set_id).unwrap().is_some());
            batches.borrow_mut().push(batch.to_vec());
            if batch[0] == 201 {
                anyhow::bail!("remote batch refused");
            }
            Ok(batch.len())
        })
        .await
    })
    .await
    .unwrap_err();
    assert!(format!("{error:#}").contains("deleting the messages of failed: remote batch refused"));
    assert_eq!(
        *batches.borrow(),
        [vec![1], (101..201).collect(), (201..301).collect()]
    );
    assert!(sets::get_set(&conn, "first").unwrap().is_none());
    assert!(parts::all_parts(&conn, "first").unwrap().is_empty());
    for (id, count) in [("failed", 205), ("later", 1)] {
        assert!(sets::get_set(&conn, id).unwrap().is_some());
        assert_eq!(parts::all_parts(&conn, id).unwrap().len(), count);
    }
}

#[tokio::test]
async fn an_out_of_range_message_prevents_even_the_first_remote_batch() {
    let removal = Removal {
        set_id: "invalid".into(),
        label: "invalid".into(),
        message_ids: (1..102).chain([i64::MAX]).collect(),
        bytes: 0,
    };
    let error = delete_messages_with(&removal, async |_| {
        panic!("all message ids must be checked before any deletion")
    })
    .await
    .unwrap_err();
    assert!(
        error
            .to_string()
            .contains("a message id of invalid is out of range")
    );
}
