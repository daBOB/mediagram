use super::*;
use crate::api::account::session;
use grammers_client::client::UpdatesConfiguration;
use grammers_mtsender::InvocationError;
use std::time::Duration;

#[path = "events_lifecycle_tests.rs"]
mod lifecycle;

fn pinned(pinned: bool) -> tl::enums::Update {
    tl::types::UpdatePinnedChannelMessages {
        pinned,
        channel_id: 3816522481,
        messages: vec![3123],
        pts: 1,
        pts_count: 1,
    }
    .into()
}

#[test]
fn a_pin_keeps_its_channel_and_direction() {
    let update = channel_update(&pinned(true)).expect("a pin is a channel update");
    assert_eq!(update.kind, UpdateKind::Pinned);
    assert_eq!(update.channel, 3816522481);
    assert!(update.pinned);
    assert!(!channel_update(&pinned(false)).expect("an unpin too").pinned);
}

#[test]
fn kinds_no_rule_acts_on_are_not_passed_along() {
    let deleted: tl::enums::Update = tl::types::UpdateDeleteChannelMessages {
        channel_id: 3816522481,
        messages: vec![3121],
        pts: 1,
        pts_count: 1,
    }
    .into();
    assert!(channel_update(&deleted).is_none());
}

async fn installed_listener(core: &Core) -> grammers_mtsender::SenderPoolFatHandle {
    let (client, handle) = session::connection(core).await;
    let updates = session::updates_receiver(core, &handle).await;
    let stream = client
        .stream_updates(
            updates.unwrap(),
            UpdatesConfiguration {
                catch_up: false,
                update_queue_limit: Some(100),
            },
        )
        .await
        .unwrap();
    *core.events.lock().await = Some(Listener::new(stream, handle.clone()));
    handle
}

fn registered_library(core: &Core) -> String {
    let mut handles = library::Handles::new();
    let handle = library::register_or_refresh_library(
        &mut handles,
        library::LibraryEntry {
            chat: -1_000_000_000_123,
            auth: 0,
            title: "Test library".into(),
        },
    );
    library::write(&library::path(core), &handles).unwrap();
    handle
}

#[tokio::test]
async fn a_dropped_listener_releases_its_consumed_connection_for_reinitialization() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let library = registered_library(&core);
    let failed = installed_listener(&core).await;
    failed.quit();

    let error = tokio::time::timeout(Duration::from_secs(1), next(&core, &library, "own"))
        .await
        .unwrap()
        .unwrap_err();

    assert!(matches!(error, CoreError::Network(_)));
    assert!(core.events.lock().await.is_none());
    assert!(core.state.lock().await.client.is_none());
    let (_, handle) = session::connection(&core).await;
    let receiver = session::updates_receiver(&core, &handle).await;
    assert!(
        receiver.is_some(),
        "retry must receive a fresh sender-pool receiver"
    );
}

#[tokio::test]
async fn revocation_clears_an_idle_listener_along_with_the_persisted_key() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let mut key = vec![7; 260];
    key[..4].copy_from_slice(&2_i32.to_be_bytes());
    std::fs::write(dir.path().join("session.key"), key).unwrap();
    let owner = installed_listener(&core).await;
    let revoked = InvocationError::Rpc(grammers_mtsender::RpcError::from(tl::types::RpcError {
        error_code: 401,
        error_message: "SESSION_REVOKED".into(),
    }));

    let error = crate::api::account::revoked::unless_revoked_for(
        &core,
        &owner,
        &revoked,
        CoreError::Network("context".into()),
    )
    .await;

    assert!(matches!(error, CoreError::NotAuthorized(_)));
    assert!(!core.is_authorized());
    assert!(core.events.lock().await.is_none());
}
