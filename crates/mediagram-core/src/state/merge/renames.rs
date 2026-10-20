//! Who is one person, before anything is merged. A port of
//! `web/src/state/renames.ts`, pinned to it by `profile-renames.json`.
//!
//! Every merge keys a viewer by normalised name, so a renamed profile would
//! otherwise split in two: the new name from current documents, the old one
//! from an older build's or a stale document. This pre-pass joins a name and
//! every former name any entry lists into one person (a union-find over
//! names, so a chain A→B→C joins even from partial documents), picks the
//! name the newest rename gave it, and rewrites every name in every document
//! to that spelling. `merge_states` and the role, stats and household merges
//! then run on the rewritten records exactly as they do without renames.
//!
//! The name shown is the entry with the highest `renamed_at` (absent is 0),
//! ties by the greater device id as `keep`'s are, so with nobody renamed the
//! spelling is chosen exactly as `merge_states` always chose it.
//!
//! Entry names, parents and admins are keyed by `normal_name`, as the rest of
//! the merge keys them; former names by `normal_former_name`, as they are read.

use std::borrow::Cow;
use std::collections::HashMap;

use crate::state::record::{MAX_FORMER_NAMES, NameKeys, SyncRecord, normal_former_name, normal_name, sorted_names};

/// Each normalised name to its parent in the union-find; a person's root
/// points at itself.
type Roots = HashMap<String, String>;

fn find(root: &mut Roots, name: &str) -> String {
    let mut top = name.to_string();
    while root[&top] != top {
        top = root[&top].clone();
    }
    // Every name on the way points at the root now, so a long chain is walked once.
    let mut at = name.to_string();
    while at != top {
        at = root.insert(at, top.clone()).expect("a name on the way is known");
    }
    top
}

fn meet(root: &mut Roots, name: &str) -> String {
    if !root.contains_key(name) {
        root.insert(name.to_string(), name.to_string());
    }
    find(root, name)
}

struct Winner<'a> {
    name: &'a str,
    renamed_at: f64,
    device: &'a str,
}

/// The records with every name of a person spelled as its winner, and each
/// person's name keys under their normalised name. Borrows the records when
/// no name, parent or admin changes, so an install nobody renamed merges
/// without a copy.
pub(super) fn resolve(records: &[SyncRecord]) -> (Cow<'_, [SyncRecord]>, HashMap<String, NameKeys>) {
    let mut root = Roots::new();
    for profile in records.iter().flat_map(|record| &record.profiles) {
        let Some(name) = normal_name(&profile.name) else {
            continue;
        };
        meet(&mut root, &name);
        for other in profile.names.former_names.iter().filter_map(|former| normal_former_name(former)) {
            let joined = meet(&mut root, &other);
            let top = find(&mut root, &name);
            root.insert(joined, top);
        }
    }
    // Each name to its person's root, once, so every lookup below is a read.
    let names: Vec<String> = root.keys().cloned().collect();
    let tops: HashMap<String, String> = names
        .into_iter()
        .map(|name| {
            let top = find(&mut root, &name);
            (name, top)
        })
        .collect();

    let mut winners: HashMap<&str, Winner> = HashMap::new();
    for record in records {
        let device = record.device.as_str();
        for profile in &record.profiles {
            let Some(name) = normal_name(&profile.name) else {
                continue;
            };
            let top = tops[&name].as_str();
            let renamed_at = profile.names.renamed_at;
            // Newer stamp, then greater device; a full tie keeps the entry met first.
            let wins = winners.get(top).is_none_or(|standing| {
                renamed_at > standing.renamed_at || (renamed_at == standing.renamed_at && device > standing.device)
            });
            if wins {
                winners.insert(top, Winner { name: profile.name.trim(), renamed_at, device });
            }
        }
    }

    // The person's spelling of a name, when it is not already spelled so; a
    // name that belongs to nobody stays as it is.
    let respelled = |name: &str| -> Option<String> {
        let top = tops.get(&normal_name(name)?)?;
        let spelling = winners.get(top.as_str())?.name;
        (spelling != name).then(|| spelling.to_string())
    };

    let mut members: HashMap<&str, Vec<String>> = HashMap::new();
    for (name, top) in &tops {
        members.entry(top.as_str()).or_default().push(name.clone());
    }
    let mut people = HashMap::new();
    for (top, winner) in &winners {
        let own = normal_name(winner.name).expect("a winner is a named entry");
        let others = members.remove(top).unwrap_or_default().into_iter().filter(|name| *name != own);
        let former_names = sorted_names(others).into_iter().take(MAX_FORMER_NAMES).collect();
        people.insert(own, NameKeys { renamed_at: winner.renamed_at.max(0.0), former_names });
    }

    let mut rewritten = Cow::Borrowed(records);
    for (at, record) in records.iter().enumerate() {
        for (which, profile) in record.profiles.iter().enumerate() {
            if let Some(name) = respelled(&profile.name) {
                rewritten.to_mut()[at].profiles[which].name = name;
            }
            if let Some(parent) = profile.roles.parent.as_deref().and_then(respelled) {
                rewritten.to_mut()[at].profiles[which].roles.parent = Some(parent);
            }
        }
        for (which, household) in record.households.iter().enumerate() {
            if let Some(name) = household.admin.as_ref().and_then(|admin| respelled(&admin.name))
                && let Some(admin) = rewritten.to_mut()[at].households[which].admin.as_mut()
            {
                admin.name = name;
            }
        }
    }
    (rewritten, people)
}

#[cfg(test)]
#[path = "renames_tests.rs"]
mod tests;
