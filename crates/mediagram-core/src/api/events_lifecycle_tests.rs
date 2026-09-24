use super::*;
use crate::api::account::{revoked, subscribe};

fn authorized_core() -> (tempfile::TempDir, std::sync::Arc<Core>) {
    let dir = tempfile::tempdir().unwrap();
    let mut key = vec![7; 260];
    key[..4].copy_from_slice(&2_i32.to_be_bytes());
    std::fs::write(dir.path().join("session.key"), key).unwrap();
    let core = Core::at(dir.path());
    (dir, core)
}

#[tokio::test]
async fn cleanup_of_an_old_pool_preserves_a_replacement_listener() {
    let (_dir, core) = authorized_core();
    let original = installed_listener(&core).await;
    let replacement = replace_login(&core).await;
    installed_listener(&core).await;

    // This is the post-state-reset cleanup, after another task installed B.
    revoked::clear_idle_listener(&core, &original);

    let listener = core.events.lock().await;
    assert!(std::sync::Arc::ptr_eq(
        &listener.as_ref().unwrap().handle.session,
        &replacement.session
    ));
    drop(listener);
    assert_replacement_kept(&core, &replacement).await;
    revoked::clear_idle_listener(&core, &replacement);
    assert!(core.events.lock().await.is_none());
}

fn rpc(code: i32) -> InvocationError {
    InvocationError::Rpc(grammers_mtsender::RpcError::from(tl::types::RpcError {
        error_code: code,
        error_message: if code == 401 {
            "SESSION_REVOKED"
        } else {
            "FLOOD_WAIT_30"
        }
        .into(),
    }))
}

fn update_state() -> tl::enums::updates::State {
    tl::types::updates::State {
        pts: 1,
        qts: 2,
        date: 3,
        seq: 4,
        unread_count: 0,
    }
    .into()
}

async fn subscribed_listener(core: &Core) -> Result<Listener, CoreError> {
    open::with_subscription(
        core,
        subscribe::subscribe_with(core, |_| async { Ok(update_state()) }),
    )
    .await
}

#[tokio::test]
async fn subscription_revocation_forgets_authorization_and_ordinary_failure_keeps_it() {
    for code in [401, 420] {
        let (_dir, core) = authorized_core();
        let error = subscribe::subscribe_with(&core, |_| async { Err(rpc(code)) })
            .await
            .err()
            .unwrap();
        if code == 401 {
            assert!(matches!(error, CoreError::NotAuthorized(_)));
            assert!(!core.is_authorized());
            assert!(core.state.lock().await.client.is_none());
        } else {
            assert!(matches!(error, CoreError::Network(ref message)
                if message == "could not start listening for library changes"));
            assert!(core.is_authorized());
            let (_, handle) = session::connection(&core).await;
            assert!(session::updates_receiver(&core, &handle).await.is_some());
        }
    }
}

#[tokio::test]
async fn dropped_listening_can_run_the_entire_subscription_and_open_sequence_again() {
    let (_dir, core) = authorized_core();
    let library = registered_library(&core);
    let failed = installed_listener(&core).await;
    failed.quit();
    assert!(next(&core, &library, "own").await.is_err());

    let listener = subscribed_listener(&core).await.unwrap();

    assert!(!std::sync::Arc::ptr_eq(
        &failed.session,
        &listener.handle.session
    ));
    assert!(session::is_current(&core, &listener.handle).await);
    assert!(
        session::updates_receiver(&core, &listener.handle)
            .await
            .is_none()
    );
    assert!(
        core.is_authorized(),
        "a transport failure does not revoke the key"
    );
}

#[tokio::test]
async fn stream_initialization_errors_release_the_consumed_receiver_and_keep_error_kind() {
    for code in [401, 420] {
        let (_dir, core) = authorized_core();
        let (_, handle) = session::connection(&core).await;
        assert!(session::updates_receiver(&core, &handle).await.is_some());

        let error = open::finish(&core, handle, Err(Box::new(rpc(code))))
            .await
            .err()
            .unwrap();

        assert!(core.state.lock().await.client.is_none());
        assert_eq!(core.is_authorized(), code != 401);
        if code == 401 {
            assert!(matches!(error, CoreError::NotAuthorized(_)));
        } else {
            assert!(matches!(error, CoreError::Network(ref message)
                if message == "could not start listening for library changes"));
            assert!(subscribed_listener(&core).await.is_ok());
        }
    }
}

#[tokio::test]
async fn waiting_errors_share_revocation_handling_without_relocking_the_listener() {
    for code in [401, 420] {
        let (_dir, core) = authorized_core();
        let handle = installed_listener(&core).await;
        let mut slot = core.events.lock().await;

        let error = tokio::time::timeout(
            Duration::from_secs(1),
            listener::finish_wait(&core, &mut slot, Err(rpc(code))),
        )
        .await
        .unwrap()
        .unwrap_err();

        assert_eq!(slot.is_some(), code != 401);
        assert_eq!(core.is_authorized(), code != 401);
        assert_eq!(session::is_current(&core, &handle).await, code != 401);
        if code == 401 {
            assert!(matches!(error, CoreError::NotAuthorized(_)));
        } else {
            assert!(matches!(error, CoreError::Network(ref message)
                if message == "stopped listening for library changes"));
        }
    }
}

