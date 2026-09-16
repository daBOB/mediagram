use mlib_spec::caption_codec::{CAPTION_BUDGET, MARKER};
use mlib_spec::{Caption, Episode, Kind, Part, ProviderIds, parse, to_text};

fn movie() -> Caption {
    Caption {
        cid: None,
        chap: None,
        path: None,
        t: Kind::Movie,
        ids: ProviderIds {
            tmdb: Some(693134),
            tvdb: None,
            imdb: Some("tt15239678".into()),
        },
        show: None,
        title: Some("Dune: Part Two".into()),
        year: Some(2024),
        s: None,
        e: None,
        abs: None,
        q: Some("2160p".into()),
        hdr: Some("DV".into()),
        container: "mkv".into(),
        vcodec: Some("hevc".into()),
        acodec: Some("truehd".into()),
        alang: vec!["en".into(), "de".into()],
        slang: vec!["en".into()],
        dur: Some(9960),
        variant: None,
        set: "01JQ8F2K9M4XZ".into(),
        part: Part {
            i: 0,
            n: 17,
            off: 0,
            len: 3_758_096_384,
            sha256: "a".repeat(64),
        },
        total: 62_914_560_000,
    }
}

fn episode() -> Caption {
    Caption {
        cid: None,
        chap: None,
        path: None,
        t: Kind::Ep,
        show: Some("Severance".into()),
        title: Some("Hello, Ms. Cobel".into()),
        year: Some(2022),
        s: Some(2),
        e: Some(Episode::Range([1, 2])),
        abs: Some(11),
        ..movie()
    }
}

#[test]
fn exact_wire_format_is_stable() {
    let text = to_text(&movie(), "").unwrap();
    let expected = format!(
        "{MARKER}\n{{\"t\":\"movie\",\"ids\":{{\"tmdb\":693134,\"tvdb\":null,\"imdb\":\"tt15239678\"}},\
\"cid\":null,\"show\":null,\"chap\":null,\"path\":null,\"title\":\"Dune: Part Two\",\"year\":2024,\"s\":null,\"e\":null,\"abs\":null,\
\"q\":\"2160p\",\"hdr\":\"DV\",\"container\":\"mkv\",\"vcodec\":\"hevc\",\"acodec\":\"truehd\",\
\"alang\":[\"en\",\"de\"],\"slang\":[\"en\"],\"dur\":9960,\"variant\":null,\"set\":\"01JQ8F2K9M4XZ\",\
\"part\":{{\"i\":0,\"n\":17,\"off\":0,\"len\":3758096384,\"sha256\":\"{}\"}},\"total\":62914560000}}",
        "a".repeat(64)
    );
    assert_eq!(text, expected);
    assert!(text.is_ascii());
    assert!(text.chars().count() < 600, "{}", text.chars().count());
}

#[test]
fn roundtrip_all_kinds_with_human_lines_and_crlf() {
    for c in [movie(), episode()] {
        let text = to_text(&c, "🎬 human line • ignored by parser").unwrap();
        assert_eq!(parse(&text).unwrap(), c);
        assert_eq!(parse(&text.replace('\n', "\r\n")).unwrap(), c);
    }
}

#[test]
fn episode_range_and_single_serialize_as_expected() {
    let text = to_text(&episode(), "").unwrap();
    assert!(text.contains("\"e\":[1,2]"));
    let single = Caption {
        e: Some(Episode::Single(7)),
        ..episode()
    };
    assert!(to_text(&single, "").unwrap().contains("\"e\":7,"));
    assert_eq!(
        parse(&to_text(&single, "").unwrap()).unwrap().e,
        Some(Episode::Single(7))
    );
}

#[test]
fn human_line_is_truncated_never_json() {
    let human = "x".repeat(5000);
    let text = to_text(&movie(), &human).unwrap();
    assert_eq!(text.chars().count(), CAPTION_BUDGET);
    assert_eq!(parse(&text).unwrap(), movie());
}

#[test]
fn oversized_json_is_an_error() {
    let big = Caption {
        variant: Some("v".repeat(1100)),
        ..movie()
    };
    assert!(to_text(&big, "").is_err());
}

#[test]
fn budget_is_utf16_and_exact_fit_does_not_panic() {
    use mlib_spec::caption_codec::tg_len;
    let text = to_text(&movie(), &"🎬".repeat(2000)).unwrap();
    assert!(tg_len(&text) <= CAPTION_BUDGET);
    assert_eq!(parse(&text).unwrap(), movie());
    let json_len = tg_len(&to_text(&movie(), "").unwrap());
    let exact = Caption {
        variant: Some("v".repeat(CAPTION_BUDGET - json_len + 3)),
        ..movie()
    };
    let t = to_text(&exact, "human").unwrap_or_else(|_| String::new());
    assert!(tg_len(&t) <= CAPTION_BUDGET);
    assert!(parse("\n\n#mlib v=2\n{}").is_err());
    assert!(parse(&format!("\n\n{}", to_text(&movie(), "").unwrap())).is_ok());
}
