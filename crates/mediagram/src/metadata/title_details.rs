//! Reading a title's own description — a film's or a whole series' — out of
//! the provider payload.

use anyhow::Result;
use mlib_spec::Kind;

use mediagram_tmdb::certification::{certification, region_of};
use mediagram_tmdb::details;
use mediagram_tmdb::details::{TitleDetailsRow, from_details};
use mediagram_tmdb::tmdb_client::TmdbApi;

/// What a provider says about one title, ready to record.
///
/// `lang` is only carried through to the row: the client sends the configured
/// language itself, and recording which one answered is how a later change of
/// language is known to have replaced the text.
pub async fn fetch(api: &impl TmdbApi, kind: Kind, id: u64, lang: &str) -> Result<TitleDetailsRow> {
    let mut row = from_details(kind, lang, &details(api, kind, id).await?);
    // The age rating in the language's country. A title whose rating cannot
    // be had keeps its description: a missing rating means "not known to be
    // kid-safe", which is the safe reading, not a reason to record nothing.
    match certification(api, kind, id, &region_of(lang)).await {
        Ok(rating) => row.certification = rating,
        Err(err) => tracing::warn!(id, error = %err, "no age rating for this title"),
    }
    Ok(row)
}
