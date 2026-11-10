//! What the mover can decide, as lists of options the phone shows as
//! buttons; a bot (`Bot::pick`) answers them for the house and the simulator.

use super::board::{field, Field, HOTELS};
use super::live::GrandAlleeGame;
use super::live_debts::Debt;
use super::rules::{Permit, Table};
use super::live_text::{taler, Phrase};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum Opt {
    Roll,
    /// Lets the extra turn after a 6 go.
    EndTurn,
    /// The leisure wing of a completed hotel, before rolling, no permit.
    Leisure(u8),
    Buy(u8),
    /// Forced purchase of another player's unbuilt plot.
    Force(u8),
    /// Names `count` stages; the permit die decides next.
    Build { hotel: u8, count: u8 },
    /// Which hotel an entrance is for.
    Hotel { hotel: u8, free: bool },
    Place { hotel: u8, field: u8, free: bool },
    /// Gratis bauen: the next stage of this hotel, free.
    FreeStage(u8),
    /// Puts a property up for a debt auction.
    Sell(u8),
    /// Pays a doubled permit by auctioning other properties, then builds.
    Raise { hotel: u8, count: u8 },
    Skip,
    /// Leaves the Rathaus.
    Done,
    /// Back to the Rathaus menu.
    Back,
}

impl Opt {
    pub(crate) fn hotel(self) -> Option<u8> {
        match self {
            Opt::Leisure(h) | Opt::Buy(h) | Opt::Force(h) | Opt::FreeStage(h) | Opt::Sell(h) => Some(h),
            Opt::Build { hotel, .. } | Opt::Raise { hotel, .. } | Opt::Hotel { hotel, .. } | Opt::Place { hotel, .. } => Some(hotel),
            Opt::Roll | Opt::EndTurn | Opt::Skip | Opt::Done | Opt::Back => None,
        }
    }

    /// What the option costs the mover (0 for what is free or costs nothing).
    pub(crate) fn price(self, t: &Table) -> i32 {
        match self {
            Opt::Leisure(h) => t.leisure_cost(h).unwrap_or(0),
            Opt::Buy(h) => HOTELS[usize::from(h)].land,
            Opt::Force(h) => t.forced_price(h),
            Opt::Build { hotel, count } => t.stage_price(hotel, count, Permit::Approved),
            Opt::Hotel { hotel, free: false } | Opt::Place { hotel, free: false, .. } => HOTELS[usize::from(hotel)].entrance,
            _ => 0,
        }
    }
}

#[derive(Debug, Clone, PartialEq)]
pub(crate) struct Ask {
    pub prompt: Phrase,
    /// The hotel in question, for the TV to point at.
    pub hotel: Option<u8>,
    pub opts: Vec<Opt>,
}

fn ask(prompt: Phrase, opts: Vec<Opt>) -> Ask {
    let hotel = opts.first().and_then(|o| o.hotel());
    Ask { prompt, hotel, opts }
}

impl GrandAlleeGame {
    fn cash(&self) -> i32 {
        self.t.seats[self.turn].cash
    }

    /// Before rolling: roll, or first a leisure wing; after a 6 the extra turn may be let go.
    pub(crate) fn roll_ask(&self) -> Ask {
        let mut opts = vec![Opt::Roll];
        let wings = self.t.owned(self.turn).into_iter().filter(|&h| self.t.leisure_cost(h).is_some_and(|c| c <= self.cash()));
        opts.extend(wings.map(Opt::Leisure));
        let extra = self.six;
        if extra {
            opts.push(Opt::EndTurn);
        }
        let prompt = if extra { Phrase::new("Eine 6: Du darfst nochmal!", "hat eine 6 und darf nochmal!") } else { Phrase::new("Du bist dran", "ist dran") };
        Ask { prompt, hotel: None, opts }
    }

