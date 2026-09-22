//! Runs the web's channel-update fixtures against this crate's port.
//!
//! `updates.rs` ports `web/src/telegram/updates.ts`; the fixtures under
//! `web/test/fixtures/channel-updates/` pin the two together. The web is
//! authoritative: a case that only passes after a change here does not
//! belong in the fixture.

use std::path::{Path, PathBuf};

use mediagram_core::updates::{ChannelUpdate, Debouncer, LibraryEvent, classify};
use serde::Deserialize;
use serde::de::DeserializeOwned;

const FIXTURES: &str = "../../web/test/fixtures/channel-updates";

fn fixture_path(file: &str) -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join(FIXTURES).join(file)
}

/// `None` if the web checkout is not present, as the other shared-fixture
/// tests allow: the web is a separate deliverable.
fn load<T: DeserializeOwned>(file: &str) -> Option<Vec<T>> {
    let path = fixture_path(file);
    let Ok(text) = std::fs::read_to_string(&path) else {
        eprintln!("skipping: {} is not present", path.display());
        return None;
    };
    Some(serde_json::from_str(&text).unwrap_or_else(|err| panic!("{}: {err}", path.display())))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Context {
    channel: i64,
    own_device: String,
}

#[derive(Deserialize)]
struct ClassifyCase {
    name: String,
    context: Context,
    update: ChannelUpdate,
    expect: Option<LibraryEvent>,
}

#[test]
fn classify_fixtures() {
    let Some(cases) = load::<ClassifyCase>("classify.json") else { return };
    for case in cases {
        let got = classify(&case.update, case.context.channel, &case.context.own_device);
        assert_eq!(got, case.expect, "{}", case.name);
    }
}

#[derive(Deserialize)]
#[serde(untagged)]
enum Step {
    Offer { offer: LibraryEvent, at: u64 },
    Take { take: u64, expect: Vec<LibraryEvent> },
}

#[derive(Deserialize)]
struct DebounceCase {
    name: String,
    window: u64,
    steps: Vec<Step>,
}

#[test]
fn debounce_fixtures() {
    let Some(cases) = load::<DebounceCase>("debounce.json") else { return };
    for case in cases {
        let mut debouncer = Debouncer::new(case.window);
        for step in case.steps {
            match step {
                Step::Offer { offer, at } => debouncer.offer(offer, at),
                Step::Take { take, expect } => assert_eq!(debouncer.take(take), expect, "{}", case.name),
            }
        }
    }
}

#[test]
fn next_due_is_idle_then_the_earliest_window() {
    let mut debouncer = Debouncer::new(5000);
    assert_eq!(debouncer.next_due(), None);
    debouncer.offer(LibraryEvent::Index, 2000);
    debouncer.offer(LibraryEvent::State, 1000);
    assert_eq!(debouncer.next_due(), Some(6000));
    debouncer.take(6000);
    assert_eq!(debouncer.next_due(), Some(7000));
}
