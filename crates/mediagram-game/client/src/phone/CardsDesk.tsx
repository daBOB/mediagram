import { useEffect, useState } from "react";
import type { Connection } from "../net";
import type { ButtonNeeds } from "../protocol/ButtonNeeds";
import type { CardDesk } from "../protocol/CardDesk";
import type { DeskButton } from "../protocol/DeskButton";
import type { DeskCard } from "../protocol/DeskCard";
import { RANK_LABEL, SUIT_SYMBOL, cardKey, cardName, isRed } from "../ui/card-face";
import "./cards-desk.css";

/** More cards than this go on a second row, so each card can stay big enough to tap. */
const PER_ROW = 7;
const chunks = <T,>(items: T[], size: number): T[][] => Array.from({ length: Math.ceil(items.length / size) }, (_, r) => items.slice(r * size, (r + 1) * size));

const met = (needs: ButtonNeeds, hand: number[], table: number | null) =>
  needs === "nothing" || (needs === "one_hand" ? hand.length === 1 : hand.length >= 1 && (needs === "some_hand" || table !== null));

function Face({ item, picked, onTap }: { item: DeskCard; picked: boolean; onTap?: () => void }) {
  const { card } = item;
  const cls = `pcard${isRed(card.suit) ? " is-red" : ""}${picked ? " is-picked" : ""}${item.enabled ? "" : " is-off"}`;
  return (
    <button className={cls} aria-label={cardName(card)} aria-pressed={picked} aria-disabled={!item.enabled || !onTap} onClick={item.enabled ? onTap : undefined}>
      <span className="pcard-rank">{RANK_LABEL[card.rank]}</span>
      <span className="pcard-suit">{SUIT_SYMBOL[card.suit]}</span>
    </button>
  );
}

/**
 * The generic card desk: the host says which cards and buttons are live, the
 * phone only collects a pick and sends it back with the button's id.
 */
export function CardsDesk({ desk, conn }: { desk: CardDesk; conn: Connection }) {
  const [hand, setHand] = useState<number[]>([]);
  const [table, setTable] = useState<number | null>(null);
  // a new prompt or hand is a new decision: an old pick may point at another card
  const key = desk.prompt + desk.hand.map((h) => cardKey(h.card)).join();
  useEffect(() => {
    setHand([]);
    setTable(null);
  }, [key]);

  const several = desk.buttons.some((b) => b.needs === "some_hand");
  const tapHand = (i: number) =>
    setHand((h) => (h.includes(i) ? h.filter((x) => x !== i) : several ? [...h, i] : [i]));
  const press = (b: DeskButton) => {
    if (!met(b.needs, hand, table)) return;
    navigator.vibrate?.(25);
    conn.send({ t: "action", action: { t: "cards", button: b.id, hand, table } });
    setHand([]);
    setTable(null);
  };

  return (
    <section className="screen desk">
      <header className="desk-head">
        <h1 className="desk-prompt">{desk.prompt}</h1>
        {desk.info && <p className="desk-info">{desk.info}</p>}
      </header>
      <div className="desk-table" data-many={desk.table.length > 4}>
        {desk.table.map((t, i) => (
          <Face key={i} item={t} picked={table === i} onTap={() => setTable((x) => (x === i ? null : i))} />
        ))}
      </div>
      <div className="desk-buttons">
        {desk.buttons.map((b) => (
          <button
            key={b.id}
            className={`desk-btn${b.primary ? " is-primary" : ""}${b.suit && isRed(b.suit) ? " is-red" : ""}`}
            aria-disabled={!met(b.needs, hand, table)}
            onClick={() => press(b)}
          >
            {b.label}
          </button>
        ))}
      </div>
      <div className="desk-hand" role="group" aria-label="Deine Karten">
        {chunks(desk.hand.map((h, i) => ({ h, i })), Math.ceil(desk.hand.length / Math.ceil(desk.hand.length / PER_ROW))).map((row, r) => (
          <div className="desk-row" key={r} style={{ "--n": row.length } as React.CSSProperties}>
            {row.map(({ h, i }) => (
              <Face key={cardKey(h.card)} item={h} picked={hand.includes(i)} onTap={() => tapHand(i)} />
            ))}
          </div>
        ))}
      </div>
    </section>
  );
}
