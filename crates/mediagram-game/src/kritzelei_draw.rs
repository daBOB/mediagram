//! Kritzelei's drawing ops from the board: which ones join the turn's drawing.

use crate::game::ActionError;
use crate::kritzelei::KritzeleiGame;
use crate::kritzelei_rules::check_op;
use crate::protocol::DrawOp;

impl KritzeleiGame {
    pub(crate) fn draw(&mut self, op: &DrawOp) -> Result<(), ActionError> {
        let line = matches!(op, DrawOp::Line { .. });
        // Every Line and More adds a point, so only an Undo or Clear flood reaches this many ops.
        if self.ops.len() >= self.cfg.max_points || (self.line_refused && matches!(op, DrawOp::More { .. })) {
            return Err(ActionError::Ignored);
        }
        let checked = check_op(op, &self.ops, self.held, &self.cfg);
        if line {
            self.line_refused = checked.is_err();
        }
        match checked {
            Ok(added) => {
                self.held += added;
                self.ops.push(op.clone());
                Ok(())
            }
            Err(ActionError::Ignored) if line => {
                // A line dropped for the cap fills the turn.
                self.held = self.cfg.max_points;
                Err(ActionError::Ignored)
            }
            Err(e) => Err(e),
        }
    }
}

#[cfg(test)]
#[path = "kritzelei_draw_tests.rs"]
mod tests;
