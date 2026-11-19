//! What a player is asked: the question and the options their phone shows.

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Question {
    /// Spin, or first buy something (only at the start of a turn).
    Turn,
    Fork,
    Career,
    Salary,
    NightSchool,
    TradeSalary,
    Steal,
    HomeInsurance,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Opt {
    Spin,
    CarInsurance,
    HomeInsurance,
    Stock,
    /// Profisport: four tokens for the top salary.
    SportTrade,
    /// Lehramt: a second career for its perk.
    SecondCareer,
    Branch(u8),
    Card(usize),
    Keep,
    Player(usize),
    Yes,
    No,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Ask {
    pub player: usize,
    pub question: Question,
    pub options: Vec<Opt>,
}
