use super::*;

/// What `subscribe` hands back once Telegram has answered: the core's own
/// connection. No request is sent here.
async fn subscribed(core: &Core) -> Result<(Client, SenderPoolFatHandle), CoreError> {
    Ok(session::connection(core).await)
}

/// Nothing was consumed, so nothing is released: the connection and its
/// updates stay for the retry, which only has to subscribe again.
#[tokio::test]
async fn a_failed_subscription_leaves_the_connection_and_its_updates_for_a_retry() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let failing = async {
        session::connection(&core).await;
        Err(CoreError::Network("offline".into()))
    };

    let error = with_subscription(&core, failing).await.err().unwrap();

    assert!(matches!(error, CoreError::Network(message) if message == "offline"));
    let (_, handle) = session::connection(&core).await;
    assert!(session::updates_receiver(&core, &handle).await.is_some());
}

#[tokio::test]
async fn a_subscribed_connection_hands_its_updates_to_the_listener() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());

    let listener = with_subscription(&core, subscribed(&core)).await.unwrap();

    assert!(session::is_current(&core, &listener.handle).await);
    assert!(
        session::updates_receiver(&core, &listener.handle)
            .await
            .is_none(),
        "the listener holds them"
    );
}

/// A connection whose updates someone already reads can never give a second
/// listener any, so it is released: the retry gets a fresh one that can.
#[tokio::test]
async fn a_second_listener_on_one_connection_is_refused_and_the_connection_released() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let first = with_subscription(&core, subscribed(&core)).await.unwrap();

    let error = with_subscription(&core, subscribed(&core))
        .await
        .err()
        .unwrap();

    assert!(matches!(error, CoreError::Network(message)
        if message == "this connection's updates are already being read"));
    assert!(!session::is_current(&core, &first.handle).await);
    let (_, fresh) = session::connection(&core).await;
    assert!(session::updates_receiver(&core, &fresh).await.is_some());
}