#[tokio::test]
async fn concurrent_revocation_wakes_an_active_listener_and_releases_its_lock() {
    let (_dir, core) = authorized_core();
    let library = registered_library(&core);
    let owner = installed_listener(&core).await;

    let (waiting, revoked) = tokio::time::timeout(Duration::from_secs(1), async {
        tokio::join!(biased; next(&core, &library, "own"), async {
            assert!(core.events.try_lock().is_err(), "next is actively waiting");
            revoked::unless_revoked_for(&core, &owner, &rpc(401), CoreError::Network("context".into())).await
        })
    })
    .await
    .unwrap();

    assert!(waiting.is_err());
    assert!(matches!(revoked, CoreError::NotAuthorized(_)));
    assert!(core.events.lock().await.is_none());
    assert!(core.state.lock().await.client.is_none());
    assert!(!core.is_authorized());
}

#[tokio::test]
async fn an_old_dropped_listener_does_not_discard_a_concurrent_replacement() {
    let (_dir, core) = authorized_core();
    let library = registered_library(&core);
    let failed = installed_listener(&core).await;

    let (waiting, replacement) = tokio::time::timeout(Duration::from_secs(1), async {
        tokio::join!(biased; next(&core, &library, "own"), async {
            assert!(core.events.try_lock().is_err(), "next is actively waiting");
            let replacement = session::connect(&core);
            let handle = replacement.handle.clone();
            core.state.lock().await.client = Some(replacement);
            handle
        })
    })
    .await
    .unwrap();

    assert!(matches!(waiting, Err(CoreError::Network(_))));
    assert!(core.events.lock().await.is_none());
    assert!(!std::sync::Arc::ptr_eq(
        &failed.session,
        &replacement.session
    ));
    assert!(session::is_current(&core, &replacement).await);
    assert!(
        session::updates_receiver(&core, &replacement)
            .await
            .is_some()
    );
    assert!(core.is_authorized());
}

#[tokio::test]
async fn a_replaced_subscription_does_not_consume_the_new_connections_receiver() {
    let (_dir, core) = authorized_core();
    let original = session::connection(&core).await.1;
    let error = open::with_subscription(
        &core,
        subscribe::subscribe_with(&core, |_| async {
            core.state.lock().await.client = Some(session::connect(&core));
            Ok(update_state())
        }),
    )
    .await
    .err()
    .unwrap();

    assert!(matches!(error, CoreError::Network(ref message)
        if message == "this connection's updates are already being read"));
    assert!(!session::is_current(&core, &original).await);
    let (_, replacement) = session::connection(&core).await;
    assert!(
        session::updates_receiver(&core, &replacement)
            .await
            .is_some()
    );
    assert!(core.is_authorized());
}

async fn replace_login(core: &Core) -> grammers_mtsender::SenderPoolFatHandle {
    let mut state = core.state.lock().await;
    *state = crate::api::State::default();
    let mut key = vec![9; 260];
    key[..4].copy_from_slice(&2_i32.to_be_bytes());
    std::fs::write(core.data_dir.join("session.key"), key).unwrap();
    let replacement = session::connect(core);
    let handle = replacement.handle.clone();
    state.client = Some(replacement);
    handle
}

async fn assert_replacement_kept(
    core: &Core,
    replacement: &grammers_mtsender::SenderPoolFatHandle,
) {
    assert!(session::is_current(core, replacement).await);
    assert_eq!(session::load_auth_key(&core.data_dir).unwrap().1, [9; 256]);
}

#[tokio::test]
async fn a_late_subscription_revocation_keeps_the_replacement_login() {
    let (_dir, core) = authorized_core();
    let replacement = std::cell::RefCell::new(None);
    let error = subscribe::subscribe_with(&core, |_| async {
        *replacement.borrow_mut() = Some(replace_login(&core).await);
        Err(rpc(401))
    })
    .await
    .err()
    .unwrap();

    assert!(matches!(error, CoreError::Network(_)));
    let replacement = replacement.into_inner().unwrap();
    assert_replacement_kept(&core, &replacement).await;
    assert!(
        session::updates_receiver(&core, &replacement)
            .await
            .is_some()
    );
}

#[tokio::test]
async fn a_late_stream_initialization_revocation_keeps_the_replacement_login() {
    let (_dir, core) = authorized_core();
    let (_, original) = session::connection(&core).await;
    assert!(session::updates_receiver(&core, &original).await.is_some());
    let replacement = replace_login(&core).await;

    let error = open::finish(&core, original, Err(Box::new(rpc(401))))
        .await
        .err()
        .unwrap();

    assert!(matches!(error, CoreError::Network(_)));
    assert_replacement_kept(&core, &replacement).await;
    assert!(
        session::updates_receiver(&core, &replacement)
            .await
            .is_some()
    );
}

#[tokio::test]
async fn a_late_waiting_revocation_keeps_the_replacement_login() {
    let (_dir, core) = authorized_core();
    installed_listener(&core).await;
    let mut slot = core.events.lock().await;
    let replacement = replace_login(&core).await;

    let error = listener::finish_wait(&core, &mut slot, Err(rpc(401)))
        .await
        .unwrap_err();

    assert!(matches!(error, CoreError::Network(_)));
    assert!(slot.is_none());
    assert_replacement_kept(&core, &replacement).await;
    assert!(
        session::updates_receiver(&core, &replacement)
            .await
            .is_some()
    );
}
