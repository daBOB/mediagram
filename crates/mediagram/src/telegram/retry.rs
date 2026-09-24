//! Retry wrapper for Telegram RPC calls: FLOOD_WAIT-aware sleep-and-retry,
//! exponential backoff for other transient errors.

use std::future::Future;
use std::time::Duration;

use anyhow::Result;
use grammers_mtsender::InvocationError;

/// Base delay for the exponential backoff applied to non-FLOOD_WAIT errors.
const BACKOFF_BASE_MS: u64 = 200;

/// Seconds to sleep before retrying, extracted from a Telegram `FLOOD_WAIT_n`
/// RPC error. Adds one second of slack on top of the server-reported value.
/// Returns `None` for any other error.
pub fn flood_wait_secs(err: &InvocationError) -> Option<u64> {
    match err {
        InvocationError::Rpc(rpc) if rpc.name == "FLOOD_WAIT" => {
            Some(u64::from(rpc.value.unwrap_or(1)) + 1)
        }
        _ => None,
    }
}

/// How long to wait before attempt `attempt + 1`, doubling each time. Capped
/// so a generous `max_attempts` cannot shift the multiplier past what a `u64`
/// of milliseconds holds.
pub fn backoff(attempt: u32) -> Duration {
    Duration::from_millis(BACKOFF_BASE_MS * 2u64.pow(attempt.saturating_sub(1).min(10)))
}

/// Flood waits and server-side failures are worth retrying; client errors such
/// as bad requests or a dead auth key never fix themselves.
fn is_retryable(err: &InvocationError) -> bool {
    match err {
        InvocationError::Rpc(rpc) => rpc.name == "FLOOD_WAIT" || rpc.code >= 500,
        _ => true,
    }
}

/// Runs `op` up to `max_attempts` times. A `FLOOD_WAIT` error sleeps for the
/// server-specified duration before retrying, and any other retryable error
/// (see `is_retryable`) backs off exponentially. A non-retryable error is
/// returned at once, as is the last error once `max_attempts` is reached.
/// A zero attempt budget still invokes `op` once.
pub async fn with_retry<T, F, Fut>(max_attempts: u32, op: F) -> Result<T>
where
    F: FnMut() -> Fut,
    Fut: Future<Output = Result<T, InvocationError>>,
{
    with_retry_if(max_attempts, op, is_retryable).await
}

/// Like [`with_retry`] but only ever retries on `FLOOD_WAIT`. Use it for
/// requests that are not idempotent (e.g. `send_message`): a lost response
/// after a committed send must surface as an error, not as a second copy.
pub async fn with_flood_wait_only<T, F, Fut>(max_attempts: u32, op: F) -> Result<T>
where
    F: FnMut() -> Fut,
    Fut: Future<Output = Result<T, InvocationError>>,
{
    with_retry_if(max_attempts, op, |err| flood_wait_secs(err).is_some()).await
}

async fn with_retry_if<T, F, Fut, P>(max_attempts: u32, mut op: F, should_retry: P) -> Result<T>
where
    F: FnMut() -> Fut,
    Fut: Future<Output = Result<T, InvocationError>>,
    P: Fn(&InvocationError) -> bool,
{
    let max_attempts = max_attempts.max(1);
    let mut attempt = 0u32;
    loop {
        attempt += 1;
        match op().await {
            Ok(value) => return Ok(value),
            Err(err) if attempt >= max_attempts || !should_retry(&err) => return Err(err.into()),
            Err(err) => {
                let delay = match flood_wait_secs(&err) {
                    Some(secs) => Duration::from_secs(secs),
                    None => backoff(attempt),
                };
                tracing::warn!(attempt, ?delay, error = %err, "retrying Telegram request");
                tokio::time::sleep(delay).await;
            }
        }
    }
}

#[cfg(test)]
#[path = "retry_tests.rs"]
mod tests;
