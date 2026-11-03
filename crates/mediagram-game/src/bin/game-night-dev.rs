//! Hosts the buzzer quiz on the LAN for development, without the app.
//! `GAME_PORT` and `GAME_MIN_PLAYERS` override the defaults; `--kids-locked`
//! starts the night as a kids profile would. `--titles <file>` loads the
//! title list (image paths relative to the workspace root).

use mediagram_game::protocol::LobbySettings;
use mediagram_game::starter::starter_questions;
use mediagram_game::{Game, GameCtx, GameTitle, Host, HostConfig, Quiz, QuizConfig, TitlePool};
use qrcode::QrCode;
use qrcode::render::unicode;

fn env_or<T: std::str::FromStr>(key: &str, default: T) -> T {
    std::env::var(key).ok().and_then(|v| v.parse().ok()).unwrap_or(default)
}

const WORKSPACE: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../..");

fn load_titles(path: &str) -> std::io::Result<Vec<GameTitle>> {
    let mut titles: Vec<GameTitle> = serde_json::from_str(&std::fs::read_to_string(path)?)?;
    for t in &mut titles {
        for image in [&mut t.poster, &mut t.still].into_iter().flatten() {
            *image = std::path::Path::new(WORKSPACE).join(&*image);
        }
    }
    Ok(titles)
}

#[tokio::main]
async fn main() -> std::io::Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "info".into()),
        )
        .init();

    let args: Vec<String> = std::env::args().collect();
    let kids_locked = args.iter().any(|a| a == "--kids-locked");
    let titles = match args.iter().position(|a| a == "--titles").and_then(|i| args.get(i + 1)) {
        Some(path) => load_titles(path)?,
        None => Vec::new(),
    };
    let defaults = HostConfig::default();
    let cfg = HostConfig {
        preferred_port: env_or("GAME_PORT", defaults.preferred_port),
        min_players: env_or("GAME_MIN_PLAYERS", defaults.min_players),
        max_players: defaults.max_players,
        kids_locked,
        readiness: defaults.readiness,
        pool: std::sync::Arc::new(TitlePool::new(titles, kids_locked)),
    };
    let factory = Box::new(|ctx: &mut GameCtx, _: &LobbySettings| {
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
