//! What the mover can decide, as lists of options the phone shows as
//! buttons, and what the house picks when the clock runs out or the mover is
//! away.

use super::board::{field, Field, HOTELS};
use super::live::{GrandAlleeGame, HOUSE};
use super::rules::Permit;
use super::live_text::taler;

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
            Opt::Build { hotel, .. } | Opt::Hotel { hotel, .. } | Opt::Place { hotel, .. } => Some(hotel),
            Opt::Roll | Opt::EndTurn | Opt::Skip | Opt::Done | Opt::Back => None,
        }
    }
}

#[derive(Debug, Clone, PartialEq)]
pub(crate) struct Ask {
    pub prompt: String,
    /// The hotel in question, for the TV to point at.
    pub hotel: Option<u8>,
    pub opts: Vec<Opt>,
}

fn ask(prompt: impl Into<String>, opts: Vec<Opt>) -> Ask {
    let hotel = opts.first().and_then(|o| o.hotel());
    Ask { prompt: prompt.into(), hotel, opts }
}

impl GrandAlleeGame {
    fn cash(&self) -> i32 {
        self.t.seats[self.turn].cash
    }

    /// What an option costs the mover (0 for what is free or costs nothing).
    pub(crate) fn price(&self, opt: Opt) -> i32 {
        let t = &self.t;
        match opt {
            Opt::Leisure(h) => t.leisure_cost(h).unwrap_or(0),
            Opt::Buy(h) => HOTELS[usize::from(h)].land,
            Opt::Force(h) => t.forced_price(h),
            Opt::Build { hotel, count } => t.stage_price(hotel, count, Permit::Approved),
            Opt::Hotel { hotel, free: false } | Opt::Place { hotel, free: false, .. } => HOTELS[usize::from(hotel)].entrance,
            _ => 0,
        }
    }

    /// Before rolling: roll, or first a leisure wing; after a 6 the extra turn may be let go.
    pub(crate) fn roll_ask(&self) -> Ask {
        let mut opts = vec![Opt::Roll];
        let wings = self.t.owned(self.turn).into_iter().filter(|&h| self.t.leisure_cost(h).is_some_and(|c| c <= self.cash()));
        opts.extend(wings.map(Opt::Leisure));
        let extra = self.roll == 6;
        if extra {
            opts.push(Opt::EndTurn);
        }
        Ask { prompt: if extra { "Eine 6: Du darfst nochmal!" } else { "Du bist dran" }.into(), hotel: None, opts }
    }

    /// What the field the mover stopped on offers that they can pay, if anything.
    pub(crate) fn field_ask(&self) -> Option<Ask> {
        let (s, at, cash) = (self.turn, self.t.seats[self.turn].field, self.cash());
        let t = &self.t;
        let (prompt, mut opts): (&str, Vec<Opt>) = match field(at) {
            Field::Plot => {
                let offers = t.plot_offers(s, at).into_iter().filter(|&(_, price)| price <= cash);
                ("Grundstück: Was kaufst du?", offers.map(|(h, _)| if t.estate(h).owner.is_some() { Opt::Force(h) } else { Opt::Buy(h) }).collect())
            }
            Field::Site => {
                // only stages the player can pay at their normal price
                let affordable = move |h: u8| (1..=t.permit_stages_left(h)).take_while(move |&n| t.stage_price(h, n, Permit::Approved) <= cash);
                let builds = t.site_hotels(s, at).into_iter().flat_map(|hotel| affordable(hotel).map(move |count| Opt::Build { hotel, count }));
                ("Baustelle: Was baust du? Dann entscheidet der Genehmigungswürfel.", builds.collect())
            }
            Field::FreeEntrance => return self.free_entrance_ask(),
            Field::FreeBuild => ("Gratis bauen: Welche Stufe schenkt dir die Stadt?", t.open_hotels(s).into_iter().map(Opt::FreeStage).collect()),
            Field::Bank | Field::Rathaus | Field::Allee => return None,
        };
        if opts.is_empty() {
            return None;
        }
        opts.push(Opt::Skip);
        Some(ask(prompt, opts))
    }

