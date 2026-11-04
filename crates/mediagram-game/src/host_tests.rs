use std::time::{Duration, Instant};

use tokio::io::AsyncWriteExt;
use tokio::net::TcpStream;

use super::*;

#[tokio::test]
async fn a_stalled_request_does_not_hold_the_stop() {
    let cfg = HostConfig { preferred_port: 0, ..HostConfig::default() };
    let host = Host::start(cfg, Box::new(|_, _| unreachable!("no game is started"))).await.unwrap();
    let mut stalled = TcpStream::connect(("127.0.0.1", host.port())).await.unwrap();
    // Half a request head, then silence: the server keeps waiting for the rest.
    stalled.write_all(b"GET /tv HTTP/1.1\r\nHost: x\r\n").await.unwrap();
    tokio::time::sleep(Duration::from_millis(100)).await;

    let began = Instant::now();
    tokio::time::timeout(Duration::from_secs(5), host.stop()).await.expect("stop returns while the request stalls");
    assert!(began.elapsed() < STOP_WAIT + Duration::from_secs(1));
    drop(stalled);
}
