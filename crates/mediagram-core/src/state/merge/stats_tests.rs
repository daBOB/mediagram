use serde_json::{Value, json};

use crate::state::merge::merge_states;
use crate::state::record::{SyncRecord, parse_record};

fn record(device: &str, title_stats: Value, day_stats: Value) -> SyncRecord {
    let body = json!({ "format": 1, "device": device, "writtenAt": 1,
        "profiles": [{ "name": "André", "titleStats": title_stats, "dayStats": day_stats }] });
    parse_record(&body.to_string()).unwrap()
}

fn title(set_id: &str, device: &str, seconds: f64, updated_at: f64) -> Value {
    json!({ "setId": set_id, "device": device, "startedAt": 1, "lastWatchedAt": updated_at,
            "seconds": seconds, "updatedAt": updated_at })
}

fn day(device: &str, seconds: f64, updated_at: f64) -> Value {
    json!({ "day": "2026-10-03", "device": device, "seconds": seconds, "updatedAt": updated_at })
}

/// The laptop's own row, and an older copy of it the phone still passes on:
/// in either order the laptop's newest counts once, and the phone's own row
/// stands beside it.
#[test]
fn each_devices_newest_row_survives_and_counts_once() {
    let laptop = record(
        "laptop",
        json!([title("01A", "laptop", 600.0, 20.0)]),
        json!([day("laptop", 600.0, 20.0)]),
    );
    let phone = record(
        "phone",
        json!([
            title("01A", "laptop", 300.0, 10.0),
            title("01A", "phone", 120.0, 15.0)
        ]),
        json!([day("laptop", 300.0, 10.0)]),
    );
    for records in [vec![laptop.clone(), phone.clone()], vec![phone, laptop]] {
        let merged = merge_states(&records);
        let mut titles: Vec<(String, f64)> = merged.profiles[0]
            .title_stats
            .iter()
            .map(|row| (row.device.clone(), row.seconds))
            .collect();
        titles.sort_by(|a, b| a.0.cmp(&b.0));
        assert_eq!(
            titles,
            [("laptop".to_string(), 600.0), ("phone".to_string(), 120.0)]
        );
        let days: Vec<f64> = merged.profiles[0]
            .day_stats
            .iter()
            .map(|row| row.seconds)
            .collect();
        assert_eq!(days, [600.0]);
    }
}

/// `01A` on device `bc` and `01Ab` on device `c` join to the same text; they
/// are still two rows.
#[test]
fn a_key_cannot_be_forged_by_where_its_halves_split() {
    let a = record("a", json!([title("01A", "bc", 1.0, 1.0)]), json!([]));
    let b = record("b", json!([title("01Ab", "c", 2.0, 1.0)]), json!([]));
    assert_eq!(merge_states(&[a, b]).profiles[0].title_stats.len(), 2);
}
