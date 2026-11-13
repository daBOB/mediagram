//! Flüchtig's rules, the timings of its taps, and the test hooks.

use crate::fluechtig_rules::Rules;

#[derive(Debug, Clone)]
pub struct FluechtigConfig {
    /// `None` plays the big city with the rules for the table's figure count
    /// (`Rules::big_for`); tests set other rules.
    pub rules: Option<Rules>,
    /// A tap counts this long before its question opened by the host's clock:
    /// the phone opens its buttons at its own estimate of that moment.
    pub early_ms: f64,
    /// A question opened by a tap opens this much later, so the second tap of
    /// a double tap cannot answer it.
    pub chain_ms: f64,
    /// How long each rules card stays on the TV before round 1; 0 starts the chase at once.
    pub intro_ms: f64,
    /// Fixes who runs and where everyone starts, for tests.
    pub seed: Option<u64>,
}

impl Default for FluechtigConfig {
    fn default() -> Self {
        Self { rules: None, early_ms: 150.0, chain_ms: 400.0, intro_ms: 10_000.0, seed: None }
    }
}
