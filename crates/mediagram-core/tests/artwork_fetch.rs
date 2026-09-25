//! One fetch run over a seeded library, reaching neither TMDB nor the poster
//! CDN: the harness these drive lives in [`fetch_stub`].

mod fetch_stub;

use fetch_stub::{
    RejectingApi, StubApi, catalog_with_collection, catalog_with_kinds, core_at, fetch_rejecting,
    fetch_with, install_crypto_provider, offline_client, write_existing_poster,
};

use mediagram_core::api::CoreError;
use mediagram_core::api::enrich::artwork::{plan_fetch, verify_then_fetch};

/// A key the provider rejects stays rejected however warm the disk cache
/// is.
///
/// The cache answers a repeated resolve, which is what it is for. It must
/// never answer for the credential: the cache keys on the endpoint and the
/// query, and the key appears in neither, so a rotated or mistyped key
/// asked through it would be validated against a file the *previous* key
/// paid for. The run that followed would then serve every title out of that
/// same cache and report a library entirely "already held" — a screen full
/// of zeroes a viewer would retry forever, which is the one outcome the
/// check exists to prevent.
#[tokio::test]
async fn a_rejected_key_is_still_rejected_after_a_successful_run() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(11225))]);
    let core = core_at(dir.path());
    let plan = plan_fetch(&core, "en-US").unwrap();
    let http = offline_client();
    install_crypto_provider();

    // A first run with a key the provider accepts, which is what leaves a
    // warm cache behind.
    let good = StubApi::with_poster("/a.jpg");
    verify_then_fetch(&core, good, &http, &plan, 780)
        .await
        .expect("a key the provider accepts fetches");

    // The same device afterwards, with a key the provider will not take.
    let err = verify_then_fetch(&core, RejectingApi, &http, &plan, 780)
        .await
        .expect_err("a rejected key must not be verified out of the cache");

    assert!(
        matches!(err, CoreError::NotAuthorized(_)),
        "reported as {err:?}"
    );
}

/// A rejected key is reported as a key problem before anything is spent,
/// and never by quoting the key back.
///
/// The whole plan is driven rather than a hand-built title list, so this is
/// the run a viewer actually starts. Nothing is asserted about the store a
/// refused run leaves: a provider refusing the credential refuses every
/// title too, so no description would be recorded whether the check came
/// first or not, and an assertion that cannot fail reads as coverage
/// without being any.
#[tokio::test]
async fn a_rejected_key_stops_the_run_before_it_starts() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(550))]);

    let err = fetch_rejecting(dir.path()).await.unwrap_err();

    assert!(
        matches!(err, CoreError::NotAuthorized(_)),
        "reported as {err:?}"
    );
}

/// One pass answers both questions about a title. They come from one cached
/// payload, and a viewer who asked for the missing pieces did not ask for
/// half of them.
///
/// The poster is already on disk because the CDN is deliberately
/// unreachable here: a download that cannot succeed says nothing about
/// whether the artwork half ran, where "already held" says it did.
#[tokio::test]
async fn one_run_records_a_description_and_settles_the_poster() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(550))]);
    write_existing_poster(dir.path(), "tmdb-movie-550");

    let report = fetch_with(dir.path(), StubApi::with_poster("/a.jpg")).await;

    assert_eq!(report.details_recorded, 1);
    assert_eq!(report.posters_already_held, 1);
    assert_eq!(report.failed, 0);
}

/// A title already described is not asked about again, the same way a
/// poster already held is not downloaded again.
#[tokio::test]
async fn a_title_already_described_is_left_alone() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(550))]);
    write_existing_poster(dir.path(), "tmdb-movie-550");

    fetch_with(dir.path(), StubApi::with_poster("/a.jpg")).await;
    let second = fetch_with(dir.path(), StubApi::with_poster("/a.jpg")).await;

    assert_eq!(second.details_recorded, 0);
    assert_eq!(second.details_already_known, 1);
    assert_eq!(second.posters_fetched, 0);
    assert_eq!(second.posters_already_held, 1);
}

/// A provider that will not answer about one title costs that title its
/// description and nothing else.
#[tokio::test]
async fn one_unanswerable_title_does_not_end_the_run() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(
        dir.path(),
        &[("movie", Some(550)), ("movie", Some(999_999_999))],
    );

    let report = fetch_with(dir.path(), StubApi::answering_only(550)).await;

    assert_eq!(report.details_recorded, 1);
    assert_eq!(report.failed, 1);
}

#[tokio::test]
async fn a_title_with_no_provider_id_is_counted_rather_than_failed() {
    // A course has no TMDB entry at all, and a library of them must not
    // report a failure for every one.
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("tut", None), ("movie", Some(11225))]);

    let report = fetch_with(dir.path(), StubApi::with_poster("/a.jpg")).await;

    assert_eq!(report.no_provider_id, 1);
}

