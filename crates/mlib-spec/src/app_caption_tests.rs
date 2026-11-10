use super::*;

fn release(code: i64) -> AppRelease {
    AppRelease {
        version: format!("0.{code}.0"),
        code,
        bytes: 47_185_920,
        sha256: "a".repeat(64),
        published_at: 1_790_900_000,
        profile: None,
    }
}

fn profile() -> AppProfile {
    AppProfile { message: 4201, bytes: 30_412, sha256: "b".repeat(64) }
}

#[test]
fn a_rendered_caption_reads_back() {
    let caption = render(MEDIA_APP_TAG, &release(93_000));
    assert!(caption.starts_with("#mlib-app v=1\n"));
    assert_eq!(parse(MEDIA_APP_TAG, &caption), Some(release(93_000)));
}

#[test]
fn captions_that_are_not_a_release_are_refused() {
    let good = render(MEDIA_APP_TAG, &release(93_000));
    let json = good.split_once('\n').unwrap().1;
    assert_eq!(parse(MEDIA_APP_TAG, "#mlib-index v=2\n{\"pushed_at\":1}"), None);
    assert_eq!(parse(MEDIA_APP_TAG, &format!("#mlib-app v=2\n{json}")), None, "a later format is not read as this one");
    assert_eq!(parse(MEDIA_APP_TAG, "#mlib-app v=1"), None, "no JSON line");
    assert_eq!(parse(MEDIA_APP_TAG, "#mlib-app v=1\n{\"version\":\"0.93.0\"}"), None, "missing fields");
    for broken in [
        AppRelease { code: 0, ..release(93_000) },
        AppRelease { bytes: 0, ..release(93_000) },
        AppRelease { sha256: "xyz".into(), ..release(93_000) },
        AppRelease { version: String::new(), ..release(93_000) },
    ] {
        assert_eq!(parse(MEDIA_APP_TAG, &render(MEDIA_APP_TAG, &broken)), None, "{broken:?}");
    }
}

#[test]
fn newest_is_the_highest_version_code_then_the_highest_message() {
    let older = render(MEDIA_APP_TAG, &release(92_000));
    let newer = render(MEDIA_APP_TAG, &release(93_000));
    let candidates = [(older.as_str(), 10), ("#mlib-index v=2\n{}", 11), (newer.as_str(), 9)];
    let (index, chosen) = newest(MEDIA_APP_TAG, &candidates).unwrap();
    assert_eq!((index, chosen.code), (2, 93_000));

    let again = [(newer.as_str(), 9), (newer.as_str(), 12)];
    assert_eq!(newest(MEDIA_APP_TAG, &again).unwrap().0, 1, "a tie goes to the later message");
    assert_eq!(newest(MEDIA_APP_TAG, &[("#mlib-index v=2\n{}", 1)]), None);
}

#[test]
fn a_profile_reads_back_and_a_caption_without_one_is_unchanged() {
    let with = AppRelease { profile: Some(profile()), ..release(93_000) };
    assert_eq!(parse(MEDIA_APP_TAG, &render(MEDIA_APP_TAG, &with)), Some(with));
    assert!(!render(MEDIA_APP_TAG, &release(93_000)).contains("profile"), "no profile, no field");
}

#[test]
fn an_unusable_profile_is_dropped_and_the_release_kept() {
    for broken in [
        AppProfile { message: 0, ..profile() },
        AppProfile { bytes: 0, ..profile() },
        AppProfile { sha256: "xyz".into(), ..profile() },
    ] {
        let caption = render(MEDIA_APP_TAG, &AppRelease { profile: Some(broken), ..release(93_000) });
        assert_eq!(parse(MEDIA_APP_TAG, &caption), Some(release(93_000)));
    }
}

#[test]
fn a_profile_of_the_wrong_shape_is_dropped_and_the_release_kept() {
    let good = render(MEDIA_APP_TAG, &release(93_000));
    let json = good.split_once('\n').unwrap().1.trim_end_matches('}');
    for profile in [r#""x""#, "{}", r#"{"message":"4201","bytes":1,"sha256":"b"}"#, r#"{"message":1,"bytes":-1,"sha256":"b"}"#] {
        let caption = format!("#mlib-app v=1\n{json},\"profile\":{profile}}}");
        assert_eq!(parse(MEDIA_APP_TAG, &caption), Some(release(93_000)), "{caption}");
    }
}

/// What a reader built before `profile` existed does with a caption that has one:
/// the same struct minus the field, which serde skips.
#[test]
fn a_reader_without_profile_still_reads_a_caption_with_one() {
    #[derive(serde::Deserialize)]
    #[allow(dead_code)]
    struct V1Release {
        version: String,
        code: i64,
        bytes: u64,
        sha256: String,
        published_at: i64,
    }
    let caption = render(MEDIA_APP_TAG, &AppRelease { profile: Some(profile()), ..release(93_000) });
    let json = caption.split_once('\n').unwrap().1;
    let old: V1Release = serde_json::from_str(json).unwrap();
    assert_eq!(old.code, 93_000);
}

#[test]
fn each_app_reads_only_its_own_tag() {
    let games = render(SPIELTISCH_TAG, &release(175_000));
    assert!(games.starts_with("#spieltisch-app v=1\n"));
    assert_eq!(parse(SPIELTISCH_TAG, &games), Some(release(175_000)));
    assert_eq!(parse(MEDIA_APP_TAG, &games), None, "a media app never takes a games release");
    assert_eq!(parse(SPIELTISCH_TAG, &render(MEDIA_APP_TAG, &release(174_001))), None);
    assert_eq!(parse(MEDIA_APP_TAG, &games.replacen("#spieltisch-app", "#mlib-apps", 1)), None, "the tag is matched whole");

    let media = render(MEDIA_APP_TAG, &release(174_001));
    let both = [(games.as_str(), 9), (media.as_str(), 8)];
    assert_eq!(newest(MEDIA_APP_TAG, &both).map(|(i, r)| (i, r.code)), Some((1, 174_001)), "a higher games versionCode is not seen");
    assert_eq!(newest(SPIELTISCH_TAG, &both).map(|(i, r)| (i, r.code)), Some((0, 175_000)));
}
