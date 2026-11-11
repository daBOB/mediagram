//! The media app's images, fetched through Kotlin. The titles' posters and
//! stills sit in the media app's storage, which this app cannot open, and a
//! textless poster needs its TMDB key; Kotlin copies each one out of the
//! media app into this app's cache and answers the copy's path.

use std::collections::HashSet;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};

use mediagram_game::{ImageKind, ImageLookup, PosterLookup};

/// Which of a title's images to fetch.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum MediaImageKind {
    Poster,
    Still,
    /// The poster without its lettering, which Poster-Raten shows when there is one.
    Textless,
}

/// Implemented in Kotlin over the media app's provider.
#[uniffi::export(with_foreign)]
pub trait MediaImages: Send + Sync {
    /// A readable copy of the image of the title `key` names, or `None` when
    /// the media app has none or does not hand it out.
    fn fetch(&self, key: String, kind: MediaImageKind) -> Option<String>;
}

/// The host's hook for posters and stills it cannot read where the titles say they are.
pub(crate) fn image_lookup(images: Arc<dyn MediaImages>) -> ImageLookup {
    Arc::new(move |key, kind| {
        let kind = match kind {
            ImageKind::Poster => MediaImageKind::Poster,
            ImageKind::Still => MediaImageKind::Still,
        };
        Box::pin(fetch(images.clone(), key, kind))
    })
}

/// The host's textless lookup, through the same fetch. Made per night: a title found to have no
/// textless poster is not asked about again that night, since every poster request asks first.
pub(crate) fn textless_lookup(images: Arc<dyn MediaImages>) -> PosterLookup {
    let misses: Arc<Mutex<HashSet<String>>> = Arc::default();
    Arc::new(move |key| {
        let (images, misses) = (images.clone(), misses.clone());
        Box::pin(async move {
            if misses.lock().unwrap_or_else(|e| e.into_inner()).contains(&key) {
                return None;
            }
            // Spawned, so a miss that answers after the image route stopped waiting is still remembered.
            let asked = tokio::spawn(async move {
                let found = fetch(images, key.clone(), MediaImageKind::Textless).await;
                if found.is_none() {
                    misses.lock().unwrap_or_else(|e| e.into_inner()).insert(key);
                }
                found
            });
            asked.await.ok().flatten()
        })
    })
}

/// Off the async runtime: Kotlin blocks on the provider's file while it copies it.
async fn fetch(images: Arc<dyn MediaImages>, key: String, kind: MediaImageKind) -> Option<PathBuf> {
    tokio::task::spawn_blocking(move || images.fetch(key, kind)).await.ok().flatten().map(PathBuf::from)
}
