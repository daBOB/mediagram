//! What Kotlin hands a game night and what it gets back.

/// How a game night is set up; Kotlin decides the kids rule and the core
/// only applies it.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct GameNightConfig {
    /// Sets whose rating is kid-safe for the profile starting the night.
    pub kids_set_ids: Vec<String>,
    pub kids_locked: bool,
    pub tmdb_key: Option<String>,
}

/// Where the box shows the game and where phones join it.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct GameNightUrls {
    pub tv_url: String,
    pub join_url: String,
}
