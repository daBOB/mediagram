//! Watchlist, Kids and collection rows — the part of `record.rs`'s format
//! that carries an explicit removal. Split out only to keep `record.rs`
//! under the line limit; the two are one format and this module's parsing
//! leans on `record`'s own hostile-input helpers throughout.

use serde::{Deserialize, Serialize};
use serde_json::Value;

use super::hostile_json::{as_array, js_number, text_};

/// A watchlist entry or a Kids mark: a title, when it last changed, and
/// whether that change was taking it off rather than putting it on.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ListRow {
    pub set_id: String,
    pub updated_at: f64,
    #[serde(default)]
    pub removed: bool,
}

/// A hand-built list, whole: merged as one row, not title by title — see
/// `merge.rs` on why.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CollectionRow {
    pub id: String,
    pub name: String,
    #[serde(default)]
    pub items: Vec<String>,
    pub updated_at: f64,
    #[serde(default)]
    pub removed: bool,
}

pub(super) fn list_row(raw: &Value) -> Option<ListRow> {
    let row = raw.as_object()?;
    let set_id = text_(row.get("setId"))?;
    let updated_at = js_number(row.get("updatedAt"));
    if !updated_at.is_finite() || updated_at <= 0.0 {
        return None;
    }
    let removed = row.get("removed").and_then(Value::as_bool).unwrap_or(false);
    Some(ListRow {
        set_id,
        updated_at,
        removed,
    })
}

/// How long a list's name from another device's document may be — not
/// `lists::MAX_NAME`'s 120: that caps what this player lets someone type,
/// this caps what a stranger's document is allowed to claim.
const MAX_LIST_NAME: usize = 200;

pub(super) fn collection_row(raw: &Value) -> Option<CollectionRow> {
    let row = raw.as_object()?;
    let id = text_(row.get("id"))?;
    let name: String = text_(row.get("name"))?
        .chars()
        .take(MAX_LIST_NAME)
        .collect();
    let updated_at = js_number(row.get("updatedAt"));
    if !updated_at.is_finite() || updated_at <= 0.0 {
        return None;
    }
    let items = as_array(row.get("items"))
        .iter()
        .filter_map(|value| text_(Some(value)))
        .collect();
    let removed = row.get("removed").and_then(Value::as_bool).unwrap_or(false);
    Some(CollectionRow {
        id,
        name,
        items,
        updated_at,
        removed,
    })
}

#[cfg(test)]
#[path = "list_record_tests.rs"]
mod tests;
