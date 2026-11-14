//! Kritzelei's turn order and the checks a drawing op from the board must pass.

use crate::game::ActionError;
use crate::kritzelei_config::KritzeleiConfig;
use crate::protocol::{GameLength, PlayerId};
use crate::protocol_board::DrawOp;
use crate::Player;

const UNREADABLE: &str = "Das Zeichenbrett hat etwas Unlesbares geschickt.";
/// Palette indices 0..=4 are inks, 5 is the eraser.
const COLOURS: u8 = 6;
const WIDTHS: u8 = 3;
const COORD_MAX: u16 = 10_000;

/// Connected players at the start, in seat order: twice round the table when fewer than
/// `two_rounds_below`, else once, and that scaled by the lobby's length.
pub(crate) fn turn_order(players: &[Player], two_rounds_below: usize, length: GameLength) -> Vec<PlayerId> {
    let once: Vec<PlayerId> = players.iter().filter(|p| p.connected()).map(|p| p.id).collect();
    once.repeat(length.scale(if once.len() < two_rounds_below { 2 } else { 1 }))
}

fn readable(points: &[u16], cfg: &KritzeleiConfig) -> bool {
    !points.is_empty() && points.len().is_multiple_of(2) && points.len() <= cfg.max_batch && points.iter().all(|&v| v <= COORD_MAX)
}

/// Points `op` adds, given the turn's `ops` so far and the `held` point count.
/// Malformed is refused; over the cap, or `More` without a line before, is dropped.
pub(crate) fn check_op(op: &DrawOp, ops: &[DrawOp], held: usize, cfg: &KritzeleiConfig) -> Result<usize, ActionError> {
    let points = match op {
        DrawOp::Line { color, width, points } => {
            if *color >= COLOURS || *width >= WIDTHS || !readable(points, cfg) {
                return Err(ActionError::NotAllowed(UNREADABLE));
            }
            points
        }
        DrawOp::More { points } => {
            if !readable(points, cfg) {
                return Err(ActionError::NotAllowed(UNREADABLE));
            }
            if !matches!(ops.last(), Some(DrawOp::Line { .. } | DrawOp::More { .. })) {
                return Err(ActionError::Ignored);
            }
            points
        }
        DrawOp::Undo | DrawOp::Clear => return Ok(0),
    };
    let added = points.len() / 2;
    if held + added > cfg.max_points { Err(ActionError::Ignored) } else { Ok(added) }
}

#[cfg(test)]
#[path = "kritzelei_rules_tests.rs"]
mod tests;
