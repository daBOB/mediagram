//! How a double episode's file name becomes an episode range.

use super::*;
use mlib_spec::filename::parse_filename;

fn input(episode: Option<u32>) -> ResolveInput {
    ResolveInput {
        episode,
        ..ResolveInput::default()
    }
}

#[test]
fn a_double_episode_keeps_its_range_when_the_same_start_is_passed() {
    let guess = parse_filename("Akte.X.S09E19E20.Die.Wahrheit.mkv").unwrap();
    assert_eq!(
        episode_value(&input(Some(19)), &guess),
        Some(Episode::Range([19, 20]))
    );
    assert_eq!(
        episode_value(&input(None), &guess),
        Some(Episode::Range([19, 20]))
    );
}

#[test]
fn a_different_explicit_episode_overrides_the_range() {
    let guess = parse_filename("Akte.X.S09E19E20.Die.Wahrheit.mkv").unwrap();
    assert_eq!(
        episode_value(&input(Some(5)), &guess),
        Some(Episode::Single(5))
    );
}
