//! Spieltisch's native core: the game night host behind a uniffi surface.

uniffi::setup_scaffolding!();

mod media_images;
mod night;

pub use media_images::{MediaImageKind, MediaImages};
pub use night::{NightError, NightUrls, SpieltischConfig, night_back, start_night, stop_night};
