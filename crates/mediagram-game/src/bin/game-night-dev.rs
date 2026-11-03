//! Hosts the buzzer quiz on the LAN for development, without the app.
//! `GAME_PORT` and `GAME_MIN_PLAYERS` override the defaults.

use mediagram_game::starter::starter_questions;
use mediagram_game::{Game, GameCtx, Host, HostConfig, Quiz, QuizConfig};
use qrcode::QrCode;
use qrcode::render::unicode;

fn env_or<T: std::str::FromStr>(key: &str, default: T) -> T {
    std::env::var(key).ok().and_then(|v| v.parse().ok()).unwrap_or(default)
}

#[tokio::main]
async fn main() -> std::io::Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "info".into()),
        )
        .init();

    let defaults = HostConfig::default();
    let cfg = HostConfig {
        preferred_port: env_or("GAME_PORT", defaults.preferred_port),
        min_players: env_or("GAME_MIN_PLAYERS", defaults.min_players),
        max_players: defaults.max_players,
    };
    let factory = Box::new(|ctx: &mut GameCtx| {
        Box::new(Quiz::new(QuizConfig::default(), starter_questions(), ctx.now)) as Box<dyn Game>
    });
    let host = Host::start(cfg, factory).await?;

    println!();
    if let Ok(code) = QrCode::new(host.join_url().as_bytes()) {
        let qr = code
            .render::<unicode::Dense1x2>()
            .dark_color(unicode::Dense1x2::Light)
            .light_color(unicode::Dense1x2::Dark)
            .build();
        println!("{qr}");
    }
    println!("  Spieltisch läuft.");
    println!("  Handys:  {}", host.join_url());
    println!("  TV:      {}", host.tv_url());
    println!();

    tokio::signal::ctrl_c().await?;
    host.stop().await;
    Ok(())
}
