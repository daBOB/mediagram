use serde_json::{Value, json};

use crate::state::record::parse_record;

fn document(profiles: Value) -> String {
    json!({ "format": 1, "device": "phone", "writtenAt": 5, "profiles": profiles }).to_string()
}

#[test]
fn a_profile_nobody_renamed_writes_neither_key() {
    let record = parse_record(&document(json!([{ "name": "Celine" }]))).unwrap();
    let wire = serde_json::to_value(&record.profiles[0]).unwrap();
    assert_eq!(wire.get("renamedAt"), None);
    assert_eq!(wire.get("formerNames"), None);
}

#[test]
fn a_renamed_profile_round_trips_through_the_wire() {
    let text = document(json!([{ "name": "Celi", "renamedAt": 1_789_000_005_000_u64,
        "formerNames": ["Celine", "Émile"] }]));
    let record = parse_record(&text).unwrap();
    assert_eq!(record.profiles[0].names.renamed_at, 1_789_000_005_000.0);
    assert_eq!(record.profiles[0].names.former_names, ["celine", "émile"]);

    let again = parse_record(&serde_json::to_string(&record).unwrap()).unwrap();
    assert_eq!(again, record);
}
