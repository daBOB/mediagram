//! Asking TMDB for titles in the library's language.
//!
//! TMDB answers in English unless told otherwise, so a German library gets
//! "Forsaken" where the file on disk says "Verlassen". TMDB has both; the
//! client simply never asked.
//!
//! The language has to be added *ahead of* the disk cache. Added behind it,
//! two languages would share one cache entry and the second would be served
//! the first's answer.

use std::cell::RefCell;

use mediagram::metadata::tmdb_client::{Localized, TmdbApi};
use serde_json::{Value, json};

/// One call: the path asked for, and the query it was asked with.
type Call = (String, Vec<(String, String)>);

/// Records what its caller asked for.
#[derive(Default)]
struct Recorder {
    seen: RefCell<Vec<Call>>,
}

impl TmdbApi for Recorder {
    async fn get_json(&self, path: &str, query: &[(&str, String)]) -> anyhow::Result<Value> {
        self.seen.borrow_mut().push((
            path.to_string(),
            query
                .iter()
                .map(|(k, v)| (k.to_string(), v.clone()))
                .collect(),
        ));
        Ok(json!({}))
    }
}

fn language_of(seen: &[Call]) -> Option<String> {
    seen.first()?
        .1
        .iter()
        .find(|(key, _)| key == "language")
        .map(|(_, value)| value.clone())
}

#[tokio::test]
async fn every_request_carries_the_configured_language() {
    let api = Localized::new(Recorder::default(), "de-DE");

    api.get_json("/tv/240459", &[]).await.unwrap();

    assert_eq!(
        language_of(&api.inner().seen.borrow()),
        Some("de-DE".into())
    );
}

#[tokio::test]
async fn the_caller_keeps_whatever_else_it_asked_for() {
    let api = Localized::new(Recorder::default(), "de-DE");

    api.get_json("/search/tv", &[("query", "Spartacus".to_string())])
        .await
        .unwrap();

    let seen = api.inner().seen.borrow();
    let sent = &seen[0].1;
    assert!(sent.iter().any(|(k, v)| k == "query" && v == "Spartacus"));
    assert!(sent.iter().any(|(k, _)| k == "language"));
}

/// A call that names its own language means it: nothing overrides it.
#[tokio::test]
async fn a_caller_that_names_a_language_wins() {
    let api = Localized::new(Recorder::default(), "de-DE");

    api.get_json("/tv/1", &[("language", "en-US".to_string())])
        .await
        .unwrap();

    let seen = api.inner().seen.borrow();
    let languages: Vec<&String> = seen[0]
        .1
        .iter()
        .filter(|(k, _)| k == "language")
        .map(|(_, v)| v)
        .collect();
    assert_eq!(languages, vec!["en-US"], "exactly one, and the caller's");
}

/// The reason this wraps the cache rather than sitting inside the client:
/// the cache keys on the query it is handed, so the language must be in it.
#[tokio::test]
async fn two_languages_are_two_different_requests() {
    let german = Localized::new(Recorder::default(), "de-DE");
    let english = Localized::new(Recorder::default(), "en-US");

    german.get_json("/tv/240459/season/1", &[]).await.unwrap();
    english.get_json("/tv/240459/season/1", &[]).await.unwrap();

    assert_ne!(
        german.inner().seen.borrow()[0].1,
        english.inner().seen.borrow()[0].1,
        "the query must differ, or one cache entry would serve both"
    );
}
