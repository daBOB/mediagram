import { useEffect, useState, type CSSProperties } from "react";
import { useServerNow, type Connection } from "../net";
import type { HotelCard } from "../protocol/HotelCard";
import type { PhoneScreen } from "../protocol/PhoneScreen";
import { ANSWER_MARKS } from "../ui/answers";
import { SheetBid } from "./SheetBid";

type Sheet = Extract<PhoneScreen, { kind: "sheet" }>;

/** Keeps a tapped button dead this long, so a double tap cannot answer the next question too. */
const TAP_LOCK_MS = 700;

/**
 * The money game's sheet: the player's cash and standing, a prompt with its
 * clock and, when it is this player's call, options answered with `choose`
 * or a sealed bid; below, the player's hotels as cards. It stays up for
 * everyone all game; without options or a bid it only informs.
 */
export function PhoneSheet({ screen, conn }: { screen: Sheet; conn: Connection }) {
  const now = useServerNow(conn);
  const [lockedUntil, setLockedUntil] = useState(0);
  // a new question starts unlocked
  useEffect(() => setLockedUntil(0), [screen.prompt, screen.opens_at]);
  const closes = screen.closes_at ?? Infinity;
  const open = now >= screen.opens_at && now < closes;
  const live = screen.options.length > 0 && open && Date.now() >= lockedUntil;
  const [cash, ...notes] = screen.lines;

  const pick = (index: number) => {
    if (!live) return;
    setLockedUntil(Date.now() + TAP_LOCK_MS);
    navigator.vibrate?.(25);
    conn.send({ t: "action", action: { t: "choose", index, at: conn.serverNow() } });
  };

  return (
    <section className="screen sheet">
      <header className="sheet-top">
        {cash && (
          <p className="sheet-cash">
            {cash.value} <span>{cash.label}</span>
          </p>
        )}
        <dl className="sheet-notes">
          {notes.map((l, i) => (
            <div key={i}>
              <dt>{l.label}</dt> <dd>{l.value}</dd>
            </div>
          ))}
        </dl>
      </header>
      {screen.prompt && (
        <div className="sheet-head">
          <p className="sheet-prompt">{screen.prompt}</p>
          {screen.closes_at !== null && (
            <span className="choice-clock" aria-live="polite">
              {Math.ceil(Math.max(0, closes - Math.max(now, screen.opens_at)) / 1000)} s
            </span>
          )}
        </div>
      )}
      {screen.closes_at !== null && (
        <div className="meter" aria-hidden="true">
          <div className="meter-fill" style={{ transform: `scaleX(${Math.min(1, Math.max(0, (closes - now) / (closes - screen.opens_at)))})` }} />
        </div>
      )}
      {screen.bid ? (
        <SheetBid key={screen.opens_at} bid={screen.bid} open={open} conn={conn} />
      ) : (
        screen.options.length > 0 && (
          <div className="sheet-keys">
            {screen.options.map((o, i) => (
              <button
                key={i}
                type="button"
                className={`key key-${o.color}`}
                aria-disabled={!live}
                onPointerDown={() => pick(i)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    pick(i);
                  }
                }}
              >
                <span className="key-mark" aria-hidden="true">
                  {ANSWER_MARKS[o.color]}
                </span>
                <span className="key-label">{o.label}</span>
              </button>
            ))}
          </div>
        )
      )}
      {screen.hotels.length > 0 && (
        <ul className="hotel-cards" aria-label="Deine Hotels">
          {screen.hotels.map((h) => (
            <HotelCardView key={h.name} hotel={h} />
          ))}
        </ul>
      )}
    </section>
  );
}

function HotelCardView({ hotel: h }: { hotel: HotelCard }) {
  const entrances = h.entrances === 0 ? "kein Eingang" : h.entrances === 1 ? "1 Eingang" : `${h.entrances} Eingänge`;
  return (
    <li className="hotel-card" style={{ "--h": h.color } as CSSProperties}>
      <div className="hotel-card-head">
        <span className="hotel-card-name">{h.name}</span>
        <span className="hotel-card-pips" role="img" aria-label={`Stufe ${h.built} von ${h.stages}`}>
          {"●".repeat(h.built)}
          {"○".repeat(h.stages - h.built)}
        </span>
      </div>
      <div className="hotel-card-meta">
        <span>
          {h.built > 0 ? (
            <>
              <b>{h.rate.toLocaleString("de-DE")}</b> pro Nacht
            </>
          ) : (
            "unbebaut"
          )}
        </span>
        <span>{h.built > 0 ? entrances : ""}</span>
        <span>
          Wert <b>{h.value.toLocaleString("de-DE")}</b>
        </span>
      </div>
    </li>
  );
}
