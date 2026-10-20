use std::borrow::Cow;

use serde_json::json;

use super::resolve;
use crate::state::record::SyncRecord;

fn record(device: &str, profiles: serde_json::Value) -> SyncRecord {
    serde_json::from_value(json!({ "format": 1, "device": device, "writtenAt": 1, "profiles": profiles })).unwrap()
}

#[test]
fn records_nobody_renamed_are_borrowed_not_copied() {
    let records = [record("tv", json!([{ "name": "Ann" }])), record("laptop", json!([{ "name": "Ann" }]))];
    let (resolved, people) = resolve(&records);
    assert!(matches!(resolved, Cow::Borrowed(_)));
    assert_eq!(people["ann"], Default::default());
}

#[test]
fn records_a_rename_respells_are_copied() {
    let records = [
        record("tv", json!([{ "name": "Celine" }])),
        record("laptop", json!([{ "name": "Celi", "renamedAt": 2, "formerNames": ["celine"] }])),
    ];
    let (resolved, _) = resolve(&records);
    assert!(matches!(resolved, Cow::Owned(_)));
    assert_eq!(resolved[0].profiles[0].name, "Celi");
    assert_eq!(records[0].profiles[0].name, "Celine");
}
