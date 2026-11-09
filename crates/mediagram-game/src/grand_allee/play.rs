//! Plays one whole game for the simulator: the live game, with a bot in
//! every seat answering its questions after a player's think time, and the
//! clock jumping from one deadline to the next. The move order, the dice and
//! the timings are the live game's own; this file only watches and records.

use rand::rngs::StdRng;
use rand::{Rng, SeedableRng};

use super::bots::{Bot, Style, STYLES};
use super::live::{Bid, GrandAlleeConfig, GrandAlleeGame, Stage};
use super::live_asks::Opt;
use super::rules::Rules;
use crate::game::{Game, GameCtx};
use crate::protocol::{Action, ServerTime};
use crate::room_seats::Player;

/// How long a player takes, in ms: a tap (roll, extra turn), a real decision
/// and a sealed bid, assumed until a test night measures them.
pub struct Think {
    pub tap: ServerTime,
    pub decide: ServerTime,
    pub bid: ServerTime,
}

pub const THINK: Think = Think { tap: 1_500.0, decide: 6_000.0, bid: 10_000.0 };

/// The room ticks the game every 50 ms.
const TICK_MS: ServerTime = 50.0;

#[derive(Debug, Clone)]
pub struct Record {
    pub styles: Vec<Style>,
    pub minutes: f64,
    pub rounds: u32,
    pub last_standing: bool,
    pub winner: usize,
    pub half_leader: Option<usize>,
    pub owned_at_half: usize,
    /// Longest stretch, in seconds, any player waited between two of their own decisions.
    pub max_wait: f64,
    pub debt_auctions: u32,
    pub forced: u32,
    pub bankrupt: u32,
}

/// Plays a game for `players` seats with styles drawn from `seed`.
pub fn play(seed: u64, players: usize, rules: Rules) -> Record {
    let mut rng = StdRng::seed_from_u64(seed);
    let bots: Vec<Bot> = (0..players).map(|_| Bot { style: STYLES[rng.random_range(0..STYLES.len())], house: false }).collect();
    // every bot is at the table, so the house never decides for one
    let mut p: Vec<Player> = (0..players).map(|i| Player::for_test(i as u8, &format!("Bot {}", i + 1), true)).collect();
    let cfg = GrandAlleeConfig { rules, seed: Some(rng.random()), ..GrandAlleeConfig::default() };
    let mut g = GrandAlleeGame::new(cfg, &p, 0.0);
    let mut rec = Record {
        styles: bots.iter().map(|b| b.style).collect(),
        minutes: 0.0, rounds: 0, last_standing: false, winner: 0, half_leader: None,
        owned_at_half: 0, max_wait: 0.0, debt_auctions: 0, forced: 0, bankrupt: 0,
    };
    // when each seat last finished deciding
    let mut last = vec![0.0; players];
    let mut now: ServerTime = 0.0;
    while !g.is_finished() {
        match &g.stage {
            Stage::Ask { ask, since, .. } => {
                let (seat, since) = (g.turn, *since);
                let index = bots[seat].pick(&g.t, seat, ask);
                let opt = ask.opts[index];
                rec.forced += u32::from(matches!(opt, Opt::Force(_)));
                rec.rounds = g.round;
                now = since + if matches!(opt, Opt::Roll | Opt::EndTurn) { THINK.tap } else { THINK.decide };
                decided(&mut rec, &mut last, seat, since, now);
                let (id, action) = (g.ids[seat], Action::Choose { index: index as u8, at: now });
                g.on_action(&mut GameCtx { now, players: &mut p }, id, &action).expect("a bot picks an option on offer");
            }
            Stage::Auction(a) if a.bids.iter().any(|&(_, answer)| answer == Bid::Open) => {
                let (since, hotel, bidders): (_, _, Vec<usize>) = (a.since, a.hotel, a.bids.iter().map(|&(b, _)| b).collect());
                rec.debt_auctions += 1;
                // everyone bids on their phone at once
                now = since + THINK.bid;
                for b in bidders {
                    decided(&mut rec, &mut last, b, since, now);
                    let action = Action::Bid { amount: bots[b].bid(&g.t, b, hotel).unwrap_or(0), at: now };
                    let id = g.ids[b];
                    g.on_action(&mut GameCtx { now, players: &mut p }, id, &action).expect("a bot bids what it has");
                }
            }
            _ => {
                let mut ctx = GameCtx { now, players: &mut p };
                let due = g.due(&ctx).expect("a game in play has a next deadline");
                now = (now.max(due) / TICK_MS).ceil() * TICK_MS;
                ctx.now = now;
                g.on_tick(&mut ctx);
            }
        }
        if rec.half_leader.is_none() && g.round > g.rounds / 2 {
            rec.half_leader = g.standings().first().copied();
            rec.owned_at_half = g.t.estates.iter().filter(|e| e.owner.is_some()).count();
        }
    }
    rec.last_standing = g.t.active() == 1;
    rec.winner = g.standings()[0];
    rec.minutes = now / 60_000.0;
    rec.bankrupt = g.gone.len() as u32;
    rec
}

/// `seat` decided from `since` until `done`; the wait since its last decision is measured.
fn decided(rec: &mut Record, last: &mut [ServerTime], seat: usize, since: ServerTime, done: ServerTime) {
    rec.max_wait = rec.max_wait.max((since - last[seat]) / 1000.0);
    last[seat] = done;
}
