//! `Core::subtitle_text`, `Core::hold_subtitles`, `Core::hold_course_subtitles`:
//! reading one subtitle track's content, and warming the on-device bundle
//! cache ahead of when a track is actually picked.
//!
//! A set's tracks come from one of two places — [`crate::catalog_subtitles`]
//! reads both — but only a bundled set ever needs the network: a legacy
//! inline row is already sitting in the index, the same as a summary is.

use std::sync::Arc;

use rusqlite::Connection;

use crate::api::{Core, CoreError, store};
use crate::catalog_subtitles::{self, BundleRef};

#[path = "subtitles_cache.rs"]
pub(in crate::api) mod cache;

/// How many lessons past the one just opened a course hold fetches ahead of
/// — the opened lesson plus these, never the whole course (a long one runs
/// to hundreds of lessons).
const COURSE_HOLD_NEXT: usize = 10;

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// One subtitle track's WebVTT text, by its position among
    /// `SetSummary.subtitles`. `None` for a set or track this build cannot
    /// read, or a bundle that could not be fetched — every cause is logged,
    /// never surfaced as a failure a player has to handle specially.
    pub async fn subtitle_text(self: Arc<Self>, set_id: String, track: u32) -> Option<String> {
        let lookup_id = set_id.clone();
        match self.blocking(move |core| resolve(core, &lookup_id, track)).await? {
            Resolved::Legacy(text) => Some(text),
            Resolved::Bundle(bundle) => {
                let decoded = cache::fetch(&self, &set_id, &bundle).await?;
                decoded.tracks.into_iter().nth(track as usize).map(|t| t.vtt)
            }
        }
    }

    /// Fetches and caches a set's bundle, if it has one — what a preload
    /// write calls so a title's subtitles are already local by the time
    /// someone opens it. `false` for a set with no bundle, or one that could
    /// not be fetched; never fails the preload that asked for it.
    pub async fn hold_subtitles(self: Arc<Self>, set_id: String) -> bool {
        let lookup_id = set_id.clone();
        let Some(bundle) = self.blocking(move |core| bundle_for(core, &lookup_id)).await else {
            return false;
        };
        cache::fetch(&self, &set_id, &bundle).await.is_some()
    }

    /// Fetches and caches the opened lesson's own bundle plus up to
    /// [`COURSE_HOLD_NEXT`] that follow it in its course, sequentially, so a
    /// long course never fetches more than the next few lessons at once.
    pub async fn hold_course_subtitles(self: Arc<Self>, set_id: String) {
        let lookup_id = set_id.clone();
        let plan = self.blocking(move |core| course_hold_plan(core, &lookup_id)).await;
        for (id, bundle) in plan {
            cache::fetch(&self, &id, &bundle).await;
        }
    }
}

enum Resolved {
    Legacy(String),
    Bundle(BundleRef),
}

fn open(core: &Core) -> Option<Connection> {
    match store::open(core) {
        Ok(conn) => Some(conn),
        Err(CoreError::NotFound(_)) => None,
        Err(err) => {
            tracing::warn!(error = %err, "the index could not be opened for a subtitle track");
            None
        }
    }
}

fn bundle_for(core: &Core, set_id: &str) -> Option<BundleRef> {
    let conn = open(core)?;
    bundle_of(&conn, set_id)
}

fn bundle_of(conn: &Connection, set_id: &str) -> Option<BundleRef> {
    catalog_subtitles::bundle_ref(conn, set_id).unwrap_or_else(|err| {
        tracing::warn!(error = %err, "a set's subtitle bundle reference could not be read");
        None
    })
}

fn resolve(core: &Core, set_id: &str, track: u32) -> Option<Resolved> {
    let conn = open(core)?;
    if let Some(bundle) = bundle_of(&conn, set_id) {
        return Some(Resolved::Bundle(bundle));
    }
    let tracks = catalog_subtitles::tracks_by_set(&conn).unwrap_or_else(|err| {
        tracing::warn!(error = %err, "a set's subtitle tracks could not be read");
        Default::default()
    });
    let lang = tracks.get(set_id)?.get(track as usize)?.lang.clone();
    catalog_subtitles::legacy_body(&conn, set_id, &lang)
        .unwrap_or_else(|err| {
            tracing::warn!(error = %err, "a set's subtitle text could not be read");
            None
        })
        .map(Resolved::Legacy)
}

fn course_hold_plan(core: &Core, set_id: &str) -> Vec<(String, BundleRef)> {
    let Some(conn) = open(core) else {
        return Vec::new();
    };
    let ids = catalog_subtitles::course_run(&conn, set_id, COURSE_HOLD_NEXT).unwrap_or_else(|err| {
        tracing::warn!(error = %err, "a course's lesson order could not be read");
        vec![set_id.to_string()]
    });
    ids.into_iter()
        .filter_map(|id| bundle_of(&conn, &id).map(|bundle| (id, bundle)))
        .collect()
}

#[cfg(test)]
#[path = "subtitles_tests.rs"]
mod tests;