    /// Passing the Rathaus: one entrance per own built hotel, each once per pass.
    pub(crate) fn rathaus_ask(&self) -> Option<Ask> {
        let fits = |h: &u8| !self.served.contains(h) && HOTELS[usize::from(*h)].entrance <= self.cash();
        let mut opts: Vec<Opt> = self.t.entrance_hotels(self.turn).into_iter().filter(fits).map(|hotel| Opt::Hotel { hotel, free: false }).collect();
        if opts.is_empty() {
            return None;
        }
        opts.push(Opt::Done);
        Some(Ask { prompt: "Rathaus: Ein Eingang je Hotel – für welches?".into(), hotel: None, opts })
    }

    /// Freier Eingang: which hotel; a single candidate goes straight to its fields.
    fn free_entrance_ask(&self) -> Option<Ask> {
        match self.t.entrance_hotels(self.turn).as_slice() {
            [] => None,
            &[hotel] => Some(self.place_ask(hotel, true, Some(Opt::Skip))),
            hotels => {
                let mut opts: Vec<Opt> = hotels.iter().map(|&hotel| Opt::Hotel { hotel, free: true }).collect();
                opts.push(Opt::Skip);
                Some(Ask { prompt: "Freier Eingang: Für welches Hotel?".into(), hotel: None, opts })
            }
        }
    }

    /// Where an entrance goes; `out` is the way back, if there is one.
    pub(crate) fn place_ask(&self, hotel: u8, free: bool, out: Option<Opt>) -> Ask {
        let mut opts: Vec<Opt> = self.t.free_frontage(hotel).into_iter().map(|field| Opt::Place { hotel, field, free }).collect();
        opts.extend(out);
        let h = &HOTELS[usize::from(hotel)];
        let what = if free { "Eingang gratis".to_string() } else { format!("Eingang für {}", taler(h.entrance)) };
        Ask { prompt: format!("{}: {what} – auf welches Feld?", h.name), hotel: Some(hotel), opts }
    }

    /// Short of `owed`: which property goes up for auction.
    pub(crate) fn sell_ask(&self, owed: i32) -> Ask {
        let opts = self.t.owned(self.turn).into_iter().map(Opt::Sell).collect();
        Ask { prompt: format!("Dir fehlen {}. Was versteigerst du?", taler(owed - self.cash())), hotel: None, opts }
    }

    /// The house's pick: the simulator's balanced bot, which also lets an
    /// extra turn go and takes the cheapest entrance (the design's absent player).
    pub(crate) fn auto_index(&self, ask: &Ask) -> usize {
        let (t, s, opts) = (&self.t, self.turn, &ask.opts);
        let at = |want: Option<Opt>| want.and_then(|w| opts.iter().position(|&o| o == w));
        let mut hotels: Vec<u8> = opts.iter().filter_map(|o| o.hotel()).collect();
        hotels.dedup();
        let pick = match opts[0] {
            Opt::Roll => opts.iter().position(|&o| matches!(o, Opt::Leisure(_)) && HOUSE.spends(t, s, self.price(o))),
            Opt::Buy(_) | Opt::Force(_) => {
                let offers: Vec<(u8, i32)> = opts.iter().filter_map(|&o| o.hotel().map(|h| (h, self.price(o)))).collect();
                HOUSE.pick_plot(t, s, &offers).and_then(|h| opts.iter().position(|o| o.hotel() == Some(h)))
            }
            Opt::Build { .. } => at(HOUSE.build_plan(t, s, &hotels).map(|(hotel, count)| Opt::Build { hotel, count })),
            Opt::Hotel { free: true, .. } => at(HOUSE.pick_free_entrance(t, &hotels).map(|hotel| Opt::Hotel { hotel, free: true })),
            Opt::Hotel { free: false, .. } => {
                let fits = opts.iter().filter(|&&o| o != Opt::Done && HOUSE.spends(t, s, self.price(o)));
                at(fits.min_by_key(|&&o| self.price(o)).copied())
            }
            Opt::FreeStage(_) => at(HOUSE.pick_free_stage(t, &hotels).map(Opt::FreeStage)),
            Opt::Sell(_) => at(HOUSE.sell_choice(t, s).map(Opt::Sell)),
            Opt::Place { .. } => Some(0),
            _ => None,
        };
        let out = || opts.iter().position(|o| matches!(o, Opt::Skip | Opt::Done | Opt::Back | Opt::EndTurn));
        pick.or_else(out).unwrap_or(0)
    }
}
