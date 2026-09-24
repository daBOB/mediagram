use super::*;
use serde_json::json;

#[test]
fn a_list_row_with_no_id_is_dropped() {
    assert_eq!(list_row(&json!({ "updatedAt": 1 })), None);
}

#[test]
fn a_list_row_with_a_zero_timestamp_is_dropped() {
    assert_eq!(list_row(&json!({ "setId": "01A", "updatedAt": 0 })), None);
}

#[test]
fn a_list_row_reads_removed_only_when_it_is_literally_true() {
    assert_eq!(
        list_row(&json!({ "setId": "01A", "updatedAt": 1 })),
        Some(ListRow {
            set_id: "01A".into(),
            updated_at: 1.0,
            removed: false
        })
    );
    assert_eq!(
        list_row(&json!({ "setId": "01A", "updatedAt": 1, "removed": true })),
        Some(ListRow {
            set_id: "01A".into(),
            updated_at: 1.0,
            removed: true
        })
    );
    assert_eq!(
        list_row(&json!({ "setId": "01A", "updatedAt": 1, "removed": "true" })),
        Some(ListRow {
            set_id: "01A".into(),
            updated_at: 1.0,
            removed: false
        })
    );
}

#[test]
fn a_collection_names_length_from_another_devices_document_is_capped() {
    let long = "x".repeat(500);
    let row = collection_row(
        &json!({ "id": "c1", "name": format!("  {long}  "), "items": [], "updatedAt": 1 }),
    )
    .unwrap();
    assert_eq!(row.name.chars().count(), 200);
}

#[test]
fn a_collection_with_only_whitespace_for_a_name_is_dropped() {
    assert_eq!(
        collection_row(&json!({ "id": "c1", "name": "   ", "items": [], "updatedAt": 1 })),
        None
    );
}

#[test]
fn a_collections_items_drop_anything_that_is_not_a_non_empty_string() {
    let row = collection_row(
        &json!({ "id": "c1", "name": "Kept", "items": ["01A", "", 7, null, "01B"], "updatedAt": 1 }),
    )
    .unwrap();
    assert_eq!(row.items, vec!["01A".to_string(), "01B".to_string()]);
}
