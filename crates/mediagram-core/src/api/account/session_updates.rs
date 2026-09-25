//! Handing the one-shot update receiver to the connection that subscribed it.

use std::sync::Arc;

use grammers_client::Client;
use grammers_mtsender::SenderPoolFatHandle;
use grammers_session::updates::UpdatesLike;
use tokio::sync::mpsc::UnboundedReceiver;

use super::{ClientHandle, connect};
use crate::api::{Core, State};

/// The single lazy connection, shared by playback, account calls, and updates.
/// A second sender pool using the same key would interfere with this one.
pub(in crate::api) async fn client(core: &Core) -> Client {
    connection(core).await.0
}

pub(in crate::api) async fn connection(core: &Core) -> (Client, SenderPoolFatHandle) {
    let mut state = core.state.lock().await;
    let live = state.client.get_or_insert_with(|| connect(core));
    (live.client.clone(), live.handle.clone())
}

fn matches(live: &ClientHandle, expected: &SenderPoolFatHandle) -> bool {
    Arc::ptr_eq(&live.handle.session, &expected.session)
}

pub(in crate::api) async fn is_current(core: &Core, expected: &SenderPoolFatHandle) -> bool {
    core.state
        .lock()
        .await
        .client
        .as_ref()
        .is_some_and(|live| matches(live, expected))
}

/// Consume only the receiver whose connection subscribed successfully. A
/// concurrent replacement retains its receiver for its own subscription.
pub(in crate::api) async fn updates_receiver(
    core: &Core,
    expected: &SenderPoolFatHandle,
) -> Option<UnboundedReceiver<UpdatesLike>> {
    let mut state = core.state.lock().await;
    state
        .client
        .as_mut()
        .filter(|live| matches(live, expected))?
        .updates
        .take()
}

/// A stopped/consumed connection cannot provide another receiver. Discard
/// its associated state only if a newer connection has not replaced it.
pub(in crate::api) async fn invalidate(core: &Core, failed: &SenderPoolFatHandle) {
    let mut state = core.state.lock().await;
    if state
        .client
        .as_ref()
        .is_some_and(|live| matches(live, failed))
    {
        *state = State::default();
    }
}
