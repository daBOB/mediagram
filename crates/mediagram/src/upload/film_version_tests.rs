use mlib_spec::caption::{Caption, Kind, Part};
use mlib_spec::ids::ProviderIds;

use super::{describe, notice, same_version};
use crate::index::set_row::SetRow;
use crate::index::{db, set_lookup, sets};

fn film(set: &str, kind: Kind, tmdb: u64, quality: &str, hdr: &str, cut: Option<&str>) -> SetRow {
    let caption = Caption {
        t: kind,
        ids: ProviderIds {
            tmdb: Some(tmdb),
            tvdb: None,
            imdb: None,
        },
        cid: None,
        show: None,
        chap: None,
        path: None,
        title: Some("Blade Runner".into()),
        year: Some(1982),
        s: None,
        e: None,
        abs: None,
        q: Some(quality.into()),
        hdr: Some(hdr.into()),
        container: "mkv".into(),
        vcodec: None,
        acodec: None,
        alang: vec![],
        slang: vec![],
        dur: None,
        variant: cut.map(str::to_string),
        set: set.into(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 0,
            sha256: String::new(),
        },
        total: 0,
    };
    SetRow::from_caption(&caption, 1_700_000_000)
}

#[test]
fn the_notice_lists_every_held_version() {
    let held = [
        film("A", Kind::Movie, 78, "1080p", "SDR", None),
        film("B", Kind::Movie, 78, "2160p", "HDR10", Some("Final Cut")),
    ];
    assert_eq!(
        notice("Blade Runner (1982)", &held),
        "Adds a version to \"Blade Runner (1982)\" — already held: \
         1080p · standard, 2160p HDR10 · Final Cut"
    );
    assert_eq!(
        describe(&film("C", Kind::Movie, 78, "720p", "SDR", Some("Extended"))),
        "720p · Extended"
    );
}

#[test]
fn only_the_same_quality_hdr_and_cut_is_the_same_version() {
    let held = film("A", Kind::Movie, 78, "2160p", "HDR10", None);
    assert!(same_version(&held, Some("2160p"), "HDR10", None, &[]));
    assert!(!same_version(&held, Some("1080p"), "HDR10", None, &[]));
    assert!(!same_version(&held, Some("2160p"), "SDR", None, &[]));
    assert!(!same_version(
        &held,
        Some("2160p"),
        "HDR10",
        Some("Final Cut"),
        &[]
    ));
}

#[test]
fn a_copy_that_adds_a_language_is_not_the_same_version() {
    let mut held = film("A", Kind::Movie, 78, "1080p", "SDR", None);
    held.alang = vec!["de".into()];
    let german = ["de".to_string()];
    let both = ["en".to_string(), "de".to_string()];
    assert!(same_version(&held, Some("1080p"), "SDR", None, &german));
    assert!(!same_version(&held, Some("1080p"), "SDR", None, &both));
    held.alang = vec!["de".into(), "en".into()];
    assert!(same_version(&held, Some("1080p"), "SDR", None, &both));
}

#[test]
fn film_versions_are_the_films_under_that_tmdb_id() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    for row in [
        film(
            "01FILM0000000000000000000A",
            Kind::Movie,
            78,
            "1080p",
            "SDR",
            None,
        ),
        film(
            "01FILM0000000000000000000B",
            Kind::Movie,
            78,
            "2160p",
            "DV",
            Some("Final Cut"),
        ),
        film(
            "01FILM0000000000000000000C",
            Kind::Movie,
            79,
            "1080p",
            "SDR",
            None,
        ),
        film(
            "01FILM0000000000000000000D",
            Kind::Ep,
            78,
            "1080p",
            "SDR",
            None,
        ),
    ] {
        sets::insert_set(&conn, &row).unwrap();
    }
    let ids: Vec<String> = set_lookup::film_versions(&conn, 78)
        .unwrap()
        .into_iter()
        .map(|row| row.set_id)
        .collect();
    assert_eq!(
        ids,
        ["01FILM0000000000000000000A", "01FILM0000000000000000000B"]
    );
    assert!(set_lookup::film_versions(&conn, 80).unwrap().is_empty());
}
