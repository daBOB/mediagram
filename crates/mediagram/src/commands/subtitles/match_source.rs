//! Matches local video files against complete sets already in the index, so
//! `subtitles backfill` knows which file, if any, is the source a set was
//! uploaded from.
//!
//! Two ways to identify a file, tried in order:
//!  1. Its byte size is one complete set's `total`, and only one. A shared
//!     size is ambiguous and never falls through to the second way.
//!  2. (`.mp4` only, when no size matched anything) its parsed show/title,
//!     season/episode or year, and a duration within 2 s of the candidate's,
//!     naming exactly one set. This fallback is reported, never matched
//!     outright: `subtitles backfill` only sends it once a person names the
//!     file with `--accept-fallback` (a later phase).
//!
//! Whichever way found it, two files never claim the same set: both are
//! downgraded to [`Verdict::Conflict`] and neither is used.

use std::collections::HashMap;
use std::path::PathBuf;

use mlib_spec::caption::Episode;
use mlib_spec::filename::Guess;

use crate::index::set_row::SetRow;

/// One local file, as the matcher needs to see it. `duration` and `guess`
/// are only consulted for the fallback, so a caller that already knows the
/// size has no match for is free to leave them unset.
#[derive(Debug, Clone)]
pub struct SourceFile {
    pub path: PathBuf,
    pub size: u64,
    pub is_mp4: bool,
    pub duration: Option<f64>,
    pub guess: Option<Guess>,
}

/// What the matcher decided about one file.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Verdict {
    /// Its size names exactly one complete set.
    Matched(String),
    /// No size match; the name+duration fallback named exactly one set.
    /// Listed only — never sent without an explicit per-file opt-in.
    Fallback(String),
    /// Its size matches more than one complete set's total.
    Ambiguous,
    /// It named the same set another file also named, by size or fallback.
    Conflict(String),
    /// Neither way found a candidate.
    Unmatched,
}

/// One file's verdict.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Match {
    pub path: PathBuf,
    pub verdict: Verdict,
}

/// Matches every file against the pool of candidate sets. `sets` should be
/// every complete `movie`/`ep`/`docu` set, whether or not it already has a
/// subtitle bundle — a bundled set is still a valid source for a re-check.
#[must_use]
pub fn match_sources(files: &[SourceFile], sets: &[SetRow]) -> Vec<Match> {
    let mut by_size: HashMap<u64, Vec<&SetRow>> = HashMap::new();
    for set in sets {
        by_size.entry(set.total).or_default().push(set);
    }

    let mut verdicts: Vec<Verdict> = Vec::with_capacity(files.len());
    let mut claims: HashMap<String, Vec<usize>> = HashMap::new();

    for (i, file) in files.iter().enumerate() {
        let verdict = match by_size.get(&file.size) {
            Some(candidates) if candidates.len() == 1 => Verdict::Matched(candidates[0].set_id.clone()),
            Some(_) => Verdict::Ambiguous,
            None if file.is_mp4 => fallback_candidate(file, sets)
                .map(Verdict::Fallback)
                .unwrap_or(Verdict::Unmatched),
            None => Verdict::Unmatched,
        };
        if let Verdict::Matched(set_id) | Verdict::Fallback(set_id) = &verdict {
            claims.entry(set_id.clone()).or_default().push(i);
        }
        verdicts.push(verdict);
    }

    for (set_id, claimants) in &claims {
        if claimants.len() > 1 {
            for &i in claimants {
                verdicts[i] = Verdict::Conflict(set_id.clone());
            }
        }
    }

    files
        .iter()
        .zip(verdicts)
        .map(|(file, verdict)| Match {
            path: file.path.clone(),
            verdict,
        })
        .collect()
}

/// The single set a name+duration match names, if exactly one does. Never
/// called for a file whose size already matched something — that path is
/// decided by size alone, ambiguous or not.
fn fallback_candidate(file: &SourceFile, sets: &[SetRow]) -> Option<String> {
    let guess = file.guess.as_ref()?;
    let duration = file.duration?;
    let mut candidates = sets
        .iter()
        .filter(|set| duration_close(set.duration, duration) && name_matches(guess, set));
    let first = candidates.next()?;
    if candidates.next().is_some() {
        return None;
    }
    Some(first.set_id.clone())
}

fn duration_close(set_duration: Option<u32>, file_duration: f64) -> bool {
    set_duration.is_some_and(|d| (f64::from(d) - file_duration).abs() <= 2.0)
}

/// Show + SxxEyy for an episode guess, title + year for a film guess.
/// Anything else (an absolute-numbered anime guess, a bare title) never
/// matches: the fallback needs the stronger of the two shapes.
fn name_matches(guess: &Guess, set: &SetRow) -> bool {
    match (guess.season, guess.episode) {
        (Some(season), Some(episode)) => {
            set.season == Some(season)
                && episode_matches(set.episode, episode)
                && set.show.as_deref().is_some_and(|show| same_name(show, &guess.title))
        }
        _ => guess
            .year
            .is_some_and(|year| set.year == Some(year) && set.title.as_deref().is_some_and(|t| same_name(t, &guess.title))),
    }
}

fn episode_matches(set_episode: Option<Episode>, wanted: u32) -> bool {
    match set_episode {
        Some(e) => wanted >= e.first() && wanted <= e.last(),
        None => false,
    }
}

fn same_name(a: &str, b: &str) -> bool {
    a.trim().eq_ignore_ascii_case(b.trim())
}

#[cfg(test)]
#[path = "match_source_tests.rs"]
mod tests;
