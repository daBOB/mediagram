use super::*;

#[tokio::test]
async fn a_blank_name_neither_makes_nor_renames_a_collection() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let viewer = core.add_profile("Viewer", false).id;

    let blank = core
        .clone()
        .create_collection(viewer.clone(), "   ".into())
        .await;
    let list = core
        .clone()
        .create_collection(viewer.clone(), "Weekend".into())
        .await
        .unwrap();
    let renamed = core
        .clone()
        .rename_collection(viewer.clone(), list.id, " \t ".into())
        .await;

    assert_eq!(blank, None);
    assert!(!renamed);
    let names: Vec<String> = core
        .snapshot(viewer)
        .await
        .collections
        .into_iter()
        .map(|list| list.name)
        .collect();
    assert_eq!(names, ["Weekend"]);
}

/// A deleted list stays behind as a tombstone with its titles, so a copy
/// arriving from another device finds the row to reconcile with instead of
/// a gap it would fill under a new id.
#[tokio::test]
async fn a_deleted_collection_leaves_the_snapshot_but_keeps_its_titles_for_sync() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let viewer = core.add_profile("Viewer", false).id;
    let list = core
        .clone()
        .create_collection(viewer.clone(), "Weekend".into())
        .await
        .unwrap();
    for set in ["first", "second"] {
        let added = core
            .clone()
            .set_in_collection(viewer.clone(), list.id.clone(), set.into(), true)
            .await;
        assert!(added);
    }

    assert!(
        core.clone()
            .delete_collection(viewer.clone(), list.id.clone())
            .await
    );

    assert!(
        core.clone()
            .snapshot(viewer.clone())
            .await
            .collections
            .is_empty()
    );
    let (removed_at, items): (Option<i64>, i64) = core
        .state_db
        .with(|conn| {
            conn.query_row(
                "SELECT removed_at, (SELECT COUNT(*) FROM collection_items WHERE collection_id = ?1)
                   FROM collections WHERE id = ?1",
                [&list.id],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
        })
        .unwrap();
    assert!(removed_at.is_some());
    assert_eq!(items, 2);
    assert!(
        !core.delete_collection(viewer, list.id).await,
        "already gone"
    );
}
