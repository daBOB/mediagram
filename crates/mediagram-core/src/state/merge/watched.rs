//! Deciding, per title, whether a live `WatchedRow` or its `UnwatchedRow`
//! removal is the more recent fact, once `keep` has already picked the
//! winner within each kind on its own. A port of
//! `web/src/state/watched-reconcile.ts`; see it, and `record.rs`'s
//! `UnwatchedRow`, for why a tie goes to the removal rather than a
//! device-id tie-break.

use std::collections::HashMap;

use crate::state::record::{UnwatchedRow, WatchedRow};

pub(super) struct Reconciled {
    pub(super) watched: Vec<WatchedRow>,
    pub(super) unwatched: Vec<UnwatchedRow>,
    /// Per title, the moment a `ProgressRow` no newer than it is
    /// superseded — a live row's own time, or a removal's
    /// `last_finished_at`.
    pub(super) finished_at: HashMap<String, f64>,
}

pub(super) fn reconcile(
    watched: HashMap<String, WatchedRow>,
    unwatched: HashMap<String, UnwatchedRow>,
) -> Reconciled {
    let mut out_watched = Vec::new();
    let mut out_unwatched = Vec::new();
    let mut finished_at = HashMap::new();

    let mut titles: Vec<&String> = watched.keys().chain(unwatched.keys()).collect();
    titles.sort();
    titles.dedup();

    for set_id in titles {
        let live = watched.get(set_id);
        let removed = unwatched.get(set_id);
        let removal_wins = removed.is_some_and(|r| live.is_none_or(|l| r.updated_at >= l.updated_at));
        if removal_wins {
            let removed = removed.unwrap();
            finished_at.insert(set_id.clone(), removed.last_finished_at);
            out_unwatched.push(removed.clone());
        } else if let Some(live) = live {
            finished_at.insert(set_id.clone(), live.updated_at);
            out_watched.push(live.clone());
        }
    }
    Reconciled {
        watched: out_watched,
        unwatched: out_unwatched,
        finished_at,
    }
}
