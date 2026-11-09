//! What the TV shows during Grand Allee: the board as data, who owns and has
//! built what, the pawns, and the turn's current stage with its clocks.

use super::board::{field, FIELDS, HOTELS};
use super::live::{Bid, GrandAlleeGame, Stage};
use super::live_asks::Opt;
use crate::protocol::{
    AnswerColor, GrandAlleeAuction, GrandAlleeBoard, GrandAlleeEntrance, GrandAlleeEstate, GrandAlleeHotel, GrandAlleeMark,
    GrandAlleeScene, GrandAlleeSeat, GrandAlleeStage, GrandAlleeTurn, PlayerId, ServerTime, TvScene,
};

/// The board as the TV gets it: every field's kind and the hotels with their prices.
pub(crate) fn board() -> GrandAlleeBoard {
    let hotels = HOTELS.iter().enumerate().map(|(id, h)| GrandAlleeHotel {
        id: id as u8,
        name: h.name.into(),
        first: h.frontage.0,
        last: h.frontage.1,
        land: h.land,
        entrance: h.entrance,
        stages: h.stages.iter().map(|&(cost, rate)| GrandAlleeStage { cost, rate }).collect(),
    });
    GrandAlleeBoard { fields: (0..FIELDS).map(field).collect(), hotels: hotels.collect() }
}

impl GrandAlleeGame {
    fn lands_at(&self, at: ServerTime) -> ServerTime {
        at + self.cfg.roll_ms
    }

    fn players_of(&self, seats: impl IntoIterator<Item = usize>) -> Vec<PlayerId> {
        seats.into_iter().map(|s| self.ids[s]).collect()
    }

    fn turn_view(&self) -> GrandAlleeTurn {
        let (t, player) = (&self.t, self.ids[self.turn]);
        match &self.stage {
            Stage::Ask { ask, since, .. } => {
                let marks = ask.opts.iter().zip(AnswerColor::ALL.iter().cycle()).filter_map(|(o, &color)| match *o {
                    Opt::Place { field, .. } => Some(GrandAlleeMark { field, color }),
                    _ => None,
                });
                GrandAlleeTurn::Ask {
                    player,
                    prompt: ask.prompt.clone(),
                    hotel: ask.hotel,
                    marks: marks.collect(),
                    opened_at: *since,
                    closes_at: since + self.cfg.ask_ms,
                }
            }
            Stage::Rolled { value, at } => GrandAlleeTurn::Rolled { player, value: *value, rolled_at: *at, lands_at: self.lands_at(*at) },
            Stage::Walking { value, from, at, bank, .. } => GrandAlleeTurn::Walking {
                player,
                value: *value,
                from: *from,
                to: t.seats[self.turn].field,
                started_at: *at,
                hop_ms: self.cfg.hop_ms,
                bank: *bank,
            },
            Stage::Guest { owner, hotel, nights, amount, at } => GrandAlleeTurn::Guest {
                player,
                owner: self.ids[*owner],
                hotel: *hotel,
                nights: *nights,
                amount: *amount,
                rolled_at: *at,
                lands_at: self.lands_at(*at),
                paid_at: self.lands_at(*at) + self.cfg.show_ms,
            },
            Stage::Permit { hotel, count, permit, at } => {
                GrandAlleeTurn::Permit { player, hotel: *hotel, count: *count, permit: *permit, rolled_at: *at, lands_at: self.lands_at(*at) }
            }
            Stage::Auction(a) => {
                let answered = |want: fn(Bid) -> bool| self.players_of(a.bids.iter().filter(|(_, b)| want(*b)).map(|&(s, _)| s));
                GrandAlleeTurn::Auction {
                    auction: GrandAlleeAuction {
                        seller: player,
                        hotel: a.hotel,
                        value: t.value(a.hotel),
                        opened_at: a.since,
                        closes_at: a.since + self.cfg.auction_ms,
                        bidders: self.players_of(a.bids.iter().map(|&(s, _)| s)),
                        bid: answered(|b| matches!(b, Bid::Amount(_))),
                        passed: answered(|b| b == Bid::Pass),
                    },
                }
            }
            Stage::Sold { hotel, winner, price, at, .. } => {
                GrandAlleeTurn::Sold { seller: player, hotel: *hotel, winner: winner.map(|w| self.ids[w]), price: *price, started_at: *at }
            }
            Stage::Note { text, broke, at } => GrandAlleeTurn::Note { player, text: text.clone(), broke: *broke, started_at: *at },
            Stage::Final => {
                let ranking = self.players_of(self.standings());
                GrandAlleeTurn::Final { winner: ranking[0], ranking }
            }
        }
    }

    pub(crate) fn tv_view(&self) -> TvScene {
        let t = &self.t;
        let estates = (0..HOTELS.len() as u8)
            .filter_map(|hotel| t.estate(hotel).owner.map(|o| GrandAlleeEstate { hotel, owner: self.ids[o], levels: t.estate(hotel).levels }))
            .collect();
        let entrances =
            t.entrances.iter().enumerate().filter_map(|(f, h)| h.map(|hotel| GrandAlleeEntrance { field: f as u8, hotel })).collect();
        let seats = t
            .seats
            .iter()
            .enumerate()
            .map(|(s, seat)| GrandAlleeSeat { player: self.ids[s], field: seat.field, cash: seat.cash, worth: t.net_worth(s), out: seat.out })
            .collect();
        TvScene::GrandAllee {
            scene: GrandAlleeScene {
                board: board(),
                estates,
                entrances,
                seats,
                round: self.round.min(self.rounds),
                rounds: self.rounds,
                turn: self.turn_view(),
                die: self.dice.die,
                permit: self.dice.permit,
                nights: self.dice.nights,
            },
        }
    }
}