/// `no_provider_id` counts what a shelf shows, not what the index holds:
/// a course is one card however many lessons it has, and two films the
/// provider numbers neither of are two, because only a collection collapses.
/// Counted per row, a 162-lesson course read as "162 titles have no provider
/// entry" beside "3 posters fetched".
#[tokio::test]
async fn titles_with_no_provider_entry_are_counted_as_a_shelf_shows_them() {
    let named = tempfile::tempdir().unwrap();
    catalog_with_collection(named.path(), "tut", Some("Rust in Anger"), 12);

    assert_eq!(
        fetch_with(named.path(), StubApi::default())
            .await
            .no_provider_id,
        1
    );

    // The same twelve with no course name. `Shelves.kt` draws those as one
    // card too, under a stand-in title, so a count beside that shelf saying
    // twelve would be a count of something the viewer cannot see.
    let bare = tempfile::tempdir().unwrap();
    catalog_with_collection(bare.path(), "tut", None, 12);

    assert_eq!(
        fetch_with(bare.path(), StubApi::default())
            .await
            .no_provider_id,
        1
    );

    // Films are not collected, so nothing collapses them into each other.
    let films = tempfile::tempdir().unwrap();
    catalog_with_kinds(films.path(), &[("movie", None), ("movie", None)]);

    assert_eq!(
        fetch_with(films.path(), StubApi::default())
            .await
            .no_provider_id,
        2
    );
}

#[tokio::test]
async fn artwork_already_on_disk_is_not_fetched_again() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(11225))]);
    write_existing_poster(dir.path(), "tmdb-movie-11225");

    let report = fetch_with(dir.path(), StubApi::with_poster("/a.jpg")).await;

    assert_eq!(report.posters_already_held, 1);
    assert_eq!(report.posters_fetched, 0);
}

/// Every episode of a series shares one poster key and one description, so
/// a season of eight is one title rather than eight.
#[tokio::test]
async fn a_series_is_one_title_however_many_episodes_it_has() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(
        dir.path(),
        &[("ep", Some(1399)), ("ep", Some(1399)), ("ep", Some(1399))],
    );

    let report = fetch_with(dir.path(), StubApi::with_poster("/a.jpg")).await;

    assert_eq!(report.posters_fetched + report.failed, 1);
    assert_eq!(report.details_recorded, 1);
    // Not "two more already described": the walk is over titles, so the
    // other two rows were never a second and third question at all.
    assert_eq!(report.details_already_known, 0);
}

/// A provider that answers without a path is not an error; that title simply
/// has no artwork. Its description is recorded all the same — the two halves
/// of a payload are missing independently.
#[tokio::test]
async fn a_title_the_provider_has_no_art_for_is_not_a_failure() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(11225))]);

    let report = fetch_with(dir.path(), StubApi::default()).await;

    assert_eq!(report.failed, 0);
    assert_eq!(report.posters_fetched, 0);
    assert_eq!(report.details_recorded, 1);
}

/// The third walk `fetch_into` makes: a resolved backdrop is asked for
/// alongside the poster, from the same payload the poster half already
/// paid for — `fetch_cache.rs` counts the request and proves it costs
/// nothing extra. The CDN is unreachable in this harness, so neither image
/// downloads; a poster and its title's backdrop are different keys, so a
/// title that loses both counts twice, the same as one that lost its poster
/// and its description.
#[tokio::test]
async fn a_resolved_backdrop_is_counted_like_a_poster() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(550))]);

    let report = fetch_with(dir.path(), StubApi::with_poster_and_backdrop("/p.jpg", "/b.jpg")).await;

    assert_eq!(report.posters_fetched + report.backdrops_fetched, 0);
    assert_eq!(report.failed, 2, "the poster key and the backdrop key both went undownloaded");
}

/// A title with no backdrop recorded costs nothing on that half of the
/// report — the same way a title with no poster costs nothing on its half.
#[tokio::test]
async fn a_title_with_no_backdrop_counts_nothing_there() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(550))]);

    let report = fetch_with(dir.path(), StubApi::with_poster("/p.jpg")).await;

    assert_eq!(report.backdrops_fetched, 0);
    assert_eq!(report.backdrops_already_held, 0);
}

/// A backdrop already on disk is left alone, exactly like a poster already
/// held.
#[tokio::test]
async fn a_backdrop_already_on_disk_is_kept_and_not_requested_again() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(550))]);
    write_existing_poster(dir.path(), "tmdb-movie-550-bg");

    let report = fetch_with(dir.path(), StubApi::with_poster_and_backdrop("/p.jpg", "/b.jpg")).await;

    assert_eq!(report.backdrops_already_held, 1);
    assert_eq!(report.backdrops_fetched, 0);
}
