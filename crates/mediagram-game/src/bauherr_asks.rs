//! What a player can decide on the field they stand on, as lists of options
//! the phone shows as buttons. The last option of every list is `Skip`.

use crate::bauherr_rules::{RESERVE, Table};
use crate::protocol::{BauherrPermit, BauherrTileKind};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum Opt {
    Buy(u8),
    /// Roll the permit die for this hotel.
    Build(u8),
    /// At the Rathaus: pick which hotel to build on.
    BuildMenu,
    /// At the Rathaus: pick which hotel to buy an entrance for.
    Entrances,
    Hotel { plot: u8, free: bool },
    Place { plot: u8, field: u8, free: bool },
    /// Build `count` stages for `cost`.
    Levels { plot: u8, count: u8, cost: i32 },
    Skip,
}

#[derive(Debug, Clone, PartialEq)]
pub(crate) struct Ask {
    pub prompt: String,
    /// The hotel in question, for the TV to point at.
    pub plot: Option<u8>,
    pub opts: Vec<Opt>,
}

fn ask(prompt: impl Into<String>, plot: Option<u8>, mut opts: Vec<Opt>) -> Ask {
    opts.push(Opt::Skip);
    Ask { prompt: prompt.into(), plot, opts }
}

impl Table {
    fn can_build(&self, seat: usize, plot: u8) -> bool {
        self.owner(plot) == Some(seat) && self.next_cost(plot).is_some_and(|c| c <= self.seats[seat].cash)
    }

    fn can_enter(&self, seat: usize, plot: u8, free: bool) -> bool {
        self.owner(plot) == Some(seat) && !self.free_fields(plot).is_empty() && (free || self.plot(plot).entrance <= self.seats[seat].cash)
    }

    fn plots_where(&self, seat: usize, test: impl Fn(u8) -> bool) -> Vec<u8> {
        self.owned(seat).filter(|&p| test(p)).collect()
    }

    /// What the field the player just reached offers, if anything.
    pub(crate) fn field_ask(&self, seat: usize) -> Option<Ask> {
        let field = self.seats[seat].field;
        let plot = self.plot_at(field);
        match self.kind(field) {
            BauherrTileKind::Bauplatz => {
                let p = plot.filter(|&p| self.owner(p).is_none() && self.plot(p).land <= self.seats[seat].cash)?;
                Some(ask(format!("{} steht zum Verkauf.", self.plot(p).name), Some(p), vec![Opt::Buy(p)]))
            }
            BauherrTileKind::Baustelle => {
                let p = plot.filter(|&p| self.can_build(seat, p))?;
                Some(ask(format!("Auf deiner Baustelle: {} ausbauen?", self.plot(p).name), Some(p), vec![Opt::Build(p)]))
            }
            BauherrTileKind::Rathaus => {
                let mut opts = Vec::new();
                if !self.plots_where(seat, |p| self.can_build(seat, p)).is_empty() {
                    opts.push(Opt::BuildMenu);
                }
                if !self.plots_where(seat, |p| self.can_enter(seat, p, false)).is_empty() {
                    opts.push(Opt::Entrances);
                }
                (!opts.is_empty()).then(|| ask("Im Rathaus: Was möchtest du erledigen?", None, opts))
            }
            BauherrTileKind::Eingang => self.hotel_ask(seat, true),
            _ => None,
        }
    }

    pub(crate) fn build_menu_ask(&self, seat: usize) -> Ask {
        let opts = self.plots_where(seat, |p| self.can_build(seat, p)).into_iter().map(Opt::Build).collect();
        ask("Welches Hotel ausbauen?", None, opts)
    }

    /// Which hotel gets an entrance; a single candidate goes straight to its fields.
    pub(crate) fn hotel_ask(&self, seat: usize, free: bool) -> Option<Ask> {
        let plots = self.plots_where(seat, |p| self.can_enter(seat, p, free));
        match plots.as_slice() {
            [] => None,
            &[plot] => Some(self.place_ask(plot, free)),
            _ => {
                let what = if free { "Gratis-Eingang" } else { "Eingang" };
                Some(ask(format!("{what}: Für welches Hotel?"), None, plots.into_iter().map(|plot| Opt::Hotel { plot, free }).collect()))
            }
        }
    }

    pub(crate) fn place_ask(&self, plot: u8, free: bool) -> Ask {
        let opts = self.free_fields(plot).into_iter().map(|field| Opt::Place { plot, field, free }).collect();
        let what = if free { "Gratis-Eingang".to_string() } else { format!("Eingang für {} Taler", self.plot(plot).entrance) };
        ask(format!("{}: {what} – wohin?", self.plot(plot).name), Some(plot), opts)
    }

    /// How far to build once the permit is in; `None` when nothing is affordable.
    pub(crate) fn levels_ask(&self, seat: usize, plot: u8, permit: BauherrPermit) -> Option<Ask> {
        let opts: Vec<Opt> =
            self.level_prices(seat, plot, permit).into_iter().map(|(count, cost)| Opt::Levels { plot, count, cost }).collect();
        (!opts.is_empty()).then(|| ask(format!("{}: Wie weit bauen?", self.plot(plot).name), Some(plot), opts))
    }

    /// What an absent player picks: buy or build while it keeps `RESERVE` in
    /// hand, the cheapest entrance, else skip.
    pub(crate) fn auto_index(&self, seat: usize, ask: &Ask) -> usize {
        let cash = self.seats[seat].cash;
        let keeps = |cost: i32| cash - cost >= RESERVE;
        let best = ask.opts.iter().enumerate().filter_map(|(i, o)| {
            let (prio, cost) = match *o {
                Opt::Buy(p) => (5, self.plot(p).land),
                Opt::Build(p) => (4, self.next_cost(p)?),
                Opt::BuildMenu => (4, 0),
                Opt::Entrances => (3, 0),
                Opt::Hotel { plot, free } => (3, if free { 0 } else { self.plot(plot).entrance }),
                Opt::Place { plot, free, .. } => (3, if free { 0 } else { self.plot(plot).entrance }),
                Opt::Levels { count, cost, .. } => (2 + i32::from(count), cost),
                Opt::Skip => return None,
            };
            keeps(cost).then_some((i, (prio, -cost)))
        });
        best.max_by_key(|&(_, key)| key).map_or(ask.opts.len() - 1, |(i, _)| i)
    }
}
