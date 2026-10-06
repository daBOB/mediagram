//! The dry run's running totals: matched and fallback files summed per
//! kind/container of the set they name, everything else counted plainly.

use std::collections::BTreeMap;

use crate::index::set_row::SetRow;

#[derive(Default, Clone, Copy)]
pub(super) struct Totals {
    pub count: u64,
    pub bytes: u64,
}

#[derive(Default, Clone, Copy)]
pub(super) struct MatchTotals {
    pub count: u64,
    pub bytes: u64,
    pub with_de_en_text: u64,
}

/// Adds one matched file of `bytes` to its set's kind/container; `counts`
/// is its (de/en text, picture-only) subtitle tracks, when it was probed. A
/// file naming a set not among the candidates is in no kind, so in no total.
pub(super) fn add_match(
    totals: &mut BTreeMap<(String, String), MatchTotals>,
    set: Option<&SetRow>,
    bytes: u64,
    counts: Option<(u32, u32)>,
) {
    let Some(set) = set else { return };
    let entry = totals
        .entry((set.kind.as_str().to_string(), set.container.clone()))
        .or_default();
    entry.count += 1;
    entry.bytes += bytes;
    if counts.is_some_and(|(de_en_text, _)| de_en_text > 0) {
        entry.with_de_en_text += 1;
    }
}

/// Binary gigabytes, as the totals are printed.
pub(super) fn gb(bytes: u64) -> f64 {
    bytes as f64 / 1_073_741_824.0
}

#[cfg(test)]
#[path = "dry_run_totals_tests.rs"]
mod tests;
