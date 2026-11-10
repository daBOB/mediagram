//! The titles a media app hands the games, in the shape `mediagram-core`
//! writes them: the core's own test produces this same fixture.

use std::path::Path;

use mediagram_game::{GameTitle, TitleKind};

#[test]
fn the_media_apps_titles_parse_as_game_titles() {
    let titles: Vec<GameTitle> =
        serde_json::from_str(include_str!("fixtures/night-titles.json")).expect("the fixture is a title list");
    let [alien, bambi, dark] = titles.as_slice() else { panic!("three titles, got {}", titles.len()) };

    assert_eq!((alien.kind, alien.year, alien.kids_ok), (TitleKind::Film, Some(1979), false));
    assert_eq!(alien.poster.as_deref(), Some(Path::new("/data/posters/alien.jpg")));
    assert_eq!(alien.still.as_deref(), Some(Path::new("/data/backdrops/alien.jpg")));
    assert_eq!((alien.directors.as_slice(), alien.cast.as_slice()), (&["Cora".to_string()][..], &["Anna".to_string(), "Ben".into()][..]));

    assert!(bambi.poster.is_none() && bambi.still.is_none(), "a title without images");

    assert_eq!((dark.key.as_str(), dark.kind, dark.title.as_str()), ("show:Dark", TitleKind::Series, "Dark"));
    assert!(dark.directors.is_empty() && dark.kids_ok);
}
