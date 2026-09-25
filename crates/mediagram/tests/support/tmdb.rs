//! Shared test doubles for TMDB resolution tests: no network access.
pub use mediagram::metadata;

use std::cell::Cell;
use std::collections::{HashMap, VecDeque};
use std::path::Path;
use std::rc::Rc;

use anyhow::Result;
use mediagram_tmdb::tmdb_client::TmdbApi;
use metadata::prompt::Prompter;
use metadata::resolve::ResolvedItem;
use mlib_spec::filename::Guess;
use serde_json::Value;

/// Serves canned TMDB responses keyed by request path; query params are
/// ignored since each test issues at most one distinct request per path.
pub struct FixtureApi {
    fixtures: HashMap<&'static str, Value>,
    calls: Cell<usize>,
}

impl FixtureApi {
    pub fn new(fixtures: &[(&'static str, &str)]) -> Self {
        let dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures/tmdb");
        let mut map = HashMap::new();
        for (path, file) in fixtures {
            let raw = std::fs::read_to_string(dir.join(file))
                .unwrap_or_else(|e| panic!("reading fixture {file}: {e}"));
            let value: Value = serde_json::from_str(&raw)
                .unwrap_or_else(|e| panic!("parsing fixture {file}: {e}"));
            map.insert(*path, value);
        }
        Self {
            fixtures: map,
            calls: Cell::new(0),
        }
    }

    pub fn call_count(&self) -> usize {
        self.calls.get()
    }
}

impl TmdbApi for FixtureApi {
    async fn get_json(&self, path: &str, _query: &[(&str, String)]) -> Result<Value> {
        self.calls.set(self.calls.get() + 1);
        self.fixtures
            .get(path)
            .cloned()
            .ok_or_else(|| anyhow::anyhow!("no fixture registered for path {path}"))
    }
}

/// Always returns the same canned response; used to drive the cache layer.
pub struct StubApi {
    pub calls: Rc<Cell<usize>>,
    pub response: Value,
}

impl TmdbApi for StubApi {
    async fn get_json(&self, _path: &str, _query: &[(&str, String)]) -> Result<Value> {
        self.calls.set(self.calls.get() + 1);
        Ok(self.response.clone())
    }
}

/// Records prompt calls and returns pre-scripted answers; panics on an
/// unscripted call so a test's expectations are enforced precisely.
#[derive(Default)]
pub struct ScriptedPrompter {
    pub select_answers: VecDeque<usize>,
    pub select_calls: usize,
    pub manual_answer: Option<ResolvedItem>,
}

impl Prompter for ScriptedPrompter {
    fn select(&mut self, _question: &str, _candidates: &[String]) -> Result<usize> {
        self.select_calls += 1;
        Ok(self
            .select_answers
            .pop_front()
            .expect("unscripted select() call"))
    }

    fn manual_entry(&mut self, _seed: &Guess) -> Result<ResolvedItem> {
        self.manual_answer
            .take()
            .ok_or_else(|| anyhow::anyhow!("unscripted manual_entry() call"))
    }
}
