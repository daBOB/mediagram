use super::*;
use std::cell::Cell;

fn fixture() -> (tempfile::TempDir, Config) {
    let dir = tempfile::tempdir().unwrap();
    let mut cfg: Config =
        toml::from_str("api_id = 1\napi_hash = 'offline-fixture'\nchannel = 'offline-fixture'\n")
            .unwrap();
    cfg.data_dir = Some(dir.path().to_path_buf());
    let conn = db::open(dir.path()).unwrap();
    conn.execute(
        "INSERT INTO sets(set_id, kind, container, total, part_count, created_at, spec_version)
         VALUES ('kept', 'movie', 'mp4', 10, 1, 0, 1)",
        [],
    )
    .unwrap();
    conn.execute(
        "INSERT INTO parts(set_id, idx, byte_offset, byte_length, message_id, status)
         VALUES ('kept', 0, 0, 10, 100, 'done')",
        [],
    )
    .unwrap();
    (dir, cfg)
}

fn assert_retained(cfg: &Config) {
    let conn = db::open(&cfg.data_dir().unwrap()).unwrap();
    assert!(sets::get_set(&conn, "kept").unwrap().is_some());
    assert_eq!(parts::all_parts(&conn, "kept").unwrap().len(), 1);
    assert!(!cfg.data_dir().unwrap().join("session.sqlite").exists());
}

#[tokio::test]
async fn dry_run_never_connects_or_deletes_even_with_yes() {
    for yes in [false, true] {
        let (_dir, cfg) = fixture();
        run_with(&cfg, vec!["kept".into()], true, yes, async |_, _| {
            panic!("dry run must not enter the connection/deletion boundary")
        })
        .await
        .unwrap();
        assert_retained(&cfg);
    }
}

#[tokio::test]
async fn missing_yes_refuses_before_connecting_or_deleting() {
    let (_dir, cfg) = fixture();
    let error = run_with(&cfg, vec!["kept".into()], false, false, async |_, _| {
        panic!("unconfirmed removal must not enter the connection/deletion boundary")
    })
    .await
    .unwrap_err();
    assert!(
        error
            .to_string()
            .contains("refusing to delete without --yes")
    );
    assert_retained(&cfg);
}

#[tokio::test]
async fn every_requested_set_is_validated_before_connecting() {
    let (_dir, cfg) = fixture();
    let error = run_with(
        &cfg,
        vec!["kept".into(), "missing".into()],
        false,
        true,
        async |_, _| panic!("a missing set must be detected before connecting"),
    )
    .await
    .unwrap_err();
    assert!(error.to_string().contains("no set missing in the index"));
    assert_retained(&cfg);
}

#[tokio::test]
async fn confirmed_removal_passes_its_plan_and_propagates_connection_failure() {
    let (_dir, cfg) = fixture();
    let calls = Cell::new(0);
    let error = run_with(
        &cfg,
        vec!["kept".into()],
        false,
        true,
        async |_, removals| {
            calls.set(calls.get() + 1);
            assert_eq!(removals.len(), 1);
            assert_eq!(removals[0].set_id, "kept");
            assert_eq!(removals[0].message_ids, [100]);
            assert_eq!(removals[0].bytes, 10);
            bail!("connection unavailable")
        },
    )
    .await
    .unwrap_err();
    assert_eq!(calls.get(), 1);
    assert_eq!(error.to_string(), "connection unavailable");
    assert_retained(&cfg);
}
