use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::Arc;
use std::sync::atomic::{AtomicU64, Ordering};

use axum::Router;
use axum::extract::ws::{Message, WebSocket, WebSocketUpgrade};
use axum::extract::{ConnectInfo, Path, State};
use axum::http::{StatusCode, Uri, header};
use axum::response::{Html, IntoResponse, Response};
use axum::routing::get;
use futures::{SinkExt, StreamExt};
use qrcode::QrCode;
use qrcode::render::svg;
use rust_embed::RustEmbed;
use tokio::sync::mpsc;

use crate::article_photos::article_photo;
use crate::game_log::serve_log;
use crate::image_files::{ImageLookup, content_type, title_image};
use crate::poster_lookup::{PosterLookup, textless_file};
use crate::room_loop::Event;
use crate::titles::{ImageKind, TitlePool};

const MAX_FRAME_BYTES: usize = 16 * 1024;

#[derive(RustEmbed)]
#[folder = "client/dist"]
#[allow_missing = true]
struct Assets;

#[derive(Clone)]
pub(crate) struct AppState {
    pub(crate) events: mpsc::UnboundedSender<Event>,
    pub(crate) next_conn: Arc<AtomicU64>,
    pub(crate) join_url: Arc<str>,
    pub(crate) pool: Arc<TitlePool>,
    pub(crate) textless: Option<PosterLookup>,
    pub(crate) images: Option<ImageLookup>,
    pub(crate) articles: Arc<crate::ArticleList>,
    pub(crate) log: Option<PathBuf>,
}

pub(crate) fn router(state: AppState) -> Router {
    Router::new()
        .route("/ws", get(ws_handler))
        .route("/qr.svg", get(qr_svg))
        .route("/img/{i}/{which}", get(image))
        .route("/img/article/{asin}", get(article_photo))
        .route("/grand-allee-log", get(serve_log))
        .fallback(static_handler)
        .with_state(state)
}

async fn ws_handler(
    ws: WebSocketUpgrade,
    ConnectInfo(peer): ConnectInfo<SocketAddr>,
    State(state): State<AppState>,
) -> Response {
    let local = peer.ip().is_loopback();
    ws.max_message_size(MAX_FRAME_BYTES)
        .on_upgrade(move |socket| client(socket, state, local))
}

async fn client(socket: WebSocket, state: AppState, local: bool) {
    let id = state.next_conn.fetch_add(1, Ordering::Relaxed);
    let (tx, mut outbound) = mpsc::unbounded_channel::<String>();
    if state.events.send(Event::Connect(id, local, tx)).is_err() {
        return;
    }
    let (mut sink, mut stream) = socket.split();

    let mut writer = tokio::spawn(async move {
        while let Some(text) = outbound.recv().await {
            if sink.send(Message::Text(text.into())).await.is_err() {
                break;
            }
        }
    });

    let events = state.events.clone();
    let reader = async move {
        while let Some(Ok(msg)) = stream.next().await {
            match msg {
                Message::Text(text) => {
                    if events.send(Event::Text(id, text.as_str().to_owned())).is_err() {
                        break;
                    }
                }
                Message::Close(_) => break,
                _ => {}
            }
        }
    };
    // The writer ends when the room task does, which closes the socket.
    tokio::select! {
        _ = reader => {}
        _ = &mut writer => {}
    }

    let _ = state.events.send(Event::Disconnect(id));
    writer.abort();
    tracing::debug!(conn = id, "disconnected");
}

async fn qr_svg(State(state): State<AppState>) -> Response {
    let Ok(code) = QrCode::new(state.join_url.as_bytes()) else {
        return StatusCode::INTERNAL_SERVER_ERROR.into_response();
    };
    let image = code
        .render::<svg::Color>()
        .min_dimensions(320, 320)
        .quiet_zone(false)
        .dark_color(svg::Color("#0c2a22"))
        .light_color(svg::Color("#fbf8f1"))
        .build();
    (
        [(header::CONTENT_TYPE, "image/svg+xml"), (header::CACHE_CONTROL, "no-cache")],
        image,
    )
        .into_response()
}

/// Phones never get artwork: only the box itself may fetch it.
pub(crate) async fn image(
    State(state): State<AppState>,
    ConnectInfo(peer): ConnectInfo<SocketAddr>,
    Path((i, which)): Path<(usize, String)>,
) -> Response {
    if !peer.ip().is_loopback() {
        return StatusCode::FORBIDDEN.into_response();
    }
    let kind = match which.as_str() {
        "poster" => ImageKind::Poster,
        "still" => ImageKind::Still,
        _ => return StatusCode::NOT_FOUND.into_response(),
    };
    let textless = match (&state.textless, kind, state.pool.key_of(i)) {
        (Some(lookup), ImageKind::Poster, Some(key)) => textless_file(lookup, key).await,
        _ => None,
    };
    let file = match textless {
        Some(path) => tokio::fs::read(&path).await.ok().map(|bytes| (path, bytes)),
        None => title_image(&state.pool, state.images.as_ref(), i, kind).await,
    };
    match file.and_then(|(path, bytes)| Some((content_type(&path, &bytes)?, bytes))) {
        Some((content_type, bytes)) => {
            ([(header::CONTENT_TYPE, content_type), (header::CACHE_CONTROL, "no-store")], bytes).into_response()
        }
        None => StatusCode::NOT_FOUND.into_response(),
    }
}

async fn static_handler(uri: Uri) -> Response {
    let path = uri.path().trim_start_matches('/');
    let path = if path.is_empty() { "index.html" } else { path };

    if let Some(file) = Assets::get(path) {
        let cache = if path.starts_with("assets/") {
            "public, max-age=31536000, immutable"
        } else {
            "no-cache"
        };
        return (
            [
                (header::CONTENT_TYPE, file.metadata.mimetype().to_string()),
                (header::CACHE_CONTROL, cache.into()),
            ],
            file.data,
        )
            .into_response();
    }

    // Client-side routes like /tv fall back to the app shell.
    if !path.contains('.') {
        if let Some(index) = Assets::get("index.html") {
            return (
                [
                    (header::CONTENT_TYPE, "text/html; charset=utf-8"),
                    (header::CACHE_CONTROL, "no-cache"),
                ],
                index.data,
            )
                .into_response();
        }
        return Html(
            "<h1>Client nicht gebaut</h1><p>Im Ordner <code>client/</code> <code>bun install && bun run build</code> ausführen und den Server neu bauen.</p>",
        )
        .into_response();
    }
    StatusCode::NOT_FOUND.into_response()
}

#[cfg(test)]
#[path = "server_tests.rs"]
mod tests;

#[cfg(test)]
#[path = "server_image_tests.rs"]
mod image_tests;
