use super::*;
use responses::Scripted;
use session::fixture::{Fixture, rpc};

#[tokio::test]
async fn library_listing_refusals_only_revoke_the_originating_login() {
    for (code, replaced) in [(401, false), (500, false), (401, true)] {
        let fixture = Fixture::new().await;
        let replacement = if replaced {
            Some(fixture.replace().await)
        } else {
            None
        };
        let result = list_libraries_with(
            &fixture.core,
            &fixture.owner,
            Scripted(vec![Err(rpc(code))].into_iter()),
        )
        .await;
        let error = result.err().unwrap();
        if let Some(replacement) = replacement {
            assert!(matches!(error, CoreError::Network(_)));
            fixture.assert_kept(&replacement, 9).await;
        } else if code == 401 {
            assert!(matches!(error, CoreError::NotAuthorized(_)));
            fixture.assert_revoked().await;
        } else {
            assert!(matches!(error, CoreError::Network(_)));
            fixture.assert_kept(&fixture.owner, 7).await;
        }
    }
}

/// A stored map that will not parse once stopped every channel request,
/// this one included, so nothing short of signing out could replace it.
/// Listing is where it is put right: the list comes from Telegram, and what
/// is written back parses again.
#[tokio::test]
async fn listing_the_libraries_replaces_a_stored_map_that_will_not_parse() {
    let fixture = Fixture::new().await;
    let file = library::path(&fixture.core);
    std::fs::write(&file, [0u8; 64]).unwrap();

    let listed = list_libraries_with(
        &fixture.core,
        &fixture.owner,
        Scripted(Vec::<Result<Dialog, _>>::new().into_iter()),
    )
    .await
    .unwrap();

    assert!(listed.is_empty());
    assert!(library::read(&file).unwrap().is_empty());
}
