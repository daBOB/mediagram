//! `profile-roles-record-parse.json` pins the parse to the web's; these
//! cover the coercions it does not spell out, and the way back out.

use super::*;
use crate::state::record::ProfileState;
use serde_json::json;

fn roles(value: serde_json::Value) -> ProfileRoles {
    profile_roles(value.as_object().unwrap())
}

/// JSON `6.0` is the number 6 to the web (`age === 6`); a `null` time is 0
/// there (`Number(null)`), which a limit may carry.
#[test]
fn a_limit_reads_as_the_web_reads_numbers() {
    assert_eq!(
        roles(json!({ "kidsAge": { "age": 6.0, "updatedAt": null } })).kids_age,
        Some(KidsAge {
            age: 6,
            updated_at: 0.0
        })
    );
    assert_eq!(
        roles(json!({ "kidsAge": { "age": 12.5, "updatedAt": 1 } })).kids_age,
        None
    );
    assert_eq!(roles(json!({ "admin": { "claimedAt": null } })).admin, None);
}

#[test]
fn a_hash_or_salt_of_the_wrong_length_or_case_is_no_pin() {
    let (hash, salt) = ("a".repeat(64), "b".repeat(32));
    let pin = |hash: &str, salt: &str| {
        roles(json!({ "pin": { "hash": hash, "salt": salt, "updatedAt": 1 } })).pin
    };
    assert!(pin(&hash, &salt).is_some());
    assert_eq!(pin(&hash[1..], &salt), None);
    assert_eq!(pin(&hash, &"B".repeat(32)), None);
    assert_eq!(pin(&format!("{}g", &hash[1..]), &salt), None);
}

#[test]
fn the_keys_are_written_in_the_webs_spelling_and_only_when_set() {
    let state: ProfileState = serde_json::from_value(json!({
        "name": "Mia", "kids": true, "kidsAge": { "age": 6, "updatedAt": 3 }, "parent": "Bea"
    }))
    .unwrap();
    let written = serde_json::to_value(&state).unwrap();
    assert_eq!(written["kidsAge"], json!({ "age": 6, "updatedAt": 3.0 }));
    assert_eq!(written["parent"], json!("Bea"));
    assert!(written.get("admin").is_none() && written.get("pin").is_none());

    let plain: ProfileState = serde_json::from_value(json!({ "name": "Bea" })).unwrap();
    let written = serde_json::to_value(&plain).unwrap();
    for key in ["admin", "kids", "kidsAge", "parent", "pin"] {
        assert!(
            written.get(key).is_none(),
            "{key} written for an ordinary profile"
        );
    }
}
