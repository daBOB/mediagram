//! Lebensweg's cards: the original's 36-card deck (9 careers, 9 houses,
//! 9 salaries, 9 stocks) and its 25 life tokens, with the original's amounts
//! (2000 edition; deck values from the fan clone our board was counted from,
//! `research/original-rules-and-numbers.md`). Names and texts are our own.
//!
//! Amounts here are the original's. One-off amounts (house prices, insurance)
//! are scaled by `Rules::once` where they are paid; per-field and per-spin
//! amounts are not, because the stretched board already has more of those fields.

/// A career's perk, as on its card.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Perk {
    /// Taxes due on the board are paid to this player instead of the bank.
    Taxes,
    /// Collects 10,000 from a player who spins a 1.
    Art,
    /// May trade 4 life tokens for the top salary.
    Sport,
    /// Collects 50,000 when the spinner stops between two numbers.
    Repair,
    /// Collects the doctor's career fields.
    Medicine,
    /// Two 8s, 9s or 10s in a row bring the top salary.
    Stage,
    /// Collects 10,000 from a player who spins a 10.
    Police,
    /// Collects 5,000 when another player buys stock or insurance.
    Sales,
    /// Draws a second career once everyone has one: its perk, not its salary.
    Teaching,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Career {
    pub name: &'static str,
    /// Only a player who studied may keep it.
    pub degree: bool,
    pub perk: Perk,
}

pub const CAREERS: [Career; 9] = [
    Career { name: "Buchhaltung", degree: true, perk: Perk::Taxes },
    Career { name: "Kunst", degree: false, perk: Perk::Art },
    Career { name: "Profisport", degree: false, perk: Perk::Sport },
    Career { name: "IT-Beratung", degree: false, perk: Perk::Repair },
    Career { name: "Medizin", degree: true, perk: Perk::Medicine },
    Career { name: "Bühne", degree: false, perk: Perk::Stage },
    Career { name: "Polizei", degree: false, perk: Perk::Police },
    Career { name: "Vertrieb", degree: false, perk: Perk::Sales },
    Career { name: "Lehramt", degree: false, perk: Perk::Teaching },
];

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Salary {
    /// Paid at every payday field passed or landed on.
    pub pay: i64,
    /// Due on the board's tax fields.
    pub taxes: i64,
}

/// Lowest to highest; the last is the top salary the Sport and Stage perks reach for.
pub const SALARIES: [Salary; 9] = [
    Salary { pay: 20_000, taxes: 5_000 },
    Salary { pay: 30_000, taxes: 10_000 },
    Salary { pay: 40_000, taxes: 15_000 },
    Salary { pay: 50_000, taxes: 20_000 },
    Salary { pay: 60_000, taxes: 25_000 },
    Salary { pay: 70_000, taxes: 30_000 },
    Salary { pay: 80_000, taxes: 35_000 },
    Salary { pay: 90_000, taxes: 40_000 },
    Salary { pay: 100_000, taxes: 45_000 },
];

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct House {
    pub name: &'static str,
    pub price: i64,
    pub insurance: i64,
}

pub const HOUSES: [House; 9] = [
    House { name: "Schiefes Häuschen", price: 40_000, insurance: 10_000 },
    House { name: "Wohnwagen", price: 60_000, insurance: 15_000 },
    House { name: "Blockhütte", price: 80_000, insurance: 20_000 },
    House { name: "Stadtwohnung", price: 100_000, insurance: 25_000 },
    House { name: "Fachwerkhaus", price: 120_000, insurance: 30_000 },
    House { name: "Strandhaus", price: 140_000, insurance: 35_000 },
    House { name: "Bauernhof", price: 160_000, insurance: 40_000 },
    House { name: "Doppelhaus", price: 180_000, insurance: 45_000 },
    House { name: "Jugendstilvilla", price: 200_000, insurance: 50_000 },
];

/// Stock cards carry the spinner numbers 1–9: the owner collects whenever anyone spins theirs.
pub const STOCKS: [u8; 9] = [1, 2, 3, 4, 5, 6, 7, 8, 9];

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Token {
    pub text: &'static str,
    pub value: i64,
}

/// The original's 25 life tokens (650,000 in all), with our own achievements.
pub const TOKENS: [Token; 25] = [
    Token { text: "Eine Sinfonie komponiert", value: 30_000 },
    Token { text: "Eine neue Eissorte erfunden", value: 10_000 },
    Token { text: "Einen Journalistenpreis gewonnen", value: 20_000 },
    Token { text: "Einen Kometen entdeckt", value: 20_000 },
    Token { text: "Eine neue Energiequelle gefunden", value: 40_000 },
    Token { text: "Über den Ärmelkanal geschwommen", value: 10_000 },
    Token { text: "Zur Bürgermeisterin gewählt", value: 50_000 },
    Token { text: "Eine bessere Mausefalle gebaut", value: 10_000 },
    Token { text: "Ein Meisterwerk gemalt", value: 30_000 },
    Token { text: "Einen Tanzwettbewerb gewonnen", value: 10_000 },
    Token { text: "Einen neuen Computer entworfen", value: 20_000 },
    Token { text: "Für das Lebenswerk geehrt", value: 20_000 },
    Token { text: "Das Familienpferd gewinnt das Derby", value: 30_000 },
    Token { text: "Ein Mittel gegen Schnupfen gefunden", value: 40_000 },
    Token { text: "Einen Meilen-Weltrekord gelaufen", value: 10_000 },
    Token { text: "Einen Preis für Menschlichkeit erhalten", value: 20_000 },
    Token { text: "Ein Spielzeug erfunden, das jeder will", value: 30_000 },
    Token { text: "Eine neue Lehrmethode entwickelt", value: 40_000 },
    Token { text: "Den Fluss wieder sauber gemacht", value: 50_000 },
    Token { text: "Den Mount Everest bestiegen", value: 10_000 },
    Token { text: "Eine neue Sportart erfunden", value: 10_000 },
    Token { text: "Den Friedenspreis erhalten", value: 50_000 },
    Token { text: "Einen großen Roman geschrieben", value: 30_000 },
    Token { text: "Eine bedrohte Art gerettet", value: 40_000 },
    Token { text: "Eine Bio-Ladenkette gegründet", value: 20_000 },
];

/// More for the stretched board: 25 × 1.3, rounded down to 32 so the pile runs
/// out about when the original's does (bots: 83 % of the game against 76 %;
/// with 33 at 86 %), keeping the original's spread of values.
pub const EXTRA_TOKENS: [Token; 7] = [
    Token { text: "Einen Chor gegründet", value: 10_000 },
    Token { text: "Einen Garten voller Bienen angelegt", value: 10_000 },
    Token { text: "Ein Dorffest organisiert", value: 20_000 },
    Token { text: "Eine Brieffreundschaft fürs Leben", value: 20_000 },
    Token { text: "Ein Kinderbuch illustriert", value: 30_000 },
    Token { text: "Einen Hof vor dem Abriss gerettet", value: 30_000 },
    Token { text: "Ein Mittel gegen Heuschnupfen gefunden", value: 40_000 },
];
