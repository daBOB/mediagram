//! `GET|HEAD|PUT /v1/game/{game}/{name}`: a game's own data, such as
//! Preisfrage's article list and photos. Same rules as the other blobs:
//! reads are open, a PUT is signed, a malformed name is a 404 before the
//! store is asked anything; a newer write replaces the older one.

use axum::Router;
use axum::body::Body;
use axum::extract::{Path, State};
use axum::http::HeaderMap;
use axum::response::Response;
use axum::routing::get;

use super::AppState;
use super::blob_routes::{MAX_IMAGE, MAX_JSON, Target, probe, read, write};
use crate::blobs::{BlobKind, valid_game, valid_game_file};

pub(super) fn routes() -> Router<AppState> {
    Router::new().route(
        "/v1/game/{game}/{name}",
        get(get_game).head(head_game).put(put_game),
    )
}

fn game_target(game: &str, name: &str) -> Option<Target> {
    (valid_game(game) && valid_game_file(name)).then(|| {
        (
            BlobKind::Game,
            format!("{game}/{name}"),
            format!("/v1/game/{game}/{name}"),
        )
    })
}

async fn get_game(
    State(state): State<AppState>,
    Path((game, name)): Path<(String, String)>,
) -> Response {
    read(state, game_target(&game, &name)).await
}

async fn head_game(
    State(state): State<AppState>,
    Path((game, name)): Path<(String, String)>,
) -> Response {
    probe(state, game_target(&game, &name)).await
}

async fn put_game(
    State(state): State<AppState>,
    Path((game, name)): Path<(String, String)>,
    headers: HeaderMap,
    body: Body,
) -> Response {
    let max = if name.ends_with(".json") {
        MAX_JSON
    } else {
        MAX_IMAGE
    };
    write(state, game_target(&game, &name), max, headers, body).await
}
