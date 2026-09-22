//! Numbering a course's chapters and lessons from the names on disk.
//!
//! Pure: no filesystem access, so the inference rules can be tested directly
//! and a dry-run shows exactly what an upload would record.

use crate::media::file_names::split_number_and_title;

/// Assigns numbers to a list of entries. Each is a name to read the number
/// and title from, paired with whatever the caller needs back (a file name, a
/// directory name).
///
/// Explicit numbers are kept; the rest follow in name order, continuing from
/// the highest explicit number, so they can never collide with one someone
/// chose. Two walks of the same tree therefore agree.
pub fn assign_numbers<T: Clone>(entries: &[(String, T)]) -> Vec<(u32, Option<String>, T)> {
    let mut parsed: Vec<(Option<u32>, Option<String>, String, T)> = entries
        .iter()
        .map(|(name, payload)| {
            let (number, title) = split_number_and_title(name);
            (number, title, name.clone(), payload.clone())
        })
        .collect();
    parsed.sort_by(|a, b| match (a.0, b.0) {
        (Some(x), Some(y)) => x.cmp(&y).then_with(|| a.2.cmp(&b.2)),
        (Some(_), None) => std::cmp::Ordering::Less,
        (None, Some(_)) => std::cmp::Ordering::Greater,
        (None, None) => a.2.cmp(&b.2),
    });

    let mut next = parsed.iter().filter_map(|p| p.0).max().unwrap_or(0);
    parsed
        .into_iter()
        .map(|(number, title, _, payload)| {
            let number = number.unwrap_or_else(|| {
                next += 1;
                next
            });
            (number, title, payload)
        })
        .collect()
}

/// [`assign_numbers`], with the numbers guaranteed distinct.
///
/// Declared numbers are honoured while they are unique. They stop being
/// unique as soon as a course repeats them across folders, which real ones do
/// constantly: several sections each numbering their own chapters from 1, or
/// several chapters each numbering their own lessons from 1. When that
/// happens the whole group is renumbered in order, because a mix of honoured
/// and invented numbers is harder to predict than a clean sequence, and the
/// dry-run table shows the result either way.
///
/// This matters beyond tidiness: the number is half of a lesson's identity,
/// and two lessons sharing an identity would make the second unreachable,
/// skipped forever as already uploaded.
pub fn assign_unique_numbers<T: Clone>(entries: &[(String, T)]) -> Vec<(u32, Option<String>, T)> {
    let assigned = assign_numbers(entries);
    let mut seen = std::collections::BTreeSet::new();
    if assigned.iter().all(|(n, _, _)| seen.insert(*n)) {
        return assigned;
    }
    assigned
        .into_iter()
        .enumerate()
        .map(|(i, (_, title, payload))| (i as u32 + 1, title, payload))
        .collect()
}
