use std::net::TcpListener;

use super::*;

fn port_of(urls: &GameNightUrls) -> u16 {
    urls.tv_url.rsplit(':').next().unwrap().trim_end_matches("/tv").parse().unwrap()
}

fn is_free(port: u16) -> bool {
    TcpListener::bind(("0.0.0.0", port)).is_ok()
}

#[tokio::test]
async fn start_twice_replaces_the_first() {
    let slot = AsyncMutex::new(None);
    let first = port_of(&replace(&slot, Vec::new(), false, 0, None).await.unwrap());
    let second = port_of(&replace(&slot, Vec::new(), false, 0, None).await.unwrap());
    assert!(first == second || is_free(first), "the first night's port must be released");

    stop(&slot).await;
    assert!(is_free(second), "stopping must free the port");
    assert!(!back(&slot).await, "no night runs after stop");
    stop(&slot).await;
}
