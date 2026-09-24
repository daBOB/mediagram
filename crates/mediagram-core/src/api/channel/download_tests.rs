use super::super::responses::Scripted;
use super::*;
use crate::api::account::session::fixture::{Fixture, rpc};

#[tokio::test]
async fn index_download_refusals_only_revoke_the_originating_login() {
    for (code, replaced) in [(401, false), (500, false), (401, true)] {
        let fixture = Fixture::new().await;
        let path = fixture.core.data_dir.join("partial.db");
        let replacement = if replaced {
            Some(fixture.replace().await)
        } else {
            None
        };
        let error = download_with(
            &fixture.core,
            &fixture.owner,
            &path,
            Scripted(vec![Ok(vec![1, 2, 3]), Err(rpc(code))].into_iter()),
        )
        .await
        .unwrap_err();
        assert_eq!(std::fs::read(path).unwrap(), [1, 2, 3]);
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
