/**
 * Own writes of a profile's PIN, age limit and daily limit, split from the
 * rows' reads (`profiles.ts`) to keep that file's size down.
 */

import type { Database } from "bun:sqlite";
import { newPin } from "./profiles-pin";

/**
 * Own writes of a PIN or a limit are dated at least a millisecond past the
 * last change: an imported one can carry another device's clock, and this
 * one running behind must not write a change that loses to the value it
 * replaced. Clamped below the largest time a peer keeps, as `setWatched` is.
 */
const LAST_STAMP = Number.MAX_SAFE_INTEGER - 1;

/** A fresh salt and hash for `pin`; `proven` unless it is a first PIN, set where there was none. */
export function writePin(db: Database, id: string, pin: string, proven: boolean): void {
  const { hash, salt } = newPin(pin);
  db.query(`UPDATE profiles SET pin_hash = ?2, pin_salt = ?3, pin_proven = ?6,
              pin_updated_at = MAX(?4, MIN(pin_updated_at, ?5) + 1) WHERE id = ?1`)
    .run(id, hash, salt, Date.now(), LAST_STAMP, proven ? 1 : 0);
}

export function writeKidsAge(db: Database, id: string, age: 6 | 12): void {
  db.query(`UPDATE profiles SET kids_age = ?2,
              kids_age_updated_at = MAX(?3, MIN(kids_age_updated_at, ?4) + 1) WHERE id = ?1`)
    .run(id, age, Date.now(), LAST_STAMP);
}

/** `minutes` null takes the limit off; either way it is dated, so it wins over an older one elsewhere. */
export function writeDailyLimit(db: Database, id: string, minutes: number | null): void {
  db.query(`UPDATE profiles SET daily_limit = ?2,
              daily_limit_updated_at = MAX(?3, MIN(daily_limit_updated_at, ?4) + 1) WHERE id = ?1`)
    .run(id, minutes, Date.now(), LAST_STAMP);
}
