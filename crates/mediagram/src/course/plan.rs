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
        (None, None) => natural_cmp(&a.2, &b.2),
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

/// Name order with the numbers inside a name compared as numbers, so the
/// parts of a documentary run `Teil 1, Teil 2, … Teil 10` rather than putting
/// `Teil 10` second.
fn natural_cmp(a: &str, b: &str) -> std::cmp::Ordering {
    let (mut a, mut b) = (a, b);
    loop {
        let (Some(x), Some(y)) = (a.chars().next(), b.chars().next()) else {
            return a.len().cmp(&b.len());
        };
        if x.is_ascii_digit() && y.is_ascii_digit() {
            let da = a.find(|c: char| !c.is_ascii_digit()).unwrap_or(a.len());
            let db = b.find(|c: char| !c.is_ascii_digit()).unwrap_or(b.len());
            let (na, nb) = (a[..da].trim_start_matches('0'), b[..db].trim_start_matches('0'));
            let order = na.len().cmp(&nb.len()).then_with(|| na.cmp(nb));
            if order.is_ne() {
                return order;
            }
            (a, b) = (&a[da..], &b[db..]);
        } else {
            if x != y {
                return x.cmp(&y);
            }
            (a, b) = (&a[x.len_utf8()..], &b[y.len_utf8()..]);
        }
    }
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

#[cfg(test)]
mod natural_order_tests {
    use super::assign_numbers;

    #[test]
    fn unnumbered_parts_run_in_numeric_order() {
        let names = ["Die Römer - Teil 10", "Die Römer - Teil 2", "Die Römer - Teil 1"];
        let entries: Vec<(String, &str)> = names.iter().map(|n| (n.to_string(), *n)).collect();
        let order: Vec<&str> = assign_numbers(&entries).into_iter().map(|(_, _, n)| n).collect();
        assert_eq!(order, ["Die Römer - Teil 1", "Die Römer - Teil 2", "Die Römer - Teil 10"]);
    }
}
