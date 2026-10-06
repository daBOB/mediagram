use super::{Kept, absorb, rows};
use crate::state::record::SyncPreference;

fn pref(scope: &str, name: &str, value: &str, updated_at: f64) -> SyncPreference {
    SyncPreference {
        scope: scope.into(),
        name: name.into(),
        value: value.into(),
        updated_at,
    }
}

/// Folds `docs` in the order given and answers the kept rows, sorted so a
/// test does not depend on the map's order.
fn merged(docs: &[(&str, Vec<SyncPreference>)]) -> Vec<SyncPreference> {
    let mut kept = Kept::new();
    for (device, prefs) in docs {
        absorb(&mut kept, prefs, device);
    }
    let mut out = rows(kept);
    out.sort_by(|a, b| (&a.scope, &a.name).cmp(&(&b.scope, &b.name)));
    out
}

#[test]
fn the_newest_choice_for_a_scope_and_name_wins_in_either_order() {
    let laptop = ("laptop", vec![pref("show:x", "subtitle", "de", 20.0)]);
    let phone = ("phone", vec![pref("show:x", "subtitle", "en", 10.0)]);
    for docs in [[laptop.clone(), phone.clone()], [phone, laptop]] {
        assert_eq!(merged(&docs), [pref("show:x", "subtitle", "de", 20.0)]);
    }
}

#[test]
fn a_tie_goes_to_the_greater_device_id() {
    let laptop = ("laptop", vec![pref("show:x", "subtitle", "de", 10.0)]);
    let phone = ("phone", vec![pref("show:x", "subtitle", "en", 10.0)]);
    for docs in [[laptop.clone(), phone.clone()], [phone, laptop]] {
        assert_eq!(merged(&docs), [pref("show:x", "subtitle", "en", 10.0)]);
    }
}

/// The language and the cue size of one show are decided independently: a
/// newer cue size from one device does not carry its older language along.
#[test]
fn each_name_in_a_scope_is_decided_on_its_own() {
    let laptop = (
        "laptop",
        vec![
            pref("show:x", "subtitle", "de", 30.0),
            pref("show:x", "cue-size", "s", 10.0),
        ],
    );
    let phone = (
        "phone",
        vec![
            pref("show:x", "subtitle", "en", 10.0),
            pref("show:x", "cue-size", "l", 30.0),
        ],
    );
    assert_eq!(
        merged(&[laptop, phone]),
        [
            pref("show:x", "cue-size", "l", 30.0),
            pref("show:x", "subtitle", "de", 30.0),
        ]
    );
}

/// `show:x` + `subtitle` and `show:xsub` + `title` join to the same text;
/// they are still two choices.
#[test]
fn a_key_cannot_be_forged_by_where_scope_and_name_split() {
    let one = ("a", vec![pref("show:x", "subtitle", "de", 10.0)]);
    let other = ("b", vec![pref("show:xsub", "title", "en", 20.0)]);
    assert_eq!(merged(&[one, other]).len(), 2);
}
