use mediagram_game::{TitleKind, TitlePool};

use super::*;
use crate::catalog::PlayableSet;
use crate::credits::Credited;
use crate::dto::summary_from;

fn set(id: &str, kind: &str, title: Option<&str>, show: Option<&str>, year: u16) -> SetSummary {
    summary_from(&PlayableSet {
        set_id: id.into(),
        kind: kind.into(),
        title: title.map(Into::into),
        show: show.map(Into::into),
        chap: None,
        path: None,
        season: None,
        episode: None,
        tmdb: Some(7),
        year: Some(year),
        container: "mkv".into(),
        vcodec: None,
        acodec: None,
        quality: None,
        hdr: None,
        duration: None,
        total: 0,
        part_count: 1,
        created_at: 0,
        alang: Vec::new(),
        slang: Vec::new(),
    })
}

fn person(name: &str) -> Credited {
    Credited { person_id: 1, name: name.into(), role: None }
}

fn credits(_key: &str) -> Option<TitleCredits> {
    Some(TitleCredits { cast: vec![person("Anna"), person("Ben")], crew: vec![person("Cora")] })
}

fn build(sets: &[SetSummary], kids: &[&str]) -> Vec<GameTitle> {
    let kids = kids.iter().map(|s| s.to_string()).collect();
    game_titles(sets, &credits, &kids)
}

#[test]
fn films_map_one_per_set() {
    let mut a = set("A", "movie", Some("Alien"), None, 1979);
    a.poster_path = Some("/p/a.jpg".into());
    a.backdrop_path = Some("/b/a.jpg".into());
    let b = set("B", "movie", Some("Brazil"), None, 1985);
    let titles = build(&[a, b], &["A"]);
    assert_eq!(titles.len(), 2);
    let t = &titles[0];
    assert_eq!((t.key.as_str(), t.kind, t.title.as_str(), t.year), ("A", TitleKind::Film, "Alien", Some(1979)));
    assert_eq!(t.poster, Some(PathBuf::from("/p/a.jpg")));
    assert_eq!(t.still, Some(PathBuf::from("/b/a.jpg")));
    assert_eq!(t.directors, vec!["Cora".to_string()]);
    assert_eq!(t.cast, vec!["Anna".to_string(), "Ben".into()]);
    assert!(t.kids_ok && !titles[1].kids_ok);
}

#[test]
fn episodes_group_into_one_show() {
    let mut e1 = set("E1", "ep", None, Some("Dark"), 2019);
    let mut e2 = set("E2", "ep", None, Some("Dark"), 2017);
    e2.poster_path = Some("/p/dark.jpg".into());
    e1.backdrop_path = Some("/b/dark.jpg".into());
    let titles = build(&[e1, e2, set("E3", "ep", None, Some("Lost"), 2004)], &[]);
    assert_eq!(titles.len(), 2);
    let t = &titles[0];
    assert_eq!((t.key.as_str(), t.kind, t.title.as_str()), ("show:Dark", TitleKind::Series, "Dark"));
    assert_eq!(t.year, Some(2017));
    assert_eq!(t.poster, Some(PathBuf::from("/p/dark.jpg")));
    assert_eq!(t.still, Some(PathBuf::from("/b/dark.jpg")));
    assert_eq!(t.cast, vec!["Anna".to_string(), "Ben".into()]);
}

#[test]
fn courses_and_documents_are_left_out() {
    let sets = ["tut", "doc", "docu"].map(|k| set(k, k, Some("X"), Some("Y"), 2020));
    assert!(build(&sets, &["tut", "doc", "docu"]).is_empty());
}

#[test]
fn a_show_is_kids_ok_only_if_every_episode_is() {
    let sets = [set("E1", "ep", None, Some("Dark"), 2017), set("E2", "ep", None, Some("Dark"), 2018)];
    assert!(!build(&sets, &["E1"])[0].kids_ok);
    assert!(build(&sets, &["E1", "E2"])[0].kids_ok);
}

#[test]
fn series_have_no_directors() {
    let titles = build(&[set("E1", "ep", None, Some("Dark"), 2017)], &[]);
    assert!(titles[0].directors.is_empty());
}

#[test]
fn kids_locked_keeps_only_kids_titles() {
    let sets = [
        set("A", "movie", Some("Alien"), None, 1979),
        set("B", "movie", Some("Bambi"), None, 1942),
        set("E1", "ep", None, Some("Dark"), 2017),
    ];
    let pool = TitlePool::new(build(&sets, &["B"]), true);
    assert_eq!(pool.eligible(false).len(), 1);
    assert_eq!(pool.get(0).key, "B");
}

#[test]
fn poster_keys_follow_title_keys() {
    let mut bare = set("B", "movie", Some("Bare"), None, 1990);
    bare.poster_key = None;
    let sets = [
        set("A", "movie", Some("Alien"), None, 1979),
        set("E1", "ep", None, Some("Lost"), 2004),
        set("E2", "ep", None, Some("Lost"), 2004),
        bare,
    ];
    let keys = poster_keys(&sets);
    assert_eq!(keys.len(), 2);
    assert_eq!(keys["A"], "tmdb-movie-7");
    assert_eq!(keys["show:Lost"], "tmdb-tv-7");
}
