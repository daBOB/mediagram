//! The groups that store a profile's place in the household, the web's
//! `roles-schema.ts` statement for statement. Their own file only because
//! `schema.rs` is at its line limit; `GROUPS` lists each like every other
//! group, so the version is still `GROUPS.len()`.

/// v7 -> v8: who may manage whom — the web's v12. A kid carries its own
/// limit (6 or 12) and the grown-up who made it; one grown-up may be the
/// admin; a grown-up may hold a PIN, stored as its salted hash, never the
/// digits. `parent_id` is not a foreign key: a parent removed here must leave
/// a kid another device still knows readable. Every existing kid was FSK 12,
/// the one limit there was; every existing mark stays "from 12" (`age` NULL).
pub(super) const ROLES: &[&str] = &[
    "ALTER TABLE profiles ADD COLUMN kids_age INTEGER",
    "ALTER TABLE profiles ADD COLUMN kids_age_updated_at INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE profiles ADD COLUMN parent_id TEXT",
    "ALTER TABLE profiles ADD COLUMN admin_claimed_at INTEGER",
    "ALTER TABLE profiles ADD COLUMN pin_hash TEXT",
    "ALTER TABLE profiles ADD COLUMN pin_salt TEXT",
    "ALTER TABLE profiles ADD COLUMN pin_updated_at INTEGER NOT NULL DEFAULT 0",
    "UPDATE profiles SET kids_age = 12 WHERE kids = 1",
    "ALTER TABLE kids ADD COLUMN age INTEGER",
];

/// v8 -> v9: whether a PIN was proven — set by someone who knew the one
/// before it, or the admin's reset — the web's v13. A first PIN never
/// replaces one set earlier elsewhere (`merge::roles`); every PIN already
/// stored is a first one.
pub(super) const PIN_PROOF: &[&str] = &["ALTER TABLE profiles ADD COLUMN pin_proven INTEGER NOT NULL DEFAULT 0"];

/// v9 -> v10: a kid's daily limit in minutes (NULL: none), and when chosen —
/// the web's v14.
pub(super) const DAILY_LIMIT: &[&str] = &[
    "ALTER TABLE profiles ADD COLUMN daily_limit INTEGER",
    "ALTER TABLE profiles ADD COLUMN daily_limit_updated_at INTEGER NOT NULL DEFAULT 0",
];

/// v10 -> v11: the sections of the library a profile does not see, a JSON
/// list of section keys, dated so the newest whole list wins across devices
/// — the web's v15. `'[]'` hides nothing, so every profile already stored
/// sees what it saw before.
pub(super) const HIDDEN_SECTIONS: &[&str] = &[
    "ALTER TABLE profiles ADD COLUMN hidden_sections TEXT NOT NULL DEFAULT '[]'",
    "ALTER TABLE profiles ADD COLUMN hidden_sections_updated_at INTEGER NOT NULL DEFAULT 0",
];

/// v11 -> v12: households — the web's v16. Home has the same id on every
/// device, so each migrates alone and they still agree, and nothing in it is
/// dated: a device that never changes it says nothing new to an older build.
/// Every profile already stored is in Home, undated for the same reason.
/// `admin_id` is not a foreign key, as `parent_id` is not: an admin removed
/// here may be known elsewhere.
pub(super) const HOUSEHOLDS: &[&str] = &[
    "CREATE TABLE IF NOT EXISTS households(
       id TEXT PRIMARY KEY,
       name TEXT NOT NULL,
       name_updated_at INTEGER NOT NULL DEFAULT 0,
       admin_id TEXT,
       admin_updated_at INTEGER NOT NULL DEFAULT 0,
       removed INTEGER NOT NULL DEFAULT 0
     )",
    "INSERT OR IGNORE INTO households(id, name) VALUES ('home', 'Home')",
    "ALTER TABLE profiles ADD COLUMN household_id TEXT NOT NULL DEFAULT 'home'",
    "ALTER TABLE profiles ADD COLUMN household_updated_at INTEGER NOT NULL DEFAULT 0",
];

/// v12 -> v13: a profile's renames — the web's v17. Rows are keyed by id, so
/// a rename is the name updated in place, dated so the newest rename names
/// the person on every device; `former_names` is a sorted JSON list of the
/// normalised names it had, as `hidden_sections` is, and none of them may be
/// taken by a new profile. Every profile already stored was never renamed,
/// so an untouched install writes the document it wrote before.
pub(super) const RENAMES: &[&str] = &[
    "ALTER TABLE profiles ADD COLUMN renamed_at INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE profiles ADD COLUMN former_names TEXT NOT NULL DEFAULT '[]'",
];
