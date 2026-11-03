use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};

use crate::starter::starter_questions;
use crate::{GameFactory, Host, HostConfig, Quiz, QuizConfig};

fn factory() -> GameFactory {
    Box::new(|ctx, _| Box::new(Quiz::new(QuizConfig::default(), starter_questions(), ctx.now)))
}

async fn start(preferred_port: u16) -> Host {
    let cfg = HostConfig { preferred_port, ..HostConfig::default() };
    Host::start(cfg, factory()).await.unwrap()
}

#[tokio::test]
async fn stop_frees_the_port() {
    let host = start(0).await;
    let addr = ("127.0.0.1", host.port());
    assert!(TcpStream::connect(addr).await.is_ok());
    host.stop().await;
    assert!(TcpStream::connect(addr).await.is_err());
}

#[tokio::test]
async fn falls_back_when_preferred_port_is_taken() {
    let taken = TcpListener::bind("0.0.0.0:0").await.unwrap();
    let p = taken.local_addr().unwrap().port();
    let host = start(p).await;
    assert_ne!(host.port(), p);
    host.stop().await;
}

#[tokio::test]
async fn qr_svg_is_served() {
    let host = start(0).await;
    let mut s = TcpStream::connect(("127.0.0.1", host.port())).await.unwrap();
    s.write_all(b"GET /qr.svg HTTP/1.1\r\nhost: x\r\nconnection: close\r\n\r\n").await.unwrap();
    let mut raw = String::new();
    s.read_to_string(&mut raw).await.unwrap();
    let (head, body) = raw.split_once("\r\n\r\n").unwrap();
    assert!(head.starts_with("HTTP/1.1 200"), "{head}");
    assert!(head.to_lowercase().contains("content-type: image/svg+xml"), "{head}");
    assert!(body.contains("<svg"));
    host.stop().await;
}
