//! Subscribe before consuming the receiver, and release failed initialization.

use grammers_client::Client;
use grammers_client::client::{UpdateStream, UpdatesConfiguration};
use grammers_mtsender::SenderPoolFatHandle;

use crate::api::account::{revoked, session, subscribe};
use crate::api::{Core, CoreError};

use super::Listener;

pub(super) async fn open(core: &Core) -> Result<Listener, CoreError> {
    with_subscription(core, subscribe::subscribe(core)).await
}

pub(super) async fn with_subscription(
    core: &Core,
    subscription: impl std::future::Future<Output = Result<(Client, SenderPoolFatHandle), CoreError>>,
) -> Result<Listener, CoreError> {
    let (client, handle) = subscription.await?;
    let Some(updates) = session::updates_receiver(core, &handle).await else {
        session::invalidate(core, &handle).await;
        return Err(CoreError::Network(
            "this connection's updates are already being read".into(),
        ));
    };
    // Changes are hints, and callers run an ordinary sync at startup. Bound
    // updates held while nobody waits: a round recovers anything discarded.
    let configuration = UpdatesConfiguration {
        catch_up: true,
        update_queue_limit: Some(100),
    };
    let result = client.stream_updates(updates, configuration).await;
    finish(core, handle, result).await
}

pub(super) async fn finish(
    core: &Core,
    handle: SenderPoolFatHandle,
    result: Result<UpdateStream, Box<dyn std::error::Error + Send + Sync>>,
) -> Result<Listener, CoreError> {
    match result {
        Ok(stream) => Ok(Listener::new(stream, handle)),
        Err(err) => {
            // The receiver was consumed even if stream construction failed.
            let fallback =
                CoreError::network("could not start listening for library changes")(&err);
            let error = revoked::unless_revoked_for(core, &handle, err.as_ref(), fallback).await;
            session::invalidate(core, &handle).await;
            Err(error)
        }
    }
}
