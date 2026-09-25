//! How old a cached TMDB answer may be before it is asked for again.
//!
//! By default the cache keeps everything for good, which is what lets a
//! machine with no key describe its library. A refresh asks again for what
//! is older than asked — and a refresh that cannot reach TMDB still answers
//! from what it had, because stale provider data beats none.

use std::cell::Cell;
use std::rc::Rc;
use std::time::Duration;

use anyhow::{Result, bail};
use mediagram_tmdb::disk_cache::DiskCachedApi;
use mediagram_tmdb::tmdb_client::TmdbApi;
use serde_json::{Value, json};

/// Answers with a counter that goes up each time it is asked, or fails.
struct Counting {
    asked: Rc<Cell<u32>>,
    failing: bool,
}

impl TmdbApi for Counting {
    async fn get_json(&self, _path: &str, _query: &[(&str, String)]) -> Result<Value> {
        self.asked.set(self.asked.get() + 1);
        if self.failing {
            bail!("tmdb is unreachable");
        }
        Ok(json!({ "answer": self.asked.get() }))
    }
}

fn api(dir: &std::path::Path, asked: &Rc<Cell<u32>>, failing: bool) -> DiskCachedApi<Counting> {
    DiskCachedApi::new(
        Counting {
            asked: asked.clone(),
            failing,
        },
        dir,
    )
}

#[tokio::test]
async fn without_an_age_an_answer_is_kept_for_good() {
    let dir = tempfile::tempdir().unwrap();
    let asked = Rc::new(Cell::new(0));
    api(dir.path(), &asked, false)
        .get_json("/movie/1", &[])
        .await
        .unwrap();
    let again = api(dir.path(), &asked, false)
        .get_json("/movie/1", &[])
        .await
        .unwrap();
    assert_eq!(asked.get(), 1);
    assert_eq!(again["answer"], 1);
}

#[tokio::test]
async fn an_answer_older_than_the_age_is_asked_for_again() {
    let dir = tempfile::tempdir().unwrap();
    let asked = Rc::new(Cell::new(0));
    api(dir.path(), &asked, false)
        .get_json("/movie/1", &[])
        .await
        .unwrap();
    let refreshed = api(dir.path(), &asked, false)
        .with_max_age(Duration::ZERO)
        .get_json("/movie/1", &[])
        .await
        .unwrap();
    assert_eq!(asked.get(), 2);
    assert_eq!(refreshed["answer"], 2);
}

#[tokio::test]
async fn a_refresh_that_cannot_reach_tmdb_keeps_the_old_answer() {
    let dir = tempfile::tempdir().unwrap();
    let asked = Rc::new(Cell::new(0));
    api(dir.path(), &asked, false)
        .get_json("/movie/1", &[])
        .await
        .unwrap();
    let kept = api(dir.path(), &asked, true)
        .with_max_age(Duration::ZERO)
        .get_json("/movie/1", &[])
        .await
        .unwrap();
    assert_eq!(kept["answer"], 1);
}