    /// What the field the mover stopped on offers that they can pay, if anything.
    pub(crate) fn field_ask(&self) -> Option<Ask> {
        let (s, at, cash) = (self.turn, self.t.seats[self.turn].field, self.cash());
        let t = &self.t;
        let (du, then, mut opts): (&str, &str, Vec<Opt>) = match field(at) {
            Field::Plot => {
                let offers = t.plot_offers(s, at).into_iter().filter(|&(_, price)| price <= cash);
                ("Grundstück: Was kaufst du?", "steht auf einem Grundstück: Was wird gekauft?", offers.map(|(h, _)| if t.estate(h).owner.is_some() { Opt::Force(h) } else { Opt::Buy(h) }).collect())
            }
            Field::Site => {
                // only stages the player can pay at their normal price
                let affordable = move |h: u8| (1..=t.permit_stages_left(h)).take_while(move |&n| t.stage_price(h, n, Permit::Approved) <= cash);
                let builds = t.site_hotels(s, at).into_iter().flat_map(|hotel| affordable(hotel).map(move |count| Opt::Build { hotel, count }));
                ("Baustelle: Was baust du? Dann entscheidet der Genehmigungswürfel.", "steht auf einer Baustelle: Was wird gebaut?", builds.collect())
            }
            Field::FreeEntrance => return self.free_entrance_ask(),
            Field::FreeBuild => {
                let opts = t.open_hotels(s).into_iter().map(Opt::FreeStage).collect();
                ("Gratis bauen: Welche Stufe schenkt dir die Stadt?", "bekommt eine Stufe gratis: Für welches Hotel?", opts)
            }
            Field::Bank | Field::Rathaus | Field::Allee => return None,
        };
        if opts.is_empty() {
            return None;
        }
        opts.push(Opt::Skip);
        Some(ask(Phrase::new(du, then), opts))
    }

    /// Passing the Rathaus: one entrance per own built hotel, each once per pass (`served` had theirs).
    pub(crate) fn rathaus_ask(&self, served: &[u8]) -> Option<Ask> {
        let fits = |h: &u8| !served.contains(h) && HOTELS[usize::from(*h)].entrance <= self.cash();
        let mut opts: Vec<Opt> = self.t.entrance_hotels(self.turn).into_iter().filter(fits).map(|hotel| Opt::Hotel { hotel, free: false }).collect();
        if opts.is_empty() {
            return None;
        }
        opts.push(Opt::Done);
        let prompt = Phrase::new("Rathaus: Ein Eingang je Hotel – für welches?", "kommt am Rathaus vorbei: Ein Eingang je Hotel – für welches?");
        Some(Ask { prompt, hotel: None, opts })
    }

    /// Freier Eingang: which hotel; a single candidate goes straight to its fields.
    fn free_entrance_ask(&self) -> Option<Ask> {
        match self.t.entrance_hotels(self.turn).as_slice() {
            [] => None,
            &[hotel] => Some(self.place_ask(hotel, true, Some(Opt::Skip))),
            hotels => {
                let mut opts: Vec<Opt> = hotels.iter().map(|&hotel| Opt::Hotel { hotel, free: true }).collect();
                opts.push(Opt::Skip);
                let prompt = Phrase::new("Freier Eingang: Für welches Hotel?", "bekommt einen Eingang gratis: Für welches Hotel?");
                Some(Ask { prompt, hotel: None, opts })
            }
        }
    }

    /// Where an entrance goes; `out` is the way back, if there is one.
    pub(crate) fn place_ask(&self, hotel: u8, free: bool, out: Option<Opt>) -> Ask {
        let mut opts: Vec<Opt> = self.t.free_frontage(hotel).into_iter().map(|field| Opt::Place { hotel, field, free }).collect();
        opts.extend(out);
        let h = &HOTELS[usize::from(hotel)];
        let (what, then) = if free {
            ("Eingang gratis".to_string(), format!("setzt einen Gratis-Eingang für {}", h.name))
        } else {
            (format!("Eingang für {}", taler(h.entrance)), format!("kauft einen Eingang für {} ({})", h.name, taler(h.entrance)))
        };
        let prompt = Phrase::new(format!("{}: {what} – auf welches Feld?", h.name), format!("{then} – auf welches Feld?"));
        Ask { prompt, hotel: Some(hotel), opts }
    }

    /// What the mover may put up for a debt auction: all they own but the hotel a doubled permit is being paid for.
    pub(crate) fn sellable(&self, debt: Debt) -> Vec<u8> {
        self.t.owned(self.turn).into_iter().filter(|&h| debt.building() != Some(h)).collect()
    }

    /// Short of `debt`: which property goes up for auction.
    pub(crate) fn sell_ask(&self, debt: Debt) -> Ask {
        let opts = self.sellable(debt).into_iter().map(Opt::Sell).collect();
        let short = taler(debt.amount() - self.cash());
        let prompt = Phrase::new(format!("Dir fehlen {short}. Was versteigerst du?"), format!("fehlen {short}. Was wird versteigert?"));
        Ask { prompt, hotel: None, opts }
    }
}
