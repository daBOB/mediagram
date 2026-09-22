use super::*;

fn ep() -> Caption {
    Caption {
        cid: None,
        chap: None,
        path: None,
        t: Kind::Ep,
        ids: ProviderIds {
            tmdb: Some(95396),
            tvdb: None,
            imdb: None,
        },
        show: Some("Severance".into()),
        title: Some("Hello, Ms. Cobel".into()),
        year: Some(2022),
        s: Some(2),
        e: Some(Episode::Single(1)),
        abs: None,
        q: Some("1080p".into()),
        hdr: Some("SDR".into()),
        container: "mkv".into(),
        vcodec: None,
        acodec: None,
        alang: vec!["en".into()],
        slang: vec![],
        dur: None,
        variant: None,
        set: "01JQ8F2K9M4XZ".into(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 10,
            sha256: "ab".into(),
        },
        total: 10,
    }
}

#[test]
fn display_and_episode_code() {
    assert_eq!(ep().display_name(), "Severance S02E01");
    assert_eq!(episode_code(1, Episode::Range([1, 2])), "S01E01-E02");
    assert_eq!(Episode::Range([3, 5]).last(), 5);
}

#[test]
fn with_part_keeps_everything_else() {
    let c = ep();
    let d = c.with_part(Part {
        i: 1,
        n: 2,
        off: 5,
        len: 5,
        sha256: "cd".into(),
    });
    assert_eq!(d.part.i, 1);
    assert_eq!(d.show, c.show);
}
