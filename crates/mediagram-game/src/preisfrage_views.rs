//! What the phones and the TV show for each Preisfrage stage.

use crate::articles::{MAX_CENTS, article_photo_url};
use crate::game::ViewCtx;
use crate::preisfrage::{PreisfrageGame, Stage};
use crate::protocol::{
    ArticleView, PhoneScreen, PlayerId, PreisfrageScene, PreisfrageStage, PriceKeypad, Tone, TvScene,
};

/// `1234567` → `12.345,67 €`.
pub(crate) fn euro(cents: u32) -> String {
    let digits = (cents / 100).to_string();
    let mut grouped = String::new();
    for (i, ch) in digits.chars().enumerate() {
        if i > 0 && (digits.len() - i).is_multiple_of(3) {
            grouped.push('.');
        }
        grouped.push(ch);
    }
    format!("{grouped},{:02} €", cents % 100)
}

impl PreisfrageGame {
    fn article_view(&self) -> ArticleView {
        let article = self.article().or(self.articles.last());
        ArticleView {
            asin: article.map_or_else(String::new, |a| a.asin.clone()),
            name: article.map_or_else(String::new, |a| a.name.clone()),
            photo: article.map_or_else(String::new, |a| article_photo_url(&a.asin)),
        }
    }

    pub(crate) fn phone_view(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        match self.stage {
            Stage::Final => {
                let standings = self.standings();
                let mine = standings.iter().find(|s| s.player == player);
                PhoneScreen::Final {
                    rank: mine.map_or(standings.len() as u8 + 1, |s| s.place),
                    of: view.players.len() as u8,
                    score: mine.map_or(0, |s| i32::try_from(s.points).unwrap_or(i32::MAX)),
                    can_restart: view.leader == Some(player),
                }
            }
            _ if !self.playing.contains(&player) => {
                PhoneScreen::Wait { title: "Preisfrage läuft".into(), text: Some("Beim nächsten Artikel bist du dabei.".into()) }
            }
            Stage::Guessing { opens_at, closes_at } => PhoneScreen::Price {
                keypad: PriceKeypad {
                    article: self.article_view(),
                    round: self.index as u8 + 1,
                    rounds: self.articles.len() as u8,
                    opens_at,
                    closes_at,
                    sent: self.guesses.get(&player).copied(),
                    max_cents: MAX_CENTS,
                },
            },
            Stage::Reveal { .. } => self.outcome(player),
        }
    }

    /// The reveal on `player`'s phone: the price, their guess and its points.
    fn outcome(&self, player: PlayerId) -> PhoneScreen {
        let row = self.rows.iter().find(|r| r.player == player);
        let (text, tone) = match row.and_then(|r| r.cents.map(|c| (r, c))) {
            None => ("Kein Tipp".to_string(), Tone::Muted),
            Some((r, cents)) => {
                let how = if r.exact { " · auf den Cent!" } else if r.closest { " · am nächsten dran" } else { "" };
                (format!("Dein Tipp: {}{how}", euro(cents)), if r.closest { Tone::Good } else { Tone::Neutral })
            }
        };
        PhoneScreen::Outcome {
            title: euro(self.article().map_or(0, |a| a.cents)),
            text: Some(text),
            tone,
            points: row.map_or(0, |r| i32::try_from(r.points).unwrap_or(i32::MAX)),
            keys: vec![],
            show_score: true,
        }
    }

    pub(crate) fn tv_view(&self) -> TvScene {
        let stage = match self.stage {
            Stage::Guessing { opens_at, closes_at } => {
                PreisfrageStage::Guessing { opens_at, closes_at, guessed: self.guesses.keys().copied().collect() }
            }
            Stage::Reveal { ends_at } => PreisfrageStage::Reveal {
                cents: self.article().map_or(0, |a| a.cents),
                checked: self.checked.clone(),
                rows: self.rows.clone(),
                ends_at,
            },
            Stage::Final => PreisfrageStage::Final { standings: self.standings() },
        };
        let scene = PreisfrageScene {
            round: self.index as u8 + 1,
            rounds: self.articles.len() as u8,
            article: self.article_view(),
            stage,
        };
        TvScene::Preisfrage { scene }
    }
}

#[cfg(test)]
#[path = "preisfrage_views_tests.rs"]
mod tests;
