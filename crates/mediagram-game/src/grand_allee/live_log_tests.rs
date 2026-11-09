use serde_json::{Value, json};

use super::live::Stage;
use super::live_debt_tests::{ben_owes_cem, bid};
use super::live_tests::{game_with, options, pick, until, ANA, BEN};
use super::rules::Rules;
use crate::game::Game;

/// The decide lines as (seat, ask, chose, by).
fn decisions(lines: &[Value]) -> Vec<(u64, &str, &str, &str)> {
    lines
        .iter()
        .filter(|l| l["ev"] == "decide")
        .map(|l| (l["seat"].as_u64().unwrap(), l["ask"].as_str().unwrap(), l["chose"].as_str().unwrap(), l["by"].as_str().unwrap()))
        .collect()
}

fn think_ms(lines: &[Value], ev: &str) -> Vec<i64> {
    lines.iter().filter(|l| l["ev"] == ev).map(|l| l["think_ms"].as_i64().unwrap()).collect()
}

#[test]
fn a_short_game_logs_its_start_every_answer_with_who_gave_it_and_its_end() {
    let (mut g, mut p) = game_with(3, &[4, 1, 5], Rules { min_rounds: 1, max_rounds: 1, ..Rules::default() });
    // Ben starts just before the plot on field 9; Cem is away
    g.t.seats[1].field = 8;
    p[2].conn = None;
    let now = pick(&mut g, &mut p, ANA, "Würfeln", 10.0);
    let buy = options(&g, &p, ANA, now).into_iter().find(|l| l.starts_with("Lagune")).expect("field 4 borders the Lagune");
    let now = pick(&mut g, &mut p, ANA, &buy, now);
    // Ben lets his roll's clock run out; the house goes on for him, and for Cem
    let now = until(&mut g, &mut p, now, |g| g.is_finished());
    let lines = g.take_log();
    assert_eq!(lines[0], json!({"ev": "start", "players": 3, "rounds": 1}));
    assert_eq!(
        decisions(&lines),
        [
            (0, "roll", "roll", "player"),
            (0, "plot", "buy", "player"),
            (1, "roll", "roll", "clock"),
            (1, "plot", "buy", "house"),
            (2, "roll", "roll", "house"),
        ]
    );
    let think = think_ms(&lines, "decide");
    assert!((20_000..=20_050).contains(&think[2]), "Ben's clock ran out: {think:?}");
    assert!(think[3] <= 50, "after a clock ran out the house answers at once: {think:?}");
    assert!((1_500..=1_550).contains(&think[4]), "the house waits the grace for an away player: {think:?}");
    let minutes = (now / 6_000.0).round() / 10.0;
    assert_eq!(lines.last(), Some(&json!({"ev": "end", "rounds": 1, "minutes": minutes, "out": [], "end": "rounds"})));
    assert!(g.take_log().is_empty(), "a drained log starts empty");
}

#[test]
fn every_debt_auction_bidder_is_logged_and_one_still_silent_at_the_close_passed() {
    for (cem_here, by) in [(true, "clock"), (false, "house")] {
        let (mut g, mut p) = ben_owes_cem(&[3]);
        if !cem_here {
            p[2].conn = None;
        }
        let now = pick(&mut g, &mut p, BEN, "Würfeln", 10.0);
        let now = pick(&mut g, &mut p, BEN, "Hacienda Sol versteigern · Wert 1.000 Taler", now);
        let now = bid(&mut g, &mut p, ANA, 500, now);
        until(&mut g, &mut p, now, |g| !matches!(g.stage, Stage::Auction(_)));
        let lines = g.take_log();
        assert_eq!(decisions(&lines), [(1, "roll", "roll", "player"), (1, "sell", "sell", "player")]);
        let bids: Vec<(u64, &str, bool)> = lines
            .iter()
            .filter(|l| l["ev"] == "bid")
            .map(|l| (l["seat"].as_u64().unwrap(), l["by"].as_str().unwrap(), l["pass"].as_bool().unwrap()))
            .collect();
        assert_eq!(bids, [(0, "player", false), (2, by, true)], "Cem here: {cem_here}");
        if cem_here {
            let think = think_ms(&lines, "bid");
            assert!((15_000..=15_050).contains(&think[1]), "the auction's clock ran out on Cem: {think:?}");
        }
    }
}
