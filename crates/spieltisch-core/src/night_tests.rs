use std::net::TcpListener;

use super::*;

fn port_of(urls: &NightUrls) -> u16 {
    urls.tv_url.rsplit(':').next().unwrap().trim_end_matches("/tv").parse().unwrap()
}

fn is_free(port: u16) -> bool {
    TcpListener::bind(("0.0.0.0", port)).is_ok()
}

#[tokio::test]
async fn start_twice_replaces_the_first() {
    let slot = AsyncMutex::new(None);
    let first = port_of(&replace(&slot, true, 0, None).await.unwrap());
    let second = port_of(&replace(&slot, true, 0, None).await.unwrap());
    assert!(first == second || is_free(first), "the first night's port must be released");

    stop(&slot).await;
    assert!(is_free(second), "stopping must free the port");
    assert!(!back(&slot).await, "no night runs after stop");
    stop(&slot).await;
}

#[tokio::test]
async fn a_night_without_a_game_has_nothing_to_step_back_from() {
    let slot = AsyncMutex::new(None);
    let urls = replace(&slot, true, 0, None).await.unwrap();
    assert!(urls.tv_url.starts_with("http://127.0.0.1:"));

    assert!(!back(&slot).await, "the lobby is already where Back leads");
    stop(&slot).await;
}

#[tokio::test]
async fn the_exported_night_starts_and_stops() {
    let data_dir = std::env::temp_dir().to_string_lossy().into_owned();
    let config = SpieltischConfig { kids_locked: true, data_dir };
    let urls = start_night(config).await.unwrap();
    assert!(!urls.join_url.is_empty());
    stop_night().await;
    assert!(!night_back().await);
}
