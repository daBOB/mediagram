//! What Kotlin hands the core to export a game night's titles.

/// Which titles a night may show, for a games app that hosts the night itself.
/// Kotlin decides the kids rule and the core only applies it.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct NightTitlesConfig {
    /// Sets whose rating is kid-safe for the profile.
    pub kids_set_ids: Vec<String>,
    /// Sets in the sections the profile sees.
    pub visible_set_ids: Vec<String>,
    /// A kids profile's night: only kid-safe titles leave this app at all,
    /// though the games app's host filters by the same flag again.
    pub kids_locked: bool,
}
