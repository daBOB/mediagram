/**
 * Deciding, validating and applying the cache's budget.
 *
 * Kept apart from `ChunkCache` itself: the store knows how to shrink to a
 * number, and this is where that number comes from and what counts as a
 * legal one — the same separation `state/settings.ts` keeps from the routes
 * that call it.
 */

import type { Settings } from "../state/settings";
import { MAX_CACHE_BUDGET_BYTES, MIN_CACHE_BUDGET_BYTES } from "../state/settings";
import type { ChunkCache } from "./store";
import type { HeldSets } from "./held";

export { MAX_CACHE_BUDGET_BYTES, MIN_CACHE_BUDGET_BYTES };

/**
 * The budget to start the cache with: the stored value first, the
 * environment next, and only then the project's own default. Env `0` (or no
 * stored value and no env) disables caching entirely, exactly as before
 * Settings existed.
 */
export function startBudget(settings: Pick<Settings, "cacheMaxBytes">, envBytes: number): number {
  return settings.cacheMaxBytes() ?? envBytes;
}

/** Whether `bytes` is a budget a viewer may set from Settings. `0` is not — that is env-only, off. */
export function validateBudget(bytes: number): boolean {
  return Number.isSafeInteger(bytes) && bytes >= MIN_CACHE_BUDGET_BYTES && bytes <= MAX_CACHE_BUDGET_BYTES;
}

/**
 * Persists `bytes`, then applies it to the live cache and refreshes what the
 * shelf believes is held in full.
 *
 * Persisted before applied: a crash between the two still starts the next
 * run at the number a viewer chose. `ChunkCache.setBudget` already coalesces
 * concurrent shrinks into one walk, so a second call here while one is
 * running waits on that walk rather than starting a second.
 */
export async function applyBudget(
  settings: Pick<Settings, "setCacheMaxBytes">,
  cache: Pick<ChunkCache, "setBudget" | "budget">,
  held: Pick<HeldSets, "refresh"> | undefined,
  bytes: number,
): Promise<{ budget: number; freedBytes: number }> {
  settings.setCacheMaxBytes(bytes);
  const { freedBytes } = await cache.setBudget(bytes);
  if (freedBytes > 0) await held?.refresh();
  return { budget: cache.budget, freedBytes };
}
