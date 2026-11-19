//! Plays many Lebensweg games with bots on the original board (original money)
//! and on ours (stretched by --more), and prints how often each route, habit
//! and career wins against its fair share (1.00 = fair), for tuning the numbers.
//!
//! cargo run --release -p mediagram-game --bin lebensweg-sim -- \
//!   [--games 2000] [--players 2,4,6] [--more 1.3] [--gap 50]
//!
//! --fields prints the stretched board's fields in order instead (for writing their texts).

use mediagram_game::lebensweg::board::Board;
use mediagram_game::lebensweg::cards::CAREERS;
use mediagram_game::lebensweg::spaces::space;
use mediagram_game::lebensweg::play::{play, Record};
use mediagram_game::lebensweg::rules::{Home, Rules};

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let flag = |name: &str| args.iter().position(|a| a == name).and_then(|i| args.get(i + 1)).cloned();
    let games: u64 = flag("--games").map_or(2000, |v| v.parse().expect("--games N"));
    let players: Vec<usize> = flag("--players").unwrap_or("2,4,6".into()).split(',').map(|p| p.parse().expect("--players 2,4,6")).collect();
    let gap: u32 = flag("--gap").map_or(Rules::default().gap_per_mille, |v| v.parse().expect("--gap per mille"));
    let more: f64 = flag("--more").map_or(Rules::default().more, |v| v.parse().expect("--more 1.3"));
    if args.iter().any(|a| a == "--fields") {
        return print_fields(more);
    }
    let boards = [("original", Rules { more: 1.0, gap_per_mille: gap, ..Rules::default() }), ("ours", Rules { more, gap_per_mille: gap, ..Rules::default() })];
    println!("{games} games per row; spinner gap {gap} ‰; ours stretched × {more}\n");
    println!("| Board | Players | Minutes p50 (p10–p90) | Spins / player | Studium / Ausbildung | Familie / Abenteuer | Riskant / Sicher | Villa / Landhaus | Insured / not | Stock / not | Winner worth p50 | Ends in debt | Pile empty at |");
    println!("|---|---|---|---|---|---|---|---|---|---|---|---|---|");
    let mut careers = Vec::new();
    for (name, rules) in &boards {
        for &n in &players {
            let recs: Vec<Record> = (0..games).map(|i| play(n as u64 * 1_000_000 + i, n, rules.clone())).collect();
            println!("{}", row(name, n, &recs));
            if n == 4 {
                careers.push((name, career_row(&recs)));
            }
        }
    }
    println!("\nCareers retired from, 4 players (win vs fair): {}", CAREERS.iter().map(|c| c.name).collect::<Vec<_>>().join(" / "));
    for (name, r) in careers {
        println!("- {name}: {r}");
    }
}

/// Wins × players ÷ seats for the seats that `pick` selects: 1.00 is a fair share.
fn fair(recs: &[Record], pick: &dyn Fn(&Record, usize) -> bool) -> String {
    let (mut seats, mut wins) = (0usize, 0usize);
    for r in recs {
        let n = r.worth.len();
        for p in 0..n {
            if pick(r, p) {
                seats += 1;
                if r.winner == p {
                    wins += n;
                }
            }
        }
    }
    if seats == 0 { "–".into() } else { format!("{:.2}", wins as f64 / seats as f64) }
}

fn pair(recs: &[Record], pick: &dyn Fn(&Record, usize) -> Option<bool>) -> String {
    format!("{} / {}", fair(recs, &|r, p| pick(r, p) == Some(true)), fair(recs, &|r, p| pick(r, p) == Some(false)))
}

fn quantile(mut v: Vec<f64>, q: f64) -> f64 {
    v.sort_by(f64::total_cmp);
    v[((v.len() - 1) as f64 * q).round() as usize]
}

fn row(name: &str, n: usize, recs: &[Record]) -> String {
    let minutes: Vec<f64> = recs.iter().map(|r| r.seconds / 60.0).collect();
    let fork = |i: usize| move |r: &Record, p: usize| r.forks[p][i].map(|b| b == 0);
    let seats = (recs.len() * n) as f64;
    let debt = recs.iter().map(|r| r.debt).sum::<usize>() as f64 / seats;
    let pile: Vec<f64> = recs.iter().filter_map(|r| r.pile_empty_at).collect();
    let winners: Vec<f64> = recs.iter().map(|r| r.worth[r.winner] as f64 / 1000.0).collect();
    format!(
        "| {name} | {n} | {:.0} ({:.0}–{:.0}) | {:.0} | {} | {} | {} | {} | {} | {} | {:.0}k | {:.1} % | {} |",
        quantile(minutes.clone(), 0.5), quantile(minutes.clone(), 0.1), quantile(minutes, 0.9),
        recs.iter().map(|r| r.spins as f64).sum::<f64>() / seats,
        pair(recs, &fork(0)), pair(recs, &fork(1)), pair(recs, &fork(2)),
        pair(recs, &|r, p| r.home[p].map(|h| h == Home::Villa)),
        pair(recs, &|r, p| Some(r.insure[p])), pair(recs, &|r, p| Some(r.stock[p])),
        quantile(winners, 0.5), 100.0 * debt,
        if pile.is_empty() { "never".into() } else { format!("{:.0} % ({:.0} % of games)", 100.0 * quantile(pile.clone(), 0.5), 100.0 * pile.len() as f64 / recs.len() as f64) },
    )
}

fn career_row(recs: &[Record]) -> String {
    (0..CAREERS.len()).map(|c| fair(recs, &|r, p| r.career[p] == Some(c))).collect::<Vec<_>>().join(" / ")
}

/// One row per field: the original space it repeats, and which copy of it this is.
fn print_fields(more: f64) {
    let board = Board::new(more);
    println!("| # | Stretch | Age | Space | Copy | Effect | Text |\n|---|---|---|---|---|---|---|");
    for (i, f) in board.fields.iter().enumerate() {
        let (effect, text) = f.space.map_or(("Stop".into(), f.name), |s| (format!("{:?}", space(s).0), space(s).1));
        let copy = f.space.map_or(1, |s| board.fields[..=i].iter().filter(|g| g.space == Some(s)).count());
        let cell = |v: Option<u8>| v.map_or(String::new(), |v| v.to_string());
        println!("| {i} | {} | {} | {} | {copy} | {effect} | {text} |", f.name, cell(f.age), cell(f.space));
    }
}
