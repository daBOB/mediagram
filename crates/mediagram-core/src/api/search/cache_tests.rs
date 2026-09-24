use std::sync::Arc;

use super::*;

fn set(id: &str) -> SearchableSet {
    SearchableSet { set_id: id.into(), title: None, show: None, chap: None, path: None, summary: None }
}

#[test]
fn the_same_version_reuses_the_corpus_rather_than_refolding_it() {
    let mut cache = SearchCache::default();
    let version = Some(PathBuf::from("/catalog/v-1"));

    let first = cache.get_or_build(version.clone(), &[set("a")]);
    let second = cache.get_or_build(version, &[set("a")]);

    assert!(Arc::ptr_eq(&first, &second), "a repeat search over the same version refolded the corpus");
}

#[test]
fn a_changed_version_rebuilds_the_corpus() {
    let mut cache = SearchCache::default();

    let first = cache.get_or_build(Some(PathBuf::from("/catalog/v-1")), &[set("a")]);
    let second = cache.get_or_build(Some(PathBuf::from("/catalog/v-2")), &[set("b")]);

    assert!(!Arc::ptr_eq(&first, &second), "a refresh's new version kept the old catalog's corpus");
}

#[test]
fn no_catalog_either_time_still_rebuilds_once_not_on_every_call() {
    // `None` stands for "nothing installed", which is one version as far as
    // this cache is concerned, however many times a caller asks about it.
    let mut cache = SearchCache::default();

    let first = cache.get_or_build(None, &[]);
    let second = cache.get_or_build(None, &[]);

    assert!(Arc::ptr_eq(&first, &second));
}
