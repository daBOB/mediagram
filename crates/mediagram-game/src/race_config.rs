//! Timings of the race game and the test hooks.

#[derive(Debug, Clone)]
pub struct RaceConfig {
    /// How long the die flies on the TV.
    pub roll_ms: f64,
    /// Pause after the die lands before a single move plays itself.
    pub show_ms: f64,
    /// Pause after the die lands when no move is possible.
    pub pass_ms: f64,
    pub hop_ms: f64,
    /// A move starts this long after it is decided, so the TV hears of it in
    /// time to land the first hop's knock through the box's slow audio output
    /// (~370 ms from scheduling to speaker).
    pub lead_ms: f64,
    /// Extra time for a captured piece to fly home.
    pub capture_ms: f64,
    /// A pick may arrive this long before the die lands by the host's clock:
    /// the phone opens its buttons at its own estimate of that moment.
    pub early_ms: f64,
    pub seed: Option<u64>,
    /// Rolls used before the die itself, for tests.
    pub rolls: Vec<u8>,
}

impl Default for RaceConfig {
    fn default() -> Self {
        Self {
            roll_ms: 1150.0,
            show_ms: 800.0,
            pass_ms: 1600.0,
            hop_ms: 280.0,
            lead_ms: 200.0,
            capture_ms: 700.0,
            early_ms: 300.0,
            seed: None,
            rolls: Vec::new(),
        }
    }
}
