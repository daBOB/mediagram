use super::super::responses::Scripted;
use super::*;
use crate::api::account::session::fixture::{Fixture, rpc};

#[tokio::test]
async fn pinned_search_refusals_only_revoke_the_originating_login() {
    search_refusals(false).await;
}

#[tokio::test]
async fn marked_search_refusals_only_revoke_the_originating_login() {
    search_refusals(true).await;
}

async fn search_refusals(marked: bool) {
    for (code, replaced) in [(401, false), (500, false), (401, true)] {
        let fixture = Fixture::new().await;
        let replacement = if replaced {
            Some(fixture.replace().await)
        } else {
            None
        };
        let result = newest_index_with(
            &fixture.core,
            &fixture.owner,
            Scripted(if marked { vec![] } else { vec![Err(rpc(code))] }.into_iter()),
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
            assert!(matches!(
                error,
                CoreError::Network(_) | CoreError::Library(_)
            ));
            fixture.assert_kept(&fixture.owner, 7).await;
        }
    }
}
