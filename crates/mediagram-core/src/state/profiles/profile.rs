//! A profile as the app sees it.

use super::role_rows;

/// A profile as the app sees it. Says whether there is a PIN, never what.
/// Every field after `name` is defaulted in the generated Kotlin (uniffi
/// field defaults), so `Profile(id, name)` call sites keep compiling.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Profile {
    pub id: String,
    pub name: String,
    /// Sees only what its own limit allows, and manages nothing.
    #[uniffi(default = false)]
    pub kids: bool,
    /// FSK 6 or 12 on a kid; `None` on a grown-up.
    #[uniffi(default = None)]
    pub kids_age: Option<u8>,
    /// Minutes a kid may watch a day, the same every day; `None` is no limit,
    /// and always on a grown-up.
    #[uniffi(default = None)]
    pub daily_limit: Option<u32>,
    /// The grown-up who made this kid. `None`, or one not here, is the admin's.
    #[uniffi(default = None)]
    pub parent_id: Option<String>,
    /// Runs its household: a grown-up, and at most one to a household.
    #[uniffi(default = false)]
    pub admin: bool,
    /// Whether a PIN is set — never the PIN, its hash or its salt.
    #[uniffi(default = false)]
    pub has_pin: bool,
    /// Section keys this profile does not see, sorted; any profile's.
    #[uniffi(default = [])]
    pub hidden_sections: Vec<String>,
    /// The owner: the one grown-up who holds the install-wide claim, and who
    /// manages households.
    #[uniffi(default = false)]
    pub owner: bool,
    /// The household it is in; a kid is wherever its parent is.
    #[uniffi(default = "home")]
    pub household: String,
}

/// The profile the app sees. A kid always has a limit, whatever the column
/// holds — 12 is what every kid saw before there was a choice — and is never
/// the admin and never has a PIN: it manages nothing and opens freely. A
/// grown-up another device later calls a kid keeps its old claim and PIN in
/// their columns; they must not make it an admin, or a profile with a PIN.
impl From<role_rows::Stored> for Profile {
    fn from(row: role_rows::Stored) -> Self {
        Profile {
            kids_age: row.kids.then_some(if row.kids_age == Some(6) { 6 } else { 12 }),
            daily_limit: row.daily_limit.filter(|_| row.kids).and_then(|m| u32::try_from(m).ok()),
            // Who runs a household is for `households::resolve` to say.
            admin: false,
            owner: row.is_owner(),
            household: row.household_id.clone(),
            has_pin: !row.kids && row.pin_hash.is_some(),
            id: row.id,
            name: row.name,
            kids: row.kids,
            parent_id: row.parent_id,
            hidden_sections: row.hidden_sections,
        }
    }
}
