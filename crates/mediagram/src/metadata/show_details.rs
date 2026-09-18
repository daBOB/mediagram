//! Reading a show's own description out of the provider payload.

use anyhow::Result;
use mlib_spec::Kind;

use super::details::details;
use super::tmdb_client::TmdbApi;
use crate::index::shows::{ShowRow, from_details};

/// What a provider says about one title, ready to record.
///
/// `lang` is only carried through to the row: the client sends the configured
/// language itself, and recording which one answered is how a later change of
/// language is known to have replaced the text.
pub async fn fetch(api: &impl TmdbApi, kind: Kind, id: u64, lang: &str) -> Result<ShowRow> {
    Ok(from_details(kind, lang, &details(api, kind, id).await?))
}
