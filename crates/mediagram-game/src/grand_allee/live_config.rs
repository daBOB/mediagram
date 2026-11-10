//! Grand Allee's timings and table rules for one live game; tests and the
//! simulator change them, the room plays the defaults.

use super::rules::Rules;

/// What a live game is set up with: its rules, the TV's animation times and the clocks.
#[derive(Debug, Clone)]
pub struct GrandAlleeConfig {
    pub rules: Rules,
    /// How long a die flies on the TV, and how long it shows after landing.
    pub roll_ms: f64,
    pub show_ms: f64,
    pub hop_ms: f64,
    /// A walk starts this long after it is decided, so the TV can land the first hop.
    pub lead_ms: f64,
    /// How long the outcome of a move or an auction stays up.
    pub note_ms: f64,
    /// A pick may arrive this long before its prompt opened by the host's clock.
    pub early_ms: f64,
    /// A question that follows straight from a tap opens this long after it,
    /// so the second tap of a double tap cannot answer it.
    pub chain_ms: f64,
    /// A decision's clock; then the house decides.
    pub ask_ms: f64,
    pub auction_ms: f64,
    /// How long each of the three rules cards shows before the first roll; 0 leaves them out.
    pub intro_ms: f64,
    pub seed: Option<u64>,
    /// Dice used before the dice themselves, for tests.
    pub rolls: Vec<u8>,
}

impl Default for GrandAlleeConfig {
    fn default() -> Self {
        Self {
            rules: Rules::default(),
            roll_ms: 1150.0,
            show_ms: 900.0,
            hop_ms: 280.0,
            lead_ms: 200.0,
            note_ms: 1400.0,
            early_ms: 150.0,
            chain_ms: 400.0,
            ask_ms: 20_000.0,
            auction_ms: 15_000.0,
            intro_ms: 10_000.0,
            seed: None,
            rolls: Vec::new(),
        }
    }
}
