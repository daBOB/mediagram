import { useState } from "react";
import type { Connection } from "../net";
import type { BidRange } from "../protocol/BidRange";
import { ANSWER_MARKS } from "../ui/answers";
import { taler } from "../ui/taler";
import { bidShare, clampBid, startBid } from "./sheet-bid-math";

const SHARES = [25, 50, 75, 100];

/**
 * A sealed bid in a debt auction: − / + in single and tenfold steps, quick
 * shares of the property's value, then one "Bieten" (or "Passen"). The host
 * takes one answer per bidder; mount it keyed by the auction so each starts fresh.
 */
export function SheetBid({ bid, open, conn }: { bid: BidRange; open: boolean; conn: Connection }) {
  const [amount, setAmount] = useState(() => startBid(bid));
  const [sent, setSent] = useState(false);
  const live = open && bid.placed === null && !sent;
  const canBid = bid.max >= bid.step;

  const send = (x: number) => {
    if (!live) return;
    setSent(true);
    navigator.vibrate?.(25);
    conn.send({ t: "action", action: { t: "bid", amount: x, at: conn.serverNow() } });
  };

  if (bid.placed !== null) {
    return (
      <div className="bid">
        <p className="bid-amount">{bid.placed > 0 ? taler(bid.placed) : "Gepasst"}</p>
        <p className="bid-note">{bid.placed > 0 ? "Dein Gebot ist abgegeben – verdeckt." : "Du bietest nicht mit."}</p>
      </div>
    );
  }
  const step = (by: number) => setAmount((a) => clampBid(bid, a + by));
  return (
    <div className="bid">
      <dl className="bid-facts">
        <div>
          <dt>Wert</dt>
          <dd>{taler(bid.value)}</dd>
        </div>
        <div>
          <dt>Höchstens</dt>
          <dd>{taler(bid.max)}</dd>
        </div>
      </dl>
      {canBid && (
        <>
          <p className="bid-amount" aria-live="polite">
            {taler(amount)}
          </p>
          <p className="bid-note">verdeckt – niemand sieht dein Gebot</p>
          <div className="bid-steps">
            {[-10, -1, 1, 10].map((n) => (
              <button key={n} type="button" className="bid-step" disabled={!live} onClick={() => step(n * bid.step)}>
                {n > 0 ? "+" : "−"}
                {(Math.abs(n) * bid.step).toLocaleString("de-DE")}
              </button>
            ))}
          </div>
          <div className="bid-shares">
            {SHARES.map((pct) => (
              <button key={pct} type="button" className="bid-share" disabled={!live} onClick={() => setAmount(bidShare(bid, pct))}>
                {pct} %
              </button>
            ))}
          </div>
        </>
      )}
      <div className="sheet-keys">
        {canBid && (
          <button type="button" className="key key-blue" aria-disabled={!live} onClick={() => send(amount)}>
            <span className="key-mark" aria-hidden="true">
              {ANSWER_MARKS.blue}
            </span>
            <span className="key-label">Bieten · {taler(amount)}</span>
          </button>
        )}
        <button type="button" className="key key-pink" aria-disabled={!live} onClick={() => send(0)}>
          <span className="key-mark" aria-hidden="true">
            {ANSWER_MARKS.pink}
          </span>
          <span className="key-label">Passen</span>
        </button>
      </div>
    </div>
  );
}
