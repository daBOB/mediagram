//! The three rules cards the TV shows before the first roll. The room's
//! leader may skip them from their phone; every other phone points at the TV.

use super::live::{GrandAlleeGame, Stage};
use super::live_text::taler;
use crate::game::{ActionError, GameCtx, ViewCtx, name};
use crate::protocol::{GrandAlleeTurn, PlayerId, ServerTime};
use crate::room_seats::leader;

/// After the last card the first move begins.
const CARDS: u8 = 3;

impl GrandAlleeGame {
    /// Card `card`'s title and lines, gold words between `**`.
    pub(crate) fn card(&self, card: u8) -> (&'static str, Vec<String>) {
        let own = |lines: &[&str]| lines.iter().map(|&l| l.to_owned()).collect();
        match card {
            0 => ("Das Ziel", vec![
                format!("Nach **{} Runden** gewinnt, wer am meisten besitzt:", self.rounds),
                "**Geld, Grundstücke** und **gebaute Hotels** zählen.".into(),
                "Wer eine Rechnung nicht mehr bezahlen kann, ist **raus**.".into(),
            ]),
            1 => ("Dein Zug", own(&[
                "**Würfle** auf dem Handy und lauf.",
                "Freies **Grundstück**? Kaufen.",
                "**Baustelle**? Bauen – der Genehmigungswürfel entscheidet.",
                "Am **Rathaus** vorbei: Eingänge kaufen.",
                "Eine **6** darf nochmal.",
            ])),
            _ => ("Das Geld", vec![
                "Landest du auf einem **fremden Eingang**, zahlst du: Nächte × Preis des Hotels.".into(),
                format!("Über die **Bank**: {}.", taler(self.t.rules.lap_bonus)),
                "Fehlt dir Geld, **versteigerst** du Besitz an die anderen.".into(),
            ]),
        }
    }

    /// The next card once this one's time is up; after the last, the first move.
    pub(crate) fn next_card(&mut self, now: ServerTime, card: u8) {
        if card + 1 < CARDS {
            self.stage = Stage::Intro { card: card + 1, at: now };
        } else {
            self.begin_move(0, now);
        }
    }

    /// The leader's tap starts the first move; anybody else's is ignored.
    pub(crate) fn skip_intro(&mut self, ctx: &GameCtx, player: PlayerId) -> Result<(), ActionError> {
        if leader(ctx.players) != Some(player) {
            return Err(ActionError::Ignored);
        }
        // the roll opens a moment later, so the second tap of a double tap cannot roll
        self.begin_move(0, ctx.now + self.cfg.chain_ms);
        Ok(())
    }

    /// A phone's prompt during the intro, and the skip button on the leader's.
    pub(crate) fn intro_phone(&self, view: &ViewCtx, player: PlayerId, card: u8) -> (String, Option<String>) {
        let skip = (leader(view.players) == Some(player)).then(|| "Überspringen".to_owned());
        (format!("Schau auf den Fernseher: {}", self.card(card).0), skip)
    }

    pub(crate) fn intro_turn(&self, view: &ViewCtx, card: u8, at: ServerTime) -> GrandAlleeTurn {
        let (title, lines) = self.card(card);
        let leader = leader(view.players).map(|id| name(view.players, id).to_owned());
        GrandAlleeTurn::Intro { card: card + 1, title: title.into(), lines, opened_at: at, closes_at: at + self.cfg.intro_ms, leader }
    }
}
