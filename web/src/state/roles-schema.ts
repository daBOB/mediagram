/**
 * v11 -> v12: who may manage whom. A profile is still a kid or a grown-up;
 * a kid now carries its own limit (6 or 12) and the grown-up who made it,
 * one grown-up may be the household's admin, and a grown-up may hold a PIN.
 *
 * Its own file only because `schema.ts` is at its line limit; `GROUPS` lists
 * it like every other group, so the version is still `GROUPS.length`.
 *
 * Columns rather than tables, like `kids` in v7: each is one fact about one
 * profile. `parent_id` is deliberately not a foreign key — a parent removed
 * here must leave a kid another device still knows readable. Every kid that
 * exists was FSK 12 in code, so it says so now; every hand mark keeps
 * meaning "from 12", which is what a `NULL` age says.
 */
export const ROLES_GROUP: readonly string[] = [
  `ALTER TABLE profiles ADD COLUMN kids_age INTEGER`,
  `ALTER TABLE profiles ADD COLUMN kids_age_updated_at INTEGER NOT NULL DEFAULT 0`,
  `ALTER TABLE profiles ADD COLUMN parent_id TEXT`,
  `ALTER TABLE profiles ADD COLUMN admin_claimed_at INTEGER`,
  `ALTER TABLE profiles ADD COLUMN pin_hash TEXT`,
  `ALTER TABLE profiles ADD COLUMN pin_salt TEXT`,
  `ALTER TABLE profiles ADD COLUMN pin_updated_at INTEGER NOT NULL DEFAULT 0`,
  `UPDATE profiles SET kids_age = 12 WHERE kids = 1`,
  `ALTER TABLE kids ADD COLUMN age INTEGER`,
];
