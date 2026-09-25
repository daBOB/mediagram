//! Owning one update stream and releasing a failed connection before retry.

use std::collections::VecDeque;
use std::time::Duration;

use grammers_client::client::UpdateStream;
use grammers_mtsender::{InvocationError, SenderPoolFatHandle};
use tokio::time::Instant;

use crate::api::account::{revoked, session};
use crate::api::{Core, CoreError};
use crate::updates::{Debouncer, LibraryEvent, classify};

use super::channel_update;

/// A burst — an upload's parts, index, pin and unpin — folds into one event.
const WINDOW_MS: u64 = 5_000;

pub(in crate::api) struct Listener {
    stream: UpdateStream,
    pub(in crate::api) handle: SenderPoolFatHandle,
    debouncer: Debouncer,
    ready: VecDeque<LibraryEvent>,
    started: Instant,
}

impl Listener {
    pub(super) fn new(stream: UpdateStream, handle: SenderPoolFatHandle) -> Self {
        Self {
            stream,
            handle,
            debouncer: Debouncer::new(WINDOW_MS),
            ready: VecDeque::new(),
            started: Instant::now(),
        }
    }
}

pub(super) async fn next(
    core: &Core,
    channel: i64,
    own_device: &str,
) -> Result<LibraryEvent, CoreError> {
    let mut slot = core.events.lock().await;
    if let Some(listener) = slot.as_ref()
        && !session::is_current(core, &listener.handle).await
    {
        *slot = None;
    }
    if slot.is_none() {
        *slot = Some(super::open::open(core).await?);
    }
    let result = slot
        .as_mut()
        .expect("just set")
        .wait(channel, own_device)
        .await;
    finish_wait(core, &mut slot, result).await
}

pub(super) async fn finish_wait(
    core: &Core,
    slot: &mut Option<Listener>,
    result: Result<LibraryEvent, InvocationError>,
) -> Result<LibraryEvent, CoreError> {
    let handle = slot
        .as_ref()
        .expect("waiting listener owns the slot")
        .handle
        .clone();
    match &result {
        Err(InvocationError::Dropped) => {
            if let Some(failed) = slot.take() {
                session::invalidate(core, &failed.handle).await;
            }
        }
        Err(err) if revoked::is_revoked(err) => *slot = None,
        _ => {}
    }
    revoked::checked_for(core, &handle, result, |err| {
        CoreError::network("stopped listening for library changes")(err)
    })
    .await
}

impl Listener {
    fn now_ms(&self) -> u64 {
        u64::try_from(self.started.elapsed().as_millis()).unwrap_or(u64::MAX)
    }

    async fn wait(
        &mut self,
        channel: i64,
        own_device: &str,
    ) -> Result<LibraryEvent, InvocationError> {
        loop {
            if let Some(event) = self.ready.pop_front() {
                return Ok(event);
            }
            let now = self.now_ms();
            self.ready.extend(self.debouncer.take(now));
            if !self.ready.is_empty() {
                continue;
            }
            let raw = match self.debouncer.next_due() {
                // A window is open: wait for more, but no longer than its end.
                Some(due) => {
                    let deadline = self.started + Duration::from_millis(due);
                    match tokio::time::timeout_at(deadline, self.stream.next_raw()).await {
                        Ok(raw) => raw?,
                        Err(_) => continue,
                    }
                }
                None => self.stream.next_raw().await?,
            };
            let event =
                channel_update(&raw.0).and_then(|update| classify(&update, channel, own_device));
            if let Some(event) = event {
                let now = self.now_ms();
                self.debouncer.offer(event, now);
            }
        }
    }
}
