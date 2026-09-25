use super::*;

#[tokio::test]
async fn account_refusals_only_revoke_the_originating_login() {
    use session::fixture::{Fixture, rpc};
    for code in [401, 500] {
        let fixture = Fixture::new().await;
        let error = account_with(&fixture.core, |_| async { Err(rpc(code)) })
            .await
            .unwrap_err();
        if code == 401 {
            assert!(matches!(error, CoreError::NotAuthorized(_)));
            fixture.assert_revoked().await;
        } else {
            assert!(matches!(error, CoreError::Network(_)));
            fixture.assert_kept(&fixture.owner, 7).await;
        }
    }
    let fixture = Fixture::new().await;
    let replacement = std::cell::RefCell::new(None);
    let error = account_with(&fixture.core, |_| async {
        *replacement.borrow_mut() = Some(fixture.replace().await);
        Err(rpc(401))
    })
    .await
    .unwrap_err();
    assert!(matches!(error, CoreError::Network(_)));
    fixture
        .assert_kept(&replacement.into_inner().unwrap(), 9)
        .await;
}

fn core(dir: &std::path::Path) -> std::sync::Arc<Core> {
    Core::new(
        dir.display().to_string(),
        1,
        "test-hash".into(),
        "test-device".into(),
    )
}

#[test]
fn no_login_has_no_datacentre() {
    let dir = tempfile::tempdir().unwrap();
    assert_eq!(core(dir.path()).dc_id(), None);
}

/// Signing out twice, or before ever signing in, is not an error and
/// never opens a connection: there is no login to end.
#[tokio::test]
async fn signing_out_with_no_login_is_a_quiet_no_op() {
    let dir = tempfile::tempdir().unwrap();
    let core = core(dir.path());
    core.sign_out().await.unwrap();
    core.sign_out().await.unwrap();
    assert!(core.state.lock().await.client.is_none());
    assert!(!core.is_authorized());
}

async fn assert_logout_blocks_key_reload(refused: bool) {
    use session::fixture::{Fixture, rpc};
    use std::future::Future;
    use std::task::{Context, Waker};

    let fixture = Fixture::new().await;
    let mut reconnect = Box::pin(session::connection(&fixture.core));
    sign_out_with(
        &fixture.core,
        |_| async move { if refused { Err(rpc(500)) } else { Ok(()) } },
        |path| {
            // Poll a real lazy connection precisely between resetting live
            // state and removing the outgoing key. No scheduling race or RPC.
            assert!(session::load_auth_key(path).is_some());
            assert!(
                reconnect
                    .as_mut()
                    .poll(&mut Context::from_waker(Waker::noop()))
                    .is_pending(),
                "reconnection must wait until the outgoing key has been removed"
            );
            session::forget_auth_key(path)
        },
    )
    .await
    .unwrap();

    let (_, replacement) = reconnect.await;
    assert!(!fixture.core.is_authorized());
    assert!(session::is_current(&fixture.core, &replacement).await);
    assert!(!session::is_current(&fixture.core, &fixture.owner).await);
    assert!(
        replacement
            .session
            .dc_option(2)
            .unwrap()
            .unwrap()
            .auth_key
            .is_none()
    );
}

#[tokio::test]
async fn signing_out_blocks_reconnection_until_the_key_is_removed() {
    assert_logout_blocks_key_reload(false).await;
}

#[tokio::test]
async fn refused_remote_logout_still_blocks_reloading_the_outgoing_key() {
    assert_logout_blocks_key_reload(true).await;
}

#[tokio::test]
async fn failure_to_remove_the_key_reports_io_after_resetting_the_connection() {
    use session::fixture::Fixture;

    let fixture = Fixture::new().await;
    let key_path = fixture.core.data_dir.join("session.key");
    let error = sign_out_with(
        &fixture.core,
        |_| async {
            // A directory collision refuses unlink on every platform, without
            // depending on the executing user's permission level.
            std::fs::remove_file(&key_path).unwrap();
            std::fs::create_dir(&key_path).unwrap();
            Ok(())
        },
        session::forget_auth_key,
    )
    .await
    .unwrap_err();
    assert!(matches!(error, CoreError::Io(message) if message == "removing the stored login"));
    fixture.assert_revoked().await;
    assert!(key_path.is_dir());
}
