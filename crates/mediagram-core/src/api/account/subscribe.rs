//! Subscribing a connection to the updates Telegram pushes.
//!
//! Beside [`session`] rather than in it: this is the update listener's one
//! need from the connection, and the session module is about keeping one.

use super::super::{Core, CoreError};
use super::session;

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
pub(in crate::api) async fn subscribe(core: &Core) -> Result<(), CoreError> {
    use grammers_session::types::{UpdateState, UpdatesState};
    use grammers_tl_types as tl;

    let unavailable = || CoreError::Network("could not start listening for library changes".into());
    let client = session::client(core).await;
    let tl::enums::updates::State::State(state) = client
        .invoke(&tl::functions::updates::GetState {})
        .await
        .map_err(unavailable().logged())?;
    let session = {
        let state = core.state.lock().await;
        state
            .client
            .as_ref()
            .map(|live| live.handle.session.clone())
    }
    .ok_or_else(unavailable)?;
    session
        .set_update_state(UpdateState::All(UpdatesState {
            pts: state.pts,
            qts: state.qts,
            date: state.date,
            seq: state.seq,
            channels: Vec::new(),
        }))
        .await
        .map_err(unavailable().logged())
}
