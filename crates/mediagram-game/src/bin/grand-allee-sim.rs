//! Plays many Grand Allee games with bots and prints the design's acceptance
//! metrics per player count, for tuning the rules' numbers.
//!
//! cargo run --release -p mediagram-game --bin grand-allee-sim -- \
//!   [--games 1000] [--players 2,4,6,8] [--start-cash 12000] [--lap-bonus 2000]
//!   [--round-turns 120] [--min-rounds 14] [--max-rounds 40] [--rent-pct 100]

use mediagram_game::grand_allee::bots::STYLES;
use mediagram_game::grand_allee::play::{play, Record};
use mediagram_game::grand_allee::rules::Rules;

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let flag = |name: &str| args.iter().position(|a| a == name).and_then(|i| args.get(i + 1)).cloned();
    let num = |name: &str, default: i64| flag(name).map_or(default, |v| v.parse().unwrap_or_else(|_| panic!("{name} needs a number")));
    let games = num("--games", 1000) as u64;
    let players: Vec<usize> = flag("--players").unwrap_or("2,4,6,8".into()).split(',').map(|p| p.parse().expect("--players 2,4,6,8")).collect();
    let d = Rules::default();
    let rules = Rules {
        start_cash: num("--start-cash", d.start_cash.into()) as i32,
        lap_bonus: num("--lap-bonus", d.lap_bonus.into()) as i32,
        round_turns: num("--round-turns", d.round_turns.into()) as u32,
        min_rounds: num("--min-rounds", d.min_rounds.into()) as u32,
        max_rounds: num("--max-rounds", d.max_rounds.into()) as u32,
        rent_pct: num("--rent-pct", d.rent_pct.into()) as i32,
        ..d
    };
    println!("{games} games per player count; {rules:?}\n");
    println!("| Players | Minutes p50 (p10–p90) | Rounds | Last standing | All owned at ½ | Hotels owned at ½ | ½-leader wins | Seat 1 wins | Max wait p50/p95 s | Debt auctions | Forced buys | Out | Win vs fair: cautious/balanced/bold |");
    println!("|---|---|---|---|---|---|---|---|---|---|---|---|---|");
    for &n in &players {
        let recs: Vec<Record> = (0..games).map(|i| play(n as u64 * 1_000_000 + i, n, rules.clone())).collect();
        println!("{}", row(n, &recs));
    }
}

fn pct(part: usize, all: usize) -> String {
    format!("{:.0} %", 100.0 * part as f64 / all.max(1) as f64)
}

fn quantile(mut v: Vec<f64>, q: f64) -> f64 {
    v.sort_by(f64::total_cmp);
    v[((v.len() - 1) as f64 * q).round() as usize]
}

fn row(n: usize, recs: &[Record]) -> String {
    let g = recs.len();
    let mean = |f: &dyn Fn(&Record) -> f64| recs.iter().map(f).sum::<f64>() / g as f64;
    let minutes: Vec<f64> = recs.iter().map(|r| r.minutes).collect();
    let waits: Vec<f64> = recs.iter().map(|r| r.max_wait).collect();
    let count = |f: &dyn Fn(&Record) -> bool| recs.iter().filter(|r| f(r)).count();
    let styles = STYLES
        .iter()
        .map(|&s| {
            let seats = recs.iter().map(|r| r.styles.iter().filter(|&&x| x == s).count()).sum::<usize>();
            let wins = count(&|r: &Record| r.styles[r.winner] == s);
            format!("{:.2}", wins as f64 * n as f64 / seats.max(1) as f64)
        })
        .collect::<Vec<_>>()
        .join("/");
    format!(
        "| {n} | {:.0} ({:.0}–{:.0}) | {:.1} | {} | {} | {:.1} | {} | {} | {:.0}/{:.0} | {:.1} | {:.1} | {:.1} | {styles} |",
        quantile(minutes.clone(), 0.5),
        quantile(minutes.clone(), 0.1),
        quantile(minutes, 0.9),
        mean(&|r| f64::from(r.rounds)),
        pct(count(&|r| r.last_standing), g),
        pct(count(&|r| r.owned_at_half == 10), g),
        mean(&|r| r.owned_at_half as f64),
        pct(count(&|r| r.half_leader == Some(r.winner)), g),
        pct(count(&|r| r.winner == 0), g),
        quantile(waits.clone(), 0.5),
        quantile(waits, 0.95),
        mean(&|r| f64::from(r.debt_auctions)),
        mean(&|r| f64::from(r.forced)),
        mean(&|r| f64::from(r.bankrupt)),
    )
}
