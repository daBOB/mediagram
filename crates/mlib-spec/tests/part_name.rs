//! `base_name` for episode numbering styles the crate's own unit tests don't
//! cover (absolute-only, season/episode, episode range), plus a
//! `part_file_name` edge the unit tests miss: an empty base.

use mlib_spec::caption::{Caption, Episode, Kind, Part};
use mlib_spec::ids::ProviderIds;
use mlib_spec::part_name::{base_name, part_file_name};

fn episode(show: &str, s: Option<u32>, e: Option<Episode>, abs: Option<u32>) -> Caption {
    Caption {
        cid: None,
        chap: None,
        path: None,
        t: Kind::Ep,
        ids: ProviderIds::default(),
        show: Some(show.to_string()),
        title: None,
        year: Some(1995),
        s,
        e,
        abs,
        q: None,
        hdr: None,
        container: "mkv".into(),
        vcodec: None,
        acodec: None,
        alang: vec![],
        slang: vec![],
        dur: None,
        variant: None,
        set: "01ABC".into(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 100,
            sha256: "abc".into(),
        },
        total: 100,
    }
}

#[test]
fn an_empty_base_name_still_produces_a_valid_extension_only_file_name() {
    let result = part_file_name("", "mkv", 0, 1);
    assert_eq!(result, ".mkv");
}

#[test]
fn absolute_only_numbering_is_zero_padded_to_three_digits() {
    let name = base_name(&episode("Evangelion", None, None, Some(42)));
    assert_eq!(name, "Evangelion (1995) - 042");
}

#[test]
fn season_and_single_episode_use_the_lowercase_sxxeyy_code() {
    let name = base_name(&episode("The Office", Some(1), Some(Episode::Single(1)), None));
    assert_eq!(name, "The Office (1995) - s01e01");
}

#[test]
fn a_multi_episode_range_is_rendered_as_sxxeyy_dash_eyy() {
    let name = base_name(&episode(
        "Breaking Bad",
        Some(5),
        Some(Episode::Range([14, 16])),
        None,
    ));
    assert_eq!(name, "Breaking Bad (1995) - s05e14-e16");
}
