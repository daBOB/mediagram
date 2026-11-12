//! Preisfrage: an odd article shows on the TV, everyone types its price on
//! their phone, and the closer a guess either way, the more it scores. Each
//! article opens with the article alone, then the keypads, then the reveal.

use std::collections::{BTreeMap, BTreeSet};

use crate::articles::{Article, MAX_CENTS};
pub use crate::preisfrage_config::PreisfrageConfig;
use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::preisfrage_rules::{round_rows, standings};
use crate::protocol::{Action, PhoneScreen, PlayerId, PriceRow, PriceStanding, ServerTime, TvScene};
use crate::room_seats::Player;

#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) enum Stage {
    /// The article shows at once; guesses count from `opens_at`.
    Guessing { opens_at: ServerTime, closes_at: ServerTime },
    Reveal { ends_at: ServerTime },
    Final,
}

pub struct PreisfrageGame {
    pub(crate) cfg: PreisfrageConfig,
    pub(crate) articles: Vec<Article>,
    /// When the prices were read, as the reveal shows it.
    pub(crate) checked: String,
    pub(crate) index: usize,
    pub(crate) stage: Stage,
    /// Who plays the current article: whoever was here when it opened.
    pub(crate) playing: BTreeSet<PlayerId>,
    pub(crate) guesses: BTreeMap<PlayerId, u32>,
    /// The current article's result, from its reveal on.
    pub(crate) rows: Vec<PriceRow>,
    pub(crate) totals: BTreeMap<PlayerId, u32>,
}

impl PreisfrageGame {
    /// Prices up to `cfg.rounds` of `articles` in the order given.
    pub(crate) fn new(cfg: PreisfrageConfig, articles: Vec<Article>, checked: String, players: &[Player], now: ServerTime) -> Self {
        let articles: Vec<Article> = articles.into_iter().take(cfg.rounds).collect();
        let mut game = Self {
            cfg,
            articles,
            checked,
            index: 0,
            stage: Stage::Final,
            playing: BTreeSet::new(),
            guesses: BTreeMap::new(),
            rows: Vec::new(),
            totals: players.iter().map(|p| (p.id, 0)).collect(),
        };
        // Never empty from the factory, which the lobby stops without articles.
        if !game.articles.is_empty() {
            game.open(players, now);
        }
        game
    }

    pub(crate) fn article(&self) -> Option<&Article> {
        self.articles.get(self.index)
    }

    pub(crate) fn standings(&self) -> Vec<PriceStanding> {
        standings(&self.totals.iter().map(|(&p, &t)| (p, t)).collect::<Vec<_>>())
    }

    fn open(&mut self, players: &[Player], now: ServerTime) {
        self.guesses.clear();
        self.rows.clear();
        self.playing = players.iter().filter(|p| p.connected()).map(|p| p.id).collect();
        let opens_at = now + self.cfg.intro_ms;
        self.stage = Stage::Guessing { opens_at, closes_at: opens_at + self.cfg.guess_ms };
    }

    fn reveal(&mut self, players: &mut [Player], now: ServerTime) {
        let price = self.article().map_or(0, |a| a.cents);
        let guesses: Vec<(PlayerId, Option<u32>)> = self.playing.iter().map(|&p| (p, self.guesses.get(&p).copied())).collect();
        self.rows = round_rows(price, &guesses, self.cfg.exact_bonus);
        for row in &self.rows {
            *self.totals.entry(row.player).or_default() += row.points;
        }
        for p in players.iter_mut() {
            if let Some(&total) = self.totals.get(&p.id) {
                p.score = i32::try_from(total).unwrap_or(i32::MAX);
            }
        }
        self.stage = Stage::Reveal { ends_at: now + self.cfg.reveal_ms };
    }
}

impl Game for PreisfrageGame {
    fn on_action(&mut self, ctx: &mut GameCtx, player: PlayerId, action: &Action) -> Result<(), ActionError> {
        let Action::GuessPrice { cents } = *action else { return Err(ActionError::Ignored) };
        let Stage::Guessing { opens_at, .. } = self.stage else { return Err(ActionError::Ignored) };
        if ctx.now < opens_at || !self.playing.contains(&player) || self.guesses.contains_key(&player) {
            return Err(ActionError::Ignored);
        }
        if !(1..=MAX_CENTS).contains(&cents) {
            return Err(ActionError::NotAllowed("Das ist kein Preis."));
        }
        self.guesses.insert(player, cents);
        Ok(())
    }

    fn on_tick(&mut self, ctx: &mut GameCtx) {
        let now = ctx.now;
        match self.stage {
            Stage::Guessing { opens_at, closes_at } => {
                // Only players of this article who are here hold it open.
                let waiting =
                    ctx.players.iter().any(|p| p.connected() && self.playing.contains(&p.id) && !self.guesses.contains_key(&p.id));
                if now >= closes_at || (now >= opens_at && !waiting) {
                    self.reveal(ctx.players, now);
                }
            }
            Stage::Reveal { ends_at } if now >= ends_at => {
                if self.index + 1 < self.articles.len() {
                    self.index += 1;
                    self.open(ctx.players, now);
                } else {
                    self.stage = Stage::Final;
                }
            }
            _ => {}
        }
    }

    fn phone_screen(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        self.phone_view(view, player)
    }

    fn tv_scene(&self, _view: &ViewCtx) -> TvScene {
        self.tv_view()
    }

    fn is_finished(&self) -> bool {
        self.stage == Stage::Final
    }
}

#[cfg(test)]
#[path = "preisfrage_tests.rs"]
pub(crate) mod tests;
