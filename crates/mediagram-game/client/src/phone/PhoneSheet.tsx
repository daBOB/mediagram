import { useEffect, useState } from "react";
import { useServerNow, type Connection } from "../net";
import type { PhoneScreen } from "../protocol/PhoneScreen";
import { ANSWER_MARKS } from "../ui/answers";

type Sheet = Extract<PhoneScreen, { kind: "sheet" }>;

/** Keeps a tapped button dead this long, so a double tap cannot answer the next question too. */
const TAP_LOCK_MS = 700;

/**
 * The status sheet of the money games: header lines (cash, holdings), a
 * prompt and, when it is this player's call, options answered with `choose`.
 * It stays up for everyone all game; without options it only informs.
 */
export function PhoneSheet({ screen, conn }: { screen: Sheet; conn: Connection }) {
  const now = useServerNow(conn);
  const [lockedUntil, setLockedUntil] = useState(0);
  // a new question starts unlocked
  useEffect(() => setLockedUntil(0), [screen.prompt, screen.opens_at]);
  const picked = screen.picked;
  const live = screen.options.length > 0 && now >= screen.opens_at && picked === null && Date.now() >= lockedUntil;

  const pick = (index: number) => {
    if (!live) return;
    setLockedUntil(Date.now() + TAP_LOCK_MS);
    navigator.vibrate?.(25);
    conn.send({ t: "action", action: { t: "choose", index, at: conn.serverNow() } });
  };

  return (
    <section className="screen sheet">
      <dl className="sheet-lines">
        {screen.lines.map((l, i) => (
          <div key={i} className="sheet-line">
            <dt>{l.label}</dt>
            <dd>{l.value}</dd>
          </div>
        ))}
      </dl>
      {screen.prompt && <p className="sheet-prompt">{screen.prompt}</p>}
      <div className="sheet-keys">
        {screen.options.map((o, i) => (
          <button
            key={i}
            className={`key key-${o.color}${picked === i ? " key-picked" : ""}${picked !== null && picked !== i ? " key-dim" : ""}`}
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
    </section>
  );
}
