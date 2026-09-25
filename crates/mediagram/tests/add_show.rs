//! A bulk rerun hands interrupted episodes to resume without planning them again.

use mediagram::commands::{add_show, args::AddShowArgs};
use mediagram::config::Config;
use mediagram::index::set_row::SetRow;
use mediagram::index::status::SetStatus;
use mediagram::index::{db, parts, sets};
use mediagram::media::test_fixtures::{ffmpeg_required, make_faststart_mp4};
use mlib_spec::caption::{Episode, Kind};

mod support;

#[tokio::test]
async fn existing_episodes_are_not_replanned_or_deleted_by_a_bulk_rerun() {
    if !ffmpeg_required("add-show rerun") {
        return;
    }
    for status in [SetStatus::Pending, SetStatus::Complete] {
        let dir = tempfile::tempdir().unwrap();
        let show = dir.path().join("show");
        std::fs::create_dir(&show).unwrap();
        let source = show.join("Example.S01E02.mp4");
        std::fs::rename(make_faststart_mp4(&show), &source).unwrap();
        let bytes = std::fs::read(&source).unwrap();
        let data_dir = dir.path().join("data");
        let conn = db::open(&data_dir).unwrap();
        let mut caption = support::export::caption("01J00000000000000000SHOW1", Kind::Ep, Some(42));
        caption.s = Some(1);
        caption.e = Some(Episode::Single(2));
        caption.total = bytes.len() as u64;
        let mut row = SetRow::from_caption(&caption, 100);
        row.status = status;
        sets::insert_set(&conn, &row).unwrap();
        parts::insert_parts(
            &conn,
            &row.set_id,
            &mlib_spec::plan_parts(caption.total, 1024 * 1024).unwrap(),
        )
        .unwrap();
        db::set_meta(
            &conn,
            &db::source_key(&row.set_id),
            source.to_str().unwrap(),
        )
        .unwrap();

        // With no provider key, any attempt to plan a new episode fails before
        // network IO. An existing episode needs neither planning nor a login.
        let mut cfg: Config =
            toml::from_str("api_id = 1\napi_hash = 'fixture'\nchannel = 'fixture'").unwrap();
        cfg.data_dir = Some(data_dir);
        add_show::run(
            &cfg,
            AddShowArgs {
                dir: show,
                tmdb: Some(42),
                dry_run: false,
                no_push: true,
                yes: true,
                delete_source: true,
            },
        )
        .await
        .unwrap();

        assert_eq!(
            conn.query_row("SELECT COUNT(*) FROM sets", [], |r| r.get::<_, i64>(0))
                .unwrap(),
            1
        );
        assert_eq!(
            conn.query_row("SELECT COUNT(*) FROM parts", [], |r| r.get::<_, i64>(0))
                .unwrap(),
            1
        );
        assert_eq!(
            sets::get_set(&conn, &row.set_id).unwrap().unwrap().status,
            status
        );
        assert_eq!(
            db::get_meta(&conn, &db::source_key(&row.set_id))
                .unwrap()
                .as_deref(),
            source.to_str()
        );
        assert_eq!(std::fs::read(&source).unwrap(), bytes);
    }
}
