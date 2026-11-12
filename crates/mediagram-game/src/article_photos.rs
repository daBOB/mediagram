//! `/img/article/{asin}`: an article's photo, which the embedder fetches from
//! the home cache server. Unlike a film's artwork it is served to phones too,
//! since the keypad shows it; only tonight's articles are ever asked for.

use std::net::SocketAddr;

use axum::extract::{ConnectInfo, Path, State};
use axum::http::{StatusCode, header};
use axum::response::{IntoResponse, Response};

use crate::image_files::content_type;
use crate::server::AppState;
use crate::titles::ImageKind;

pub(crate) async fn article_photo(
    State(state): State<AppState>,
    ConnectInfo(_peer): ConnectInfo<SocketAddr>,
    Path(asin): Path<String>,
) -> Response {
    let (Some(lookup), true) = (&state.images, state.articles.holds(&asin)) else {
        return StatusCode::NOT_FOUND.into_response();
    };
    let Some(path) = lookup(asin, ImageKind::Article).await else {
        return StatusCode::NOT_FOUND.into_response();
    };
    let photo = tokio::fs::read(&path)
        .await
        .ok()
        .and_then(|bytes| Some((content_type(&path, &bytes)?, bytes)));
    match photo {
        Some((content_type, bytes)) => (
            [
                (header::CONTENT_TYPE, content_type),
                (header::CACHE_CONTROL, "no-store"),
            ],
            bytes,
        )
            .into_response(),
        None => StatusCode::NOT_FOUND.into_response(),
    }
}
