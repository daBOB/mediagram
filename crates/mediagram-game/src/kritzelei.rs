//! Kritzelei: one player draws a word on the iPad, the TV shows the line as it
//! grows, the room guesses out loud and the drawer taps who got it.

use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::kritzelei_clock::TurnClock;
pub use crate::kritzelei_config::KritzeleiConfig;
use crate::kritzelei_rules::turn_order;
use crate::protocol::{Action, BoardAct, BoardScreen, Buzz, DrawOp, PhoneScreen, PlayerId, ServerTime, TvScene};
use crate::room_seats::Player;

const WHO: &str = "Wer soll das sein?";

#[derive(Debug, Clone, Copy, PartialEq)]
#[cfg_attr(not(test), allow(dead_code))]
pub(crate) enum Stage {
    /// The word stays hidden until the drawer taps "Ich bin dran".
    Handover,
    Drawing,
    /// The who-got-it list is open; the clock stands.
    Picking,
    Reveal { guesser: Option<PlayerId>, ends_at: ServerTime },
    Final,
}

#[cfg_attr(not(test), allow(dead_code))]
pub struct KritzeleiGame {
    pub(crate) cfg: KritzeleiConfig,
    /// Who draws each turn.
    pub(crate) turns: Vec<PlayerId>,
    /// The word of each turn, by the same index.
    pub(crate) words: Vec<&'static str>,
    pub(crate) index: usize,
    pub(crate) stage: Stage,
    pub(crate) clock: TurnClock,
    /// The turn's drawing, append-only.
    pub(crate) ops: Vec<DrawOp>,
    /// Points `ops` hold.
    pub(crate) held: usize,
    /// The last Line was refused, so no `More` may continue the stroke before it.
    pub(crate) line_refused: bool,
    pub(crate) board_present: bool,
}

#[cfg_attr(not(test), allow(dead_code))]
impl KritzeleiGame {
    pub(crate) fn new(cfg: KritzeleiConfig, deck: Vec<&'static str>, players: &[Player], _now: ServerTime) -> Self {
        let turns = turn_order(players, cfg.two_rounds_below);
        Self {
            clock: TurnClock::new(cfg.draw_ms),
            stage: if turns.is_empty() { Stage::Final } else { Stage::Handover },
            turns,
            words: deck,
            index: 0,
            ops: Vec::new(),
            held: 0,
            line_refused: false,
            board_present: true,
            cfg,
        }
    }

    pub(crate) fn drawer(&self) -> PlayerId {
        self.turns.get(self.index).copied().unwrap_or_default()
    }

    /// The turn's word; the deck holds more words than any game has turns.
    pub(crate) fn word(&self) -> &'static str {
        self.words.get(self.index).copied().unwrap_or_default()
    }

    fn next_turn(&mut self) {
        self.ops.clear();
        self.held = 0;
        self.clock = TurnClock::new(self.cfg.draw_ms);
        if self.index + 1 < self.turns.len() {
            self.index += 1;
            self.stage = Stage::Handover;
        } else {
            self.stage = Stage::Final;
        }
    }

    /// Who got it, or `None` for nobody; either way the word is revealed.
    fn pick(&mut self, ctx: &mut GameCtx, guesser: Option<PlayerId>) -> Result<(), ActionError> {
        let drawer = self.drawer();
        if let Some(guesser) = guesser {
            if guesser == drawer || !ctx.players.iter().any(|p| p.id == guesser) {
                return Err(ActionError::NotAllowed(WHO));
            }
            for p in ctx.players.iter_mut() {
                if p.id == guesser {
                    p.score += self.cfg.guesser_points;
                } else if p.id == drawer {
                    p.score += self.cfg.drawer_points;
                }
            }
        }
        self.stage = Stage::Reveal { guesser, ends_at: ctx.now + self.cfg.reveal_ms };
        Ok(())
    }
}

impl Game for KritzeleiGame {
    /// Phones only watch; everything is done on the board.
    fn on_action(&mut self, _ctx: &mut GameCtx, _player: PlayerId, _action: &Action) -> Result<(), ActionError> {
        Err(ActionError::Ignored)
    }

    fn on_tick(&mut self, ctx: &mut GameCtx) {
        match self.stage {
            // At 0 the list opens by itself, so a shout in the last second still counts.
            Stage::Drawing if self.clock.left(ctx.now) <= 0.0 => {
                self.clock.pause(ctx.now);
                self.stage = Stage::Picking;
            }
            Stage::Reveal { ends_at, .. } if ctx.now >= ends_at => self.next_turn(),
            _ => {}
        }
    }

    fn phone_screen(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        self.phone_view(view, player)
    }

    fn tv_scene(&self, view: &ViewCtx) -> TvScene {
        self.tv_view(view)
    }

    fn board_screen(&self, view: &ViewCtx) -> BoardScreen {
        self.board_view(view)
    }

    fn phone_buzz(&self, _view: &ViewCtx, player: PlayerId) -> Option<Buzz> {
        self.buzz(player)
    }

    fn is_finished(&self) -> bool {
        self.stage == Stage::Final
    }

    fn on_board(&mut self, ctx: &mut GameCtx, act: &BoardAct) -> Result<(), ActionError> {
        let now = ctx.now;
        match (self.stage, act) {
            (Stage::Handover, BoardAct::Ready) => {
                self.clock.start(now);
                self.stage = Stage::Drawing;
            }
            (Stage::Handover, BoardAct::Skip) => self.next_turn(),
            (Stage::Drawing, BoardAct::Guessed) => {
                self.clock.pause(now);
                self.stage = Stage::Picking;
            }
            (Stage::Picking, BoardAct::KeepDrawing) if self.clock.left(now) > 0.0 => {
                self.clock.start(now);
                self.stage = Stage::Drawing;
            }
            (Stage::Drawing | Stage::Picking, BoardAct::Pick { player }) => self.pick(ctx, *player)?,
            (Stage::Drawing, BoardAct::Draw { op }) => self.draw(op)?,
            _ => return Err(ActionError::Ignored),
        }
        Ok(())
    }

    /// Safe to hear twice: starting a running clock or pausing a paused one changes nothing.
    fn on_board_presence(&mut self, now: ServerTime, present: bool) {
        self.board_present = present;
        if self.stage == Stage::Drawing {
            if present { self.clock.start(now) } else { self.clock.pause(now) }
        }
    }

    fn drawing(&self) -> Option<(u32, &[DrawOp])> {
        (self.stage != Stage::Final).then_some((self.index as u32 + 1, self.ops.as_slice()))
    }
}

#[cfg(test)]
#[path = "kritzelei_tests.rs"]
pub(crate) mod tests;

#[cfg(test)]
#[path = "kritzelei_game_clock_tests.rs"]
mod game_clock_tests;
