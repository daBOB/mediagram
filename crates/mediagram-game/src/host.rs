use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::Arc;
use std::sync::atomic::AtomicU64;
use std::time::Duration;

use tokio::net::TcpListener;
use tokio::sync::{mpsc, oneshot};
use tokio::task::JoinHandle;

use crate::factory::game_factory;
use crate::game::GameFactory;
use crate::general::general_pack;
use crate::image_files::ImageLookup;
use crate::lan::lan_ip;
use crate::poster_config::poster_ready;
use crate::poster_lookup::PosterLookup;
use crate::room::{Room, RoomConfig};
use crate::room_loop::{Event, room_loop};
use crate::room_settings::Readiness;
use crate::server::{AppState, router};
use crate::titles::{GameTitle, TitlePool};

const STOP_WAIT: Duration = Duration::from_secs(2);
/// The most players a room seats unless its host says otherwise.
pub(crate) const ROOM_CAP: u8 = 8;

#[derive(Clone)]
pub struct HostConfig {
    pub preferred_port: u16,
    pub min_players: u8,
    pub max_players: u8,
    pub kids_locked: bool,
    pub readiness: Readiness,
    /// Images are served from it to the box itself.
    pub pool: Arc<TitlePool>,
    /// Textless poster files, asked for by title key; `None` serves the pool's own.
    pub textless: Option<PosterLookup>,
    /// Copies of title images the host cannot read at their own paths; `None` serves only those.
    pub images: Option<ImageLookup>,
    /// Where games append their log lines, also served at `/grand-allee-log`; `None` keeps no log.
    pub log: Option<PathBuf>,
}

impl Default for HostConfig {
    fn default() -> Self {
        Self {
            preferred_port: 8080,
            min_players: 1,
            max_players: ROOM_CAP,
            kids_locked: false,
            // The default pool holds no titles, so no poster game can be started.
            readiness: Readiness { poster: false, poster_kids: false },
            pool: Arc::default(),
            textless: None,
            images: None,
            log: None,
        }
    }
}

/// Hosts the games over `titles`. The one `kids_locked` decides both which
/// titles exist and whether the toggle is locked, so the two cannot disagree.
/// `images` supplies the titles' images from elsewhere when their paths
/// can't be read here. `log` is the file games append their log lines to,
/// across nights.
pub async fn start_game_night(
    titles: Vec<GameTitle>,
    kids_locked: bool,
    preferred_port: u16,
    textless: Option<PosterLookup>,
    images: Option<ImageLookup>,
    log: Option<PathBuf>,
) -> std::io::Result<Host> {
    let pool = Arc::new(TitlePool::new(titles, kids_locked));
    let cfg = HostConfig {
        preferred_port,
        kids_locked,
        readiness: Readiness {
            poster: poster_ready(&pool, &pool.eligible(false)),
            poster_kids: poster_ready(&pool, &pool.eligible(true)),
        },
        pool: pool.clone(),
        textless: textless.clone(),
        images,
        log,
        ..HostConfig::default()
    };
    Host::start(cfg, game_factory(pool, Arc::new(general_pack()), textless)).await
}

/// A running game-night server: HTTP pages, the join QR and the room socket.
pub struct Host {
    port: u16,
    join_url: String,
    events: mpsc::UnboundedSender<Event>,
    shutdown: oneshot::Sender<()>,
    server: JoinHandle<()>,
    room: JoinHandle<()>,
}

impl Host {
    /// Binds the preferred port, or any free one when it is taken.
    pub async fn start(cfg: HostConfig, factory: GameFactory) -> std::io::Result<Host> {
        let listener = match TcpListener::bind(("0.0.0.0", cfg.preferred_port)).await {
            Ok(l) => l,
            Err(_) => TcpListener::bind(("0.0.0.0", 0)).await?,
        };
        let port = listener.local_addr()?.port();
        let ip = lan_ip().map_or_else(|| "127.0.0.1".to_string(), |ip| ip.to_string());
        let join_url = format!("http://{ip}:{port}/");

        let room = Room::new(
            RoomConfig {
                join_url: join_url.clone(),
                min_players: cfg.min_players,
                max_players: cfg.max_players,
                kids_locked: cfg.kids_locked,
                readiness: cfg.readiness,
            },
            factory,
        );
        let (events, rx) = mpsc::unbounded_channel();
        let room = tokio::spawn(room_loop(room, rx, cfg.log.clone()));

        let state = AppState {
            events: events.clone(),
            next_conn: Arc::new(AtomicU64::new(1)),
            join_url: join_url.as_str().into(),
            pool: cfg.pool,
            textless: cfg.textless,
            images: cfg.images,
            log: cfg.log,
        };
        let (shutdown, stopped) = oneshot::channel::<()>();
        let app = router(state).into_make_service_with_connect_info::<SocketAddr>();
        let server = tokio::spawn(async move {
            let served = axum::serve(listener, app)
                .with_graceful_shutdown(async {
                    let _ = stopped.await;
                })
                .await;
            if let Err(e) = served {
                tracing::error!("game server stopped: {e}");
            }
        });
        Ok(Host { port, join_url, events, shutdown, server, room })
    }

    pub fn join_url(&self) -> String {
        self.join_url.clone()
    }

    pub fn tv_url(&self) -> String {
        format!("http://127.0.0.1:{}/tv", self.port)
    }

    pub fn port(&self) -> u16 {
        self.port
    }

    /// Ends the running game. `true` if one was running.
    pub async fn back(&self) -> bool {
        let (tx, rx) = oneshot::channel();
        if self.events.send(Event::Back(tx)).is_err() {
            return false;
        }
        rx.await.unwrap_or(false)
    }

    /// Returns once the listener is closed and the room task has ended.
    /// Open requests get [`STOP_WAIT`] to finish; axum sets no header-read
    /// timeout, so a connection stalled mid-request would otherwise hold the
    /// stop, and every later night waits on this one.
    pub async fn stop(self) {
        let _ = self.events.send(Event::Stop);
        let _ = self.shutdown.send(());
        let mut server = self.server;
        if tokio::time::timeout(STOP_WAIT, &mut server).await.is_err() {
            // The listener is already closed; only the stalled connections remain.
            server.abort();
        }
        let _ = self.room.await;
    }
}

#[cfg(test)]
#[path = "host_tests.rs"]
mod tests;
