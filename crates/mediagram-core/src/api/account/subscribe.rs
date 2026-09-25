//! Subscribing a connection to the updates Telegram pushes.
//!
//! Beside [`session`] rather than in it: this is the update listener's one
//! need from the connection, and the session module is about keeping one.

use super::super::{Core, CoreError};
use super::{revoked, session};
use grammers_client::Client;
use grammers_mtsender::{InvocationError, SenderPoolFatHandle};
use grammers_tl_types as tl;

/// Asks Telegram for the account's update state and stores it in the
/// session, which is also what subscribes this connection to pushed updates.
///
/// Done here, before the listener takes the receiver, rather than left to
/// grammers: its stream asks once, ignores a failure, and never asks again
/// — and it only asks at all when the session already knows its own user,
/// which the in-memory session this core keeps does not. Either way the
/// stream stays silent. Here a failure is an error the caller can retry, and
/// the stored state gives the stream a base to recover gaps from after the
/// connection drops.
pub(in crate::api) async fn subscribe(
    core: &Core,
) -> Result<(Client, SenderPoolFatHandle), CoreError> {
    subscribe_with(core, |client| async move {
        client.invoke(&tl::functions::updates::GetState {}).await
    })
    .await
}

/// Only the GetState request is replaceable; connection ownership and
/// subscription state are shared by production and offline lifecycle tests.
pub(in crate::api) async fn subscribe_with<F>(
    core: &Core,
    request: impl FnOnce(Client) -> F,
) -> Result<(Client, SenderPoolFatHandle), CoreError>
where
    F: std::future::Future<Output = Result<tl::enums::updates::State, InvocationError>>,
{
    use grammers_session::types::{UpdateState, UpdatesState};

    let unavailable = || CoreError::Network("could not start listening for library changes".into());
    let (client, handle) = session::connection(core).await;
    let tl::enums::updates::State::State(state) =
        revoked::checked_for(core, &handle, request(client.clone()).await, |err| {
            unavailable().logged()(err)
        })
        .await?;
    handle
        .session
        .set_update_state(UpdateState::All(UpdatesState {
            pts: state.pts,
            qts: state.qts,
            date: state.date,
            seq: state.seq,
            channels: Vec::new(),
        }))
        .await
        .map_err(unavailable().logged())?;
    Ok((client, handle))
}
