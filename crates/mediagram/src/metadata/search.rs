//! TMDB search plus the plausibility/auto-pick/prompt rule for disambiguation.

use anyhow::{Context, Result, bail};
use mlib_spec::Kind;
use mlib_spec::filename::Guess;
use strsim::normalized_levenshtein;

use super::prompt::Prompter;
use super::resolve::{ResolvedItem, fetch_details};
use super::tmdb_client::TmdbApi;
use super::tmdb_types::{SearchHit, SearchResponse};

/// A single result is auto-picked when it is the only one whose year falls
/// within this many years of the filename guess.
const YEAR_WINDOW: i32 = 1;
/// Minimum normalized-Levenshtein similarity for an unambiguous auto-pick.
const AUTO_PICK_SIMILARITY: f64 = 0.9;

/// Search TMDB for `guess`, auto-picking an unambiguous hit or prompting
/// over the plausible ones, then fetches full details for the chosen id.
pub(super) async fn search_and_resolve(
    api: &impl TmdbApi,
    guess: &Guess,
    kind: Kind,
    ui: &mut dyn Prompter,
) -> Result<ResolvedItem> {
    // No server-side year filter: TMDB matches it exactly, which would hide the
    // off-by-one release years the client-side window below is meant to tolerate.
    let query = vec![("query", guess.title.clone())];
    let path = match kind {
        Kind::Movie => "/search/movie",
        Kind::Ep => "/search/tv",
        Kind::Tut => bail!("a tutorial has no TMDB entry; courses are described by hand"),
    };

    let value = api.get_json(path, &query).await?;
    let search: SearchResponse =
        serde_json::from_value(value).context("invalid tmdb search response")?;
    if search.results.is_empty() {
        bail!(
            "no tmdb results for \"{}\"; retry with --manual",
            guess.title
        );
    }

    let chosen_id = pick_candidate(&search.results, guess, ui)?;
    fetch_details(api, chosen_id, kind).await
}

/// Applies the plausibility/auto-pick/prompt rule over one page of results.
fn pick_candidate(results: &[SearchHit], guess: &Guess, ui: &mut dyn Prompter) -> Result<u64> {
    let plausible: Vec<&SearchHit> = match guess.year {
        Some(year) => results
            .iter()
            .filter(|h| {
                h.year()
                    .is_some_and(|y| (i32::from(y) - i32::from(year)).abs() <= YEAR_WINDOW)
            })
            .collect(),
        None => results.iter().collect(),
    };

    if plausible.len() == 1 {
        let similarity = normalized_levenshtein(
            &guess.title.to_lowercase(),
            &plausible[0].display_title().to_lowercase(),
        );
        if similarity >= AUTO_PICK_SIMILARITY {
            return Ok(plausible[0].id);
        }
    }

    let candidates: Vec<&SearchHit> = if plausible.is_empty() {
        results.iter().collect()
    } else {
        plausible
    };
    let labels: Vec<String> = candidates.iter().map(|h| render_candidate(h)).collect();
    let question = format!("Multiple TMDB matches for \"{}\"", guess.title);
    let idx = ui.select(&question, &labels)?;
    Ok(candidates[idx].id)
}

/// Renders one candidate as `Title (Year) [tmdb-ID]` for the select prompt.
fn render_candidate(hit: &SearchHit) -> String {
    match hit.year() {
        Some(year) => format!("{} ({}) [tmdb-{}]", hit.display_title(), year, hit.id),
        None => format!("{} [tmdb-{}]", hit.display_title(), hit.id),
    }
}
