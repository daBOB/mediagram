//! The kids flag on a synced profile: read only from a literal `true`,
//! written only when set, and sticky across a merge.

use mediagram_core::state::merge::merge_states;
use mediagram_core::state::record::parse_record;

const KIDS_AND_NOT: &str = r#"{"format":1,"device":"tablet","writtenAt":1,"profiles":[
  {"name":"Mia","kids":true,"progress":[],"watched":[]},
  {"name":"Ben","kids":"yes","progress":[],"watched":[]}]}"#;

#[test]
fn only_a_literal_true_marks_a_kids_profile() {
    let record = parse_record(KIDS_AND_NOT).unwrap();
    assert!(record.profiles[0].kids);
    assert!(!record.profiles[1].kids);
}

#[test]
fn an_ordinary_profile_is_written_without_the_key() {
    let record = parse_record(KIDS_AND_NOT).unwrap();
    let written = serde_json::to_value(&record).unwrap();
    assert_eq!(
        written["profiles"][0]["kids"],
        serde_json::Value::Bool(true)
    );
    assert!(written["profiles"][1].get("kids").is_none());
}

#[test]
fn any_device_saying_kids_makes_the_merged_viewer_kids() {
    let with = parse_record(
        r#"{"format":1,"device":"laptop","writtenAt":0,"profiles":[{"name":"Mia","kids":true}]}"#,
    )
    .unwrap();
    let without = parse_record(
        r#"{"format":1,"device":"desktop","writtenAt":0,"profiles":[{"name":"mia"}]}"#,
    )
    .unwrap();
    for order in [vec![with.clone(), without.clone()], vec![without, with]] {
        let merged = merge_states(&order);
        assert_eq!(merged.profiles.len(), 1);
        assert!(merged.profiles[0].kids);
    }
}
