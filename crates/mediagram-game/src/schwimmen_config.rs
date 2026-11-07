//! Timings of Schwimmen.

#[derive(Debug, Clone)]
pub struct SchwimmenConfig {
    /// An absent player's move happens after this.
    pub auto_ms: f64,
    /// How long the banner keeps naming the last happening.
    pub banner_ms: f64,
    /// How long the revealed hands stay before the next round is dealt.
    pub reveal_ms: f64,
    pub seed: Option<u64>,
}

impl Default for SchwimmenConfig {
    fn default() -> Self {
        Self { auto_ms: 1500.0, banner_ms: 2600.0, reveal_ms: 8000.0, seed: None }
    }
}
