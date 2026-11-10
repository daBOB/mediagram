// The numbers of a sealed bid on the phone: whole steps from one step up to the bidder's cash.

import type { BidRange } from "../protocol/BidRange";

/** `x` in whole steps, at least one step and at most what the bidder has. */
export function clampBid(bid: BidRange, x: number): number {
  const whole = Math.floor(x / bid.step) * bid.step;
  return Math.max(Math.min(bid.step, bid.max), Math.min(bid.max, whole));
}

/** `pct` per cent of the property's value, as a bid this bidder can make. */
export const bidShare = (bid: BidRange, pct: number) => clampBid(bid, (bid.value * pct) / 100);

/** Where the bid starts: what the bank would pay without a bid. */
export const startBid = (bid: BidRange) => clampBid(bid, bid.bank);
