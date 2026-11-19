//! The original's money and counts, and how the stretched board scales them.

use super::board::MORE;

/// The original's numbers (2000 rulebook unless noted), before `more` scales
/// the one-off ones (`Rules::once`).
#[derive(Debug, Clone)]
pub struct Rules {
    /// Board stretch; one-off amounts and token counts scale with it.
    pub more: f64,
    pub start_cash: i64,
    /// Studium: two loans' worth, paid before the first move.
    pub tuition: i64,
    pub loan: i64,
    /// Per loan, due when it is repaid at retirement.
    pub interest: i64,
    pub car_insurance: i64,
    pub stock_price: i64,
    /// Per spin of the stock's number, any player's spin.
    pub stock_payout: i64,
    pub police_fine: i64,
    pub art_sale: i64,
    pub repair_fee: i64,
    pub commission: i64,
    /// Tokens set aside for the richest at the Villa am See.
    pub estate_tokens: usize,
    /// How often the spinner stops between two numbers, per mille (the clone's
    /// wheel: 164; a tuning knob, measured with bots).
    pub gap_per_mille: u32,
}

impl Default for Rules {
    fn default() -> Self {
        Self {
            more: MORE,
            start_cash: 10_000,
            tuition: 40_000,
            loan: 20_000,
            interest: 5_000,
            car_insurance: 10_000,
            stock_price: 50_000,
            stock_payout: 10_000,
            police_fine: 5_000,
            art_sale: 10_000,
            repair_fee: 50_000,
            commission: 5_000,
            estate_tokens: 4,
            gap_per_mille: 50,
        }
    }
}

impl Rules {
    /// A one-off amount on the stretched board: the longer path pays and
    /// charges `more` times as often, so what happens once grows with it.
    pub fn once(&self, amount: i64) -> i64 {
        (amount as f64 * self.more / 1_000.0).round() as i64 * 1_000
    }
    pub fn once_count(&self, n: usize) -> usize {
        (n as f64 * self.more).round() as usize
    }
}
