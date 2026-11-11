use std::net::TcpListener;
use std::time::{Duration, Instant};

use super::*;

fn port_of(urls: &GameNightUrls) -> u16 {
    urls.tv_url.rsplit(':').next().unwrap().trim_end_matches("/tv").parse().unwrap()
}

/// In a parallel test run a just-closed port can read as taken for a few
/// microseconds (never in a serial run); a port the night really leaked
/// stays taken past the wait.
fn is_free(port: u16) -> bool {
    let deadline = Instant::now() + Duration::from_millis(500);
    loop {
        if TcpListener::bind(("0.0.0.0", port)).is_ok() {
            return true;
        }
        if Instant::now() >= deadline {
            return false;
        }
        std::thread::sleep(Duration::from_millis(1));
    }
}

#[tokio::test]
async fn start_twice_replaces_the_first() {
    let slot = AsyncMutex::new(None);
    let first = port_of(&replace(&slot, Vec::new(), false, 0, None, None).await.unwrap());
    let second = port_of(&replace(&slot, Vec::new(), false, 0, None, None).await.unwrap());
    assert!(first == second || is_free(first), "the first night's port must be released");

    stop(&slot).await;
    assert!(is_free(second), "stopping must free the port");
    assert!(!back(&slot).await, "no night runs after stop");
    stop(&slot).await;
}
