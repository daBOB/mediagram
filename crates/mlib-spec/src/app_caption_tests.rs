use super::*;

fn release(code: i64) -> AppRelease {
    AppRelease {
        version: format!("0.{code}.0"),
        code,
        bytes: 47_185_920,
        sha256: "a".repeat(64),
        published_at: 1_790_900_000,
    }
}

#[test]
fn a_rendered_caption_reads_back() {
    let caption = render(&release(93_000));
    assert!(caption.starts_with("#mlib-app v=1\n"));
    assert_eq!(parse(&caption), Some(release(93_000)));
}

#[test]
fn captions_that_are_not_a_release_are_refused() {
    let good = render(&release(93_000));
    let json = good.split_once('\n').unwrap().1;
    assert_eq!(parse("#mlib-index v=2\n{\"pushed_at\":1}"), None);
    assert_eq!(parse(&format!("#mlib-app v=2\n{json}")), None, "a later format is not read as this one");
    assert_eq!(parse("#mlib-app v=1"), None, "no JSON line");
    assert_eq!(parse("#mlib-app v=1\n{\"version\":\"0.93.0\"}"), None, "missing fields");
    for broken in [
        AppRelease { code: 0, ..release(93_000) },
        AppRelease { bytes: 0, ..release(93_000) },
        AppRelease { sha256: "xyz".into(), ..release(93_000) },
        AppRelease { version: String::new(), ..release(93_000) },
    ] {
        assert_eq!(parse(&render(&broken)), None, "{broken:?}");
    }
}

#[test]
fn newest_is_the_highest_version_code_then_the_highest_message() {
    let older = render(&release(92_000));
    let newer = render(&release(93_000));
    let candidates = [(older.as_str(), 10), ("#mlib-index v=2\n{}", 11), (newer.as_str(), 9)];
    let (index, chosen) = newest(&candidates).unwrap();
    assert_eq!((index, chosen.code), (2, 93_000));

    let again = [(newer.as_str(), 9), (newer.as_str(), 12)];
    assert_eq!(newest(&again).unwrap().0, 1, "a tie goes to the later message");
    assert_eq!(newest(&[("#mlib-index v=2\n{}", 1)]), None);
}
