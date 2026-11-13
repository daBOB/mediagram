//! The rules cards the TV shows before round 1. Nobody moves while they run;
//! the room's leader may skip them from their phone.

use crate::fluechtig::{Ask, FluechtigGame, Step};
use crate::game::{ActionError, GameCtx, ViewCtx, name};
use crate::protocol::{FluechtigIntro, PlayerId, ServerTime};
use crate::room_seats::leader;

/// After the last card the fugitive's first question opens.
pub(crate) const CARDS: u8 = 3;

/// The card on the TV (0-based) and when it opened.
#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) struct Intro {
    pub card: u8,
    pub at: ServerTime,
}

impl FluechtigGame {
    /// The next card once this one's time is up; after the last, the chase.
    pub(crate) fn next_card(&mut self, now: ServerTime) {
        let Some(Intro { card, .. }) = self.intro else { return };
        if card + 1 < CARDS {
            self.intro = Some(Intro { card: card + 1, at: now });
        } else {
            self.begin_chase(now);
        }
    }

    /// The leader's tap starts the chase; anybody else's is ignored.
    pub(crate) fn skip_intro(&mut self, ctx: &GameCtx, player: PlayerId) -> Result<(), ActionError> {
        if leader(ctx.players) != Some(player) {
            return Err(ActionError::Ignored);
        }
        // the question opens a moment later, so the second tap of a double tap cannot answer it
        self.begin_chase(ctx.now + self.cfg.chain_ms);
        Ok(())
    }

    /// The card as TV and phones see it; None once the chase runs.
    pub(crate) fn intro_view(&self, view: &ViewCtx) -> Option<FluechtigIntro> {
        let Intro { card, at } = self.intro?;
        let leader = leader(view.players).map(|id| name(view.players, id).to_owned());
        Some(FluechtigIntro { card: card + 1, cards: CARDS, opened_at: at, closes_at: at + self.cfg.intro_ms, leader })
    }

    fn begin_chase(&mut self, opens_at: ServerTime) {
        self.intro = None;
        self.ask = Ask { opens_at, step: Step::Field };
    }
}
