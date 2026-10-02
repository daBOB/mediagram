use super::super::responses::Scripted;
use super::*;
use crate::api::account::session::fixture::{Fixture, rpc};

#[tokio::test]
async fn a_channel_with_no_pins_has_no_release() {
    let fixture = Fixture::new().await;
    let found = latest_with(
        &fixture.core,
        &fixture.owner,
        -100,
        Scripted(vec![].into_iter()),
    )
    .await;
    assert_eq!(found.unwrap(), None);
}

#[tokio::test]
async fn a_refused_pin_read_is_an_error_not_an_empty_answer() {
    let fixture = Fixture::new().await;
    let found = latest_with(
        &fixture.core,
        &fixture.owner,
        -100,
        Scripted(vec![Err(rpc(500))].into_iter()),
    )
    .await;
    assert!(found.is_err());
}
