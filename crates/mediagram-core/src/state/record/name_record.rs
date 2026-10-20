//! A renamed profile on the sync record: when it took the name it has now,
//! and the names it had before. A port of `web/src/state/name-record.ts`,
//! pinned to it by `profile-renames.json`.
//!
//! Sync knows a viewer by its name, so a rename would otherwise make a new
//! viewer on every device. These two keys let the merge (`merge/renames.rs`)
//! treat a name and its former names as one person. New optional keys, not a
//! `SYNC_FORMAT` bump: an older reader drops them and reads a profile under
//! its new name.
//!
//! Read as hostile as the rest of the document: each key is checked on its
//! own and a bad one is dropped on its own, never the profile.

use std::collections::BTreeSet;

use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};

use super::hostile_json::js_number;
use super::{is_stamp, js_collapse, normal_name};

/// The keys of a profile's entry that say what it was called.
#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NameKeys {
    /// When this profile took the name it has now; 0, and not written, when
    /// nobody renamed it.
    #[serde(default, skip_serializing_if = "is_zero")]
    pub renamed_at: f64,
    /// Every name it had before, normalised, sorted by bytes, each once,
    /// never its own; not written when empty.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub former_names: Vec<String>,
}

/// A bound on what a document makes a device store; a longer list is
/// dropped whole.
pub(crate) const MAX_FORMER_NAMES: usize = 32;
/// Three times what a stored name may be (`profiles.rs`), in code points:
/// NFC and lower-casing can lengthen a name a device was allowed to store
/// ("İ" becomes "i̇").
const MAX_NAME: usize = 3 * 120;

fn is_zero(at: &f64) -> bool {
    *at == 0.0
}

/// Each name once, by UTF-8 bytes: `String`'s order, and the web's
/// `Buffer.compare`.
pub(crate) fn sorted_names(names: impl IntoIterator<Item = String>) -> Vec<String> {
    names.into_iter().collect::<BTreeSet<_>>().into_iter().collect()
}

pub(crate) fn name_keys(row: &Map<String, Value>, own: &str) -> NameKeys {
    let renamed_at = js_number(row.get("renamedAt"));
    let renamed_at = if is_stamp(renamed_at) { renamed_at } else { 0.0 };

    let former_names = match row.get("formerNames") {
        Some(Value::Array(listed)) if listed.len() <= MAX_FORMER_NAMES => {
            let own = normal_name(own);
            sorted_names(listed.iter().filter_map(former_name).filter(|name| Some(name) != own.as_ref()))
        }
        _ => Vec::new(),
    };
    NameKeys { renamed_at, former_names }
}

/// `cleanName` without its cut: an over-long name is dropped, not shortened
/// into another.
fn former_name(entry: &Value) -> Option<String> {
    let clean = js_collapse(entry.as_str()?);
    if clean.is_empty() || clean.chars().count() > MAX_NAME {
        return None;
    }
    normal_name(&clean)
}

#[cfg(test)]
#[path = "name_record_tests.rs"]
mod tests;
