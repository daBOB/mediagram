//! The decision log a test night is measured by: what each question asked,
//! what was chosen, how long it took and who answered, by seat and never by
//! name. Entries stay data until the host drains them; the simulator never does.

use serde::Serialize;
use serde_json::Value;

use super::live::{Auction, Bid, GrandAlleeGame};
use super::live_asks::{Ask, Opt};
use crate::game::GameCtx;
use crate::protocol::ServerTime;

/// Who answered a question or a debt auction.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
pub(crate) enum By {
    Player,
    /// The question's clock ran out on a phone at the table.
    Clock,
    /// The house, for an absent player or a mover whose clock already ran out this turn.
    House,
}

/// One line of the log; times are the host's clock, rounded to whole ms.
#[derive(Serialize)]
#[serde(tag = "ev", rename_all = "lowercase")]
enum Entry {
    Start { players: usize, rounds: u32 },
    Decide { seat: usize, round: u32, ask: &'static str, chose: &'static str, at: i64, think_ms: i64, by: By },
    Bid { seat: usize, at: i64, think_ms: i64, by: By, pass: bool },
    End { rounds: u32, minutes: f64, out: Vec<usize>, end: &'static str },
}

pub(crate) struct Log {
    /// When the game began, for the minutes it took.
    began: ServerTime,
    entries: Vec<Entry>,
}

impl Log {
    pub(crate) fn new(players: usize, rounds: u32, now: ServerTime) -> Self {
        Self { began: now, entries: vec![Entry::Start { players, rounds }] }
    }

    /// The entries since the last call, as JSON, so only a host that keeps them pays for it.
    pub(crate) fn take(&mut self) -> Vec<Value> {
        std::mem::take(&mut self.entries).iter().map(|e| serde_json::to_value(e).expect("log entries always serialize")).collect()
    }
}

fn ms(t: ServerTime) -> i64 {
    t.round() as i64
}

impl GrandAlleeGame {
    /// The mover answered a question of `kind`, opened at `since`, with `opt` at `now`.
    pub(crate) fn log_decision(&mut self, kind: &'static str, since: ServerTime, opt: Opt, now: ServerTime, by: By) {
        let (seat, round, think_ms) = (self.turn, self.round, ms((now - since).max(0.0)));
        self.log.entries.push(Entry::Decide { seat, round, ask: kind, chose: opt.kind(), at: ms(now), think_ms, by });
    }

    /// Who answered for a silent mover: the house when it came before the clock, else the clock.
    pub(crate) fn silent_by(&self, ctx: &GameCtx, since: ServerTime) -> By {
        if self.house_at(ctx, since) < since + self.cfg.ask_ms { By::House } else { By::Clock }
    }

    pub(crate) fn log_bid(&mut self, seat: usize, since: ServerTime, now: ServerTime, by: By, pass: bool) {
        self.log.entries.push(Entry::Bid { seat, at: ms(now), think_ms: ms((now - since).max(0.0)), by, pass });
    }

    /// A bidder still silent at the close passed: the house's doing when they were away, else the clock's.
    pub(crate) fn log_silent_bidders(&mut self, ctx: &GameCtx, a: &Auction) {
        for &(seat, _) in a.bids.iter().filter(|&&(_, answer)| answer == Bid::Open) {
            let by = if ctx.house_at(self.ids[seat], a.since).is_some() { By::House } else { By::Clock };
            self.log_bid(seat, a.since, ctx.now, by, true);
        }
    }

    /// The game is over after `rounds` rounds.
    pub(crate) fn log_end(&mut self, now: ServerTime, rounds: u32) {
        let end = if self.t.active() <= 1 { "bankrupt" } else { "rounds" };
        let minutes = ((now - self.log.began) / 6_000.0).round() / 10.0;
        self.log.entries.push(Entry::End { rounds, minutes, out: self.gone.clone(), end });
    }
}

impl Ask {
    /// What the question asks, told by what it offers, so think times can be compared per kind.
    pub(crate) fn kind(&self) -> &'static str {
        let offers = |f: fn(&Opt) -> bool| self.opts.iter().any(f);
        match self.opts.first() {
            Some(Opt::Roll) if offers(|o| matches!(o, Opt::Leisure(_))) => "leisure",
            Some(Opt::Roll) => "roll",
            Some(Opt::Buy(_) | Opt::Force(_)) => "plot",
            Some(Opt::Build { .. }) => "build",
            Some(Opt::Hotel { free: false, .. }) => "rathaus",
            Some(Opt::Hotel { free: true, .. }) => "entrance",
            Some(Opt::Place { .. }) => "field",
            Some(Opt::FreeStage(_)) => "free-build",
            Some(Opt::Sell(_)) if offers(|o| matches!(o, Opt::Raise { .. })) => "raise",
            Some(Opt::Sell(_)) => "sell",
            _ => "other",
        }
    }
}

impl Opt {
    fn kind(self) -> &'static str {
        match self {
            Opt::Roll => "roll",
            Opt::EndTurn => "end-turn",
            Opt::Leisure(_) => "leisure",
            Opt::Buy(_) => "buy",
            Opt::Force(_) => "force",
            Opt::Build { .. } => "build",
            Opt::Hotel { .. } => "hotel",
            Opt::Place { .. } => "place",
            Opt::FreeStage(_) => "free-stage",
            Opt::Sell(_) => "sell",
            Opt::Raise { .. } => "raise",
            Opt::Skip => "skip",
            Opt::Done => "done",
            Opt::Back => "back",
        }
    }
}
