/**
 * A grown-up's PIN: four digits, kept as a salted SHA-256.
 *
 * Not a password hash in any strong sense, and not meant as one: anyone
 * holding the sync document can try all ten thousand. That document lives in
 * the household's own channel, readable only by the account that owns the
 * library, and the people a PIN is for cannot read it — a slow hash would buy
 * nothing against that. The Android core computes the same string for the
 * same salt and PIN (`pin-hash.json`), which is what lets a PIN set on the
 * television open the profile on the laptop.
 */

import { createHash, randomBytes, timingSafeEqual } from "node:crypto";

/** Exactly four ASCII digits; anything else is refused where it arrives. */
export function validPin(pin: unknown): pin is string {
  return typeof pin === "string" && /^[0-9]{4}$/.test(pin);
}

export function hashPin(salt: string, pin: string): string {
  return createHash("sha256").update(salt + pin, "utf8").digest("hex");
}

/** A fresh salt, and the hash of `pin` under it. */
export function newPin(pin: string): { hash: string; salt: string } {
  const salt = randomBytes(16).toString("hex");
  return { hash: hashPin(salt, pin), salt };
}

/**
 * Whether `pin` is the one stored. Every byte of the two digests is compared
 * however early they differ; a stored hash of the wrong length cannot be
 * this PIN's, and says so before `timingSafeEqual`, which would throw.
 */
export function pinMatches(hash: string, salt: string, pin: string): boolean {
  const given = Buffer.from(hashPin(salt, pin), "hex");
  const held = Buffer.from(hash, "hex");
  return given.length === held.length && timingSafeEqual(given, held);
}
