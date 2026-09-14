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

/// Flood waits and server-side failures are worth retrying; client errors such
/// as bad requests or a dead auth key never fix themselves.
fn is_retryable(err: &InvocationError) -> bool {
    match err {
        InvocationError::Rpc(rpc) => rpc.name == "FLOOD_WAIT" || rpc.code >= 500,
        _ => true,
    }
}

/// Runs `op` up to `max_attempts` times. A `FLOOD_WAIT` error sleeps for the
/// server-specified duration before retrying; any other error backs off
/// exponentially. Returns the last error once `max_attempts` is reached.
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
                    None => Duration::from_millis(BACKOFF_BASE_MS * 2u64.pow(attempt - 1)),
                };
                tracing::warn!(attempt, ?delay, error = %err, "retrying Telegram request");
                tokio::time::sleep(delay).await;
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use std::cell::Cell;

    use grammers_mtsender::RpcError;

    use super::*;

    fn flood_wait(secs: u32) -> InvocationError {
        InvocationError::Rpc(RpcError {
            code: 420,
            name: "FLOOD_WAIT".into(),
            value: Some(secs),
            caused_by: None,
        })
    }

    fn other_error() -> InvocationError {
        InvocationError::Rpc(RpcError {
            code: 500,
            name: "INTERNAL".into(),
            value: None,
            caused_by: None,
        })
    }

    #[test]
    fn flood_wait_secs_adds_one_second_of_slack() {
        assert_eq!(flood_wait_secs(&flood_wait(5)), Some(6));
    }

    #[test]
    fn flood_wait_secs_is_none_for_other_rpc_errors() {
        assert_eq!(flood_wait_secs(&other_error()), None);
    }

    #[tokio::test]
    async fn with_retry_sleeps_and_retries_on_flood_wait() {
        let calls = Cell::new(0);
        let result: Result<()> = with_retry(3, || {
            calls.set(calls.get() + 1);
            let first_call = calls.get() == 1;
            async move {
                if first_call {
                    Err(flood_wait(0))
                } else {
                    Ok(())
                }
            }
        })
        .await;

        assert!(result.is_ok());
        assert_eq!(calls.get(), 2);
    }

    #[tokio::test]
    async fn with_retry_gives_up_after_max_attempts() {
        let calls = Cell::new(0);
        let result: Result<()> = with_retry(3, || {
            calls.set(calls.get() + 1);
            async move { Err(other_error()) }
        })
        .await;

        assert!(result.is_err());
        assert_eq!(calls.get(), 3);
    }
}
