//! The films and series a game night draws on, built from the catalog the
//! app already lists and the credits it already holds.

use std::collections::{HashMap, HashSet};
use std::path::PathBuf;

use mediagram_game::{GameTitle, TitleKind};

use crate::credits::{Credited, TitleCredits};
use crate::dto::SetSummary;

type Credits<'a> = &'a dyn Fn(&str) -> Option<TitleCredits>;

fn names(people: &[Credited]) -> Vec<String> {
    people.iter().map(|p| p.name.clone()).collect()
}

fn show_key(show: &str) -> String {
    format!("show:{show}")
}

fn film(set: &SetSummary, credits: Credits, kids: &HashSet<String>) -> Option<GameTitle> {
    let credited = set.poster_key.as_deref().and_then(credits).unwrap_or_default();
    Some(GameTitle {
        key: set.set_id.clone(),
        kind: TitleKind::Film,
        // A film without a title cannot be asked about.
        title: set.title.clone()?,
        year: set.year.and_then(|y| u16::try_from(y).ok()),
        poster: set.poster_path.as_ref().map(PathBuf::from),
        still: set.backdrop_path.as_ref().map(PathBuf::from),
        directors: names(&credited.crew),
        cast: names(&credited.cast),
        kids_ok: kids.contains(&set.set_id),
    })
}

/// One show from all of its episode sets, in catalog order.
fn series(show: &str, eps: &[&SetSummary], credits: Credits, kids: &HashSet<String>) -> GameTitle {
    // Episodes of one show share a poster key, so the first that has one is
    // as good as any.
    let credited = eps.iter().find_map(|e| e.poster_key.as_deref()).and_then(credits).unwrap_or_default();
    GameTitle {
        key: show_key(show),
        kind: TitleKind::Series,
        title: show.to_string(),
        year: eps.iter().filter_map(|e| e.year).min().and_then(|y| u16::try_from(y).ok()),
        poster: eps.iter().find_map(|e| e.poster_path.as_ref()).map(PathBuf::from),
        still: eps.iter().find_map(|e| e.backdrop_path.as_ref()).map(PathBuf::from),
        // A series' crew is its creators, not directors a player would name.
        directors: Vec::new(),
        cast: names(&credited.cast),
        // One unsafe episode makes the whole show unsafe: a question shows
        // the show, and cannot show only its safe half.
        kids_ok: eps.iter().all(|e| kids.contains(&e.set_id)),
    }
}

/// Films one per set, series one per show; courses and documentaries are not
/// something a party guesses at. `kids_set_ids` and `visible_set_ids` are
/// decided by the caller, so the kids and section rules each live in one
/// place. A show is visible when any of its episodes is, as the library
/// builds its shows from the visible sets.
pub fn game_titles(
    sets: &[SetSummary],
    credits: Credits,
    kids_set_ids: &HashSet<String>,
    visible_set_ids: &HashSet<String>,
) -> Vec<GameTitle> {
    let mut out = Vec::new();
    let mut shows: Vec<&str> = Vec::new();
    let mut episodes: HashMap<&str, Vec<&SetSummary>> = HashMap::new();
    for set in sets {
        match (set.kind.as_str(), set.show.as_deref()) {
            ("movie", _) if visible_set_ids.contains(&set.set_id) => out.extend(film(set, credits, kids_set_ids)),
            ("ep", Some(show)) => {
                if !episodes.contains_key(show) {
                    shows.push(show);
                }
                episodes.entry(show).or_default().push(set);
            }
            _ => {}
        }
    }
    out.extend(
        shows
            .into_iter()
            .filter(|s| episodes[s].iter().any(|e| visible_set_ids.contains(&e.set_id)))
            .map(|s| series(s, &episodes[s], credits, kids_set_ids)),
    );
    out
}

/// Each title's key against its poster key, for the titles that have one.
pub fn poster_keys(sets: &[SetSummary]) -> HashMap<String, String> {
    let mut keys = HashMap::new();
    for set in sets {
        let Some(poster_key) = &set.poster_key else { continue };
        match (set.kind.as_str(), set.show.as_deref()) {
            ("movie", _) => {
                keys.insert(set.set_id.clone(), poster_key.clone());
            }
            ("ep", Some(show)) => {
                // Episodes of one show share a poster key.
                keys.entry(show_key(show)).or_insert_with(|| poster_key.clone());
            }
            _ => {}
        }
    }
    keys
}

#[cfg(test)]
#[path = "game_titles_tests.rs"]
mod tests;
