use super::*;

/// A moment after every stamp these tests write.
const NOW: i64 = 1_800_000_000;

const INDEX: &str = "#mlib-index v=2\n{\"pushed_at\":1781568000,\"sets\":538,\"schema\":6}";

/// An index caption pushed at `at`, as `push-index` writes one.
fn pushed(at: i64) -> String {
    mlib_spec::index_caption::render(at, 538)
}

/// Captions with ascending ids, which is the order a channel hands them
/// back in; the ids only ever break a tie.
fn found<'a>(texts: &[&'a str]) -> Vec<(&'a str, i64)> {
    texts
        .iter()
        .enumerate()
        .map(|(i, text)| (*text, i as i64))
        .collect()
}

fn message(text: &str) -> CoreError {
    pick_index(&found(&[text]), NOW).unwrap_err()
}

fn reason(err: &CoreError) -> String {
    err.to_string()
}

#[test]
fn a_channel_holding_no_snapshot_says_so() {
    assert_eq!(
        reason(&pick_index(&[], NOW).unwrap_err()),
        format!("library error: {NOTHING_PINNED}")
    );
}

#[test]
fn pins_that_are_not_an_index_say_so_rather_than_nothing_is_pinned() {
    let err = message("welcome to the channel");
    assert_eq!(reason(&err), format!("library error: {NOT_AN_INDEX}"));
}

/// Every named channel state, one sentence each. A person reading one has to be
/// able to tell which of them they are in.
#[test]
fn every_named_channel_failure_reads_differently() {
    let messages = [
        reason(&pick_index(&[], NOW).unwrap_err()),
        reason(&message("welcome")),
        reason(&CoreError::Library(UNREADABLE.into())),
        reason(&CoreError::NotAuthorized(NO_LONGER_VISIBLE.into())),
    ];
    for (i, one) in messages.iter().enumerate() {
        for other in &messages[i + 1..] {
            assert_ne!(one, other);
        }
    }
}

/// Whatever these sentences say, they may not say it with an id: they
/// travel to the caller verbatim.
#[test]
fn no_channel_failure_message_carries_an_id() {
    let digits = |text: &str| text.chars().filter(char::is_ascii_digit).count();
    assert_eq!(digits(NOTHING_PINNED), 0);
    assert_eq!(digits(NOT_AN_INDEX), 0);
    assert_eq!(digits(UNREADABLE), 0);
    assert_eq!(digits(NO_LONGER_VISIBLE), 0);
}

#[test]
fn a_snapshots_own_timestamp_names_the_version_it_installs() {
    assert_eq!(pushed_at(INDEX, NOW), 1_781_568_000);
}

#[test]
fn a_caption_this_build_cannot_parse_still_installs_its_index() {
    assert_eq!(pushed_at("#mlib-index v=9", 99), 99);
    assert_eq!(pushed_at("#mlib-index v=9\nnot json", 99), 99);
    assert_eq!(pushed_at("#mlib-index v=9\n{\"pushed_at\":0}", 99), 99);
}

fn rpc(code: i32, name: &str) -> InvocationError {
    InvocationError::Rpc(grammers_mtsender::RpcError {
        code,
        name: name.into(),
        value: None,
        caused_by: None,
    })
}

#[test]
fn a_channel_this_account_cannot_address_is_not_reported_as_a_network_blip() {
    assert_eq!(
        reason(&channel_error(&rpc(400, "CHANNEL_INVALID"))),
        format!("not authorized: {NO_LONGER_VISIBLE}"),
    );
    assert_eq!(
        reason(&channel_error(&rpc(403, "CHANNEL_PRIVATE"))),
        format!("not authorized: {NO_LONGER_VISIBLE}"),
    );
}

/// The other half: a server-side wobble must not tell someone they have
/// lost access to their own channel.
#[test]
fn a_server_side_failure_is_reported_as_one() {
    assert!(reason(&channel_error(&rpc(500, "INTERNAL"))).starts_with("network error"));
}

/// A snapshot dated far ahead still installs when it is the only index there
/// is, named for now rather than for the year it claims.
#[test]
fn a_stamp_from_the_future_names_its_version_for_now() {
    assert_eq!(pushed_at(&pushed(NOW + 400 * 24 * 60 * 60), NOW), NOW);
    assert_eq!(pushed_at(&pushed(NOW + 60), NOW), NOW + 60);
}

/// The cases the web's port is checked against, run here as well so the
/// two cannot choose different snapshots. Core is the authority: a case that
/// passes only after a change on the web does not belong in the file.
#[test]
fn the_shared_pick_index_fixtures_hold() {
    #[derive(serde::Deserialize)]
    struct Fixture {
        now: i64,
        cases: Vec<Case>,
    }
    #[derive(serde::Deserialize)]
    struct Case {
        name: String,
        candidates: Vec<Candidate>,
        expect: serde_json::Value,
    }
    #[derive(serde::Deserialize)]
    struct Candidate {
        text: String,
        id: i64,
    }

    let path = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../../web/test/fixtures/pick-index/cases.json");
    // Skipped without the web checkout, as the other shared fixtures are.
    let Ok(text) = std::fs::read_to_string(&path) else {
        eprintln!("skipping: {} is not present", path.display());
        return;
    };
    let fixture: Fixture = serde_json::from_str(&text).unwrap();
    for case in fixture.cases {
        let candidates: Vec<(&str, i64)> = case
            .candidates
            .iter()
            .map(|c| (c.text.as_str(), c.id))
            .collect();
        let got = match pick_index(&candidates, fixture.now) {
            Ok(index) => serde_json::json!(index),
            Err(err) if err.to_string().ends_with(NOTHING_PINNED) => {
                serde_json::json!("nothing-pinned")
            }
            Err(err) if err.to_string().ends_with(NOT_AN_INDEX) => {
                serde_json::json!("not-an-index")
            }
            Err(err) => panic!("{}: unexpected {err}", case.name),
        };
        assert_eq!(got, case.expect, "{}", case.name);
    }
}
