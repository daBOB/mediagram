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

#[test]
fn backoff_doubles_per_attempt_and_then_stops_growing() {
    assert_eq!(backoff(1), Duration::from_millis(200));
    assert_eq!(backoff(2), Duration::from_millis(400));
    assert_eq!(backoff(3), Duration::from_millis(800));
    // A large `max_attempts` must not shift the multiplier out of range.
    assert_eq!(backoff(u32::MAX), backoff(11));
}

#[tokio::test(start_paused = true)]
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

#[tokio::test(start_paused = true)]
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

#[tokio::test(start_paused = true)]
async fn flood_only_does_not_repeat_an_ambiguous_server_failure() {
    let calls = Cell::new(0);
    let started = tokio::time::Instant::now();
    let result: Result<()> = with_flood_wait_only(3, || {
        calls.set(calls.get() + 1);
        async { Err(other_error()) }
    })
    .await;

    assert_eq!(
        calls.get(),
        1,
        "a committed send may have lost its response"
    );
    assert_eq!(started.elapsed(), Duration::ZERO);
    assert!(matches!(
        result.unwrap_err().downcast_ref::<InvocationError>(),
        Some(InvocationError::Rpc(rpc)) if rpc.code == 500 && rpc.name == "INTERNAL"
    ));
}

#[tokio::test(start_paused = true)]
async fn flood_only_does_not_repeat_an_ambiguous_transport_failure() {
    let calls = Cell::new(0);
    let started = tokio::time::Instant::now();
    let result: Result<()> = with_flood_wait_only(3, || {
        calls.set(calls.get() + 1);
        async { Err(InvocationError::Dropped) }
    })
    .await;

    assert_eq!(
        calls.get(),
        1,
        "a dropped response does not prove send failure"
    );
    assert_eq!(started.elapsed(), Duration::ZERO);
    assert!(matches!(
        result.unwrap_err().downcast_ref::<InvocationError>(),
        Some(InvocationError::Dropped)
    ));
}

#[tokio::test(start_paused = true)]
async fn flood_only_waits_for_the_server_delay_and_slack_before_retrying() {
    let calls = Cell::new(0);
    let started = tokio::time::Instant::now();
    let result = with_flood_wait_only(3, || {
        calls.set(calls.get() + 1);
        let attempt = calls.get();
        async move {
            if attempt == 1 {
                Err(flood_wait(7))
            } else {
                assert_eq!(started.elapsed(), Duration::from_secs(8));
                Ok("sent once")
            }
        }
    })
    .await;

    assert_eq!(result.unwrap(), "sent once");
    assert_eq!(calls.get(), 2);
}

#[tokio::test(start_paused = true)]
async fn flood_only_stops_at_the_attempt_budget_without_a_final_sleep() {
    for max_attempts in [0u32, 1, 3] {
        let calls = Cell::new(0);
        let started = tokio::time::Instant::now();
        let result: Result<()> = with_flood_wait_only(max_attempts, || {
            calls.set(calls.get() + 1);
            let attempt = calls.get();
            async move { Err(flood_wait(attempt)) }
        })
        .await;

        let attempts = max_attempts.max(1);
        assert_eq!(calls.get(), attempts);
        assert_eq!(
            started.elapsed(),
            Duration::from_secs((1..attempts).map(|attempt| u64::from(attempt) + 1).sum())
        );
        assert!(matches!(
            result.unwrap_err().downcast_ref::<InvocationError>(),
            Some(InvocationError::Rpc(rpc)) if rpc.name == "FLOOD_WAIT" && rpc.value == Some(attempts)
        ));
    }
}

#[tokio::test(start_paused = true)]
async fn flood_only_stops_on_an_ambiguous_failure_after_an_allowed_retry() {
    let calls = Cell::new(0);
    let started = tokio::time::Instant::now();
    let result: Result<()> = with_flood_wait_only(4, || {
        calls.set(calls.get() + 1);
        let first = calls.get() == 1;
        async move { Err(if first { flood_wait(0) } else { other_error() }) }
    })
    .await;

    assert_eq!(calls.get(), 2);
    assert_eq!(started.elapsed(), Duration::from_secs(1));
    assert!(matches!(
        result.unwrap_err().downcast_ref::<InvocationError>(),
        Some(InvocationError::Rpc(rpc)) if rpc.code == 500
    ));
}
