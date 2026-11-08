import { useEffect, useState, type CSSProperties, type KeyboardEvent, type PointerEvent } from "react";
import { useServerNow, type Connection } from "../net";
import type { PhoneScreen } from "../protocol/PhoneScreen";
import type { Trick } from "../protocol/Trick";
import { ANSWER_MARKS } from "../ui/answers";
import { TRICK_NAMES } from "../ui/tricks";
import { HOLD_MS, holdDone, revealed, slotOrder } from "./choice-tricks";

type Choice = Extract<PhoneScreen, { kind: "choice" }>;

/** Answer buttons that light up on every phone at `opens_at`. Kopfnuss tricks
 *  (`screen.tricks`) move, hide, mirror or glue them; without tricks they are
 *  the plain buzzer buttons every game uses. */
export function ChoiceScreen({ screen, conn }: { screen: Choice; conn: Connection }) {
  const now = useServerNow(conn);
  const [localPick, setLocalPick] = useState<number | null>(null);
  const [hold, setHold] = useState<{ index: number; since: number } | null>(null);
  const picked = screen.picked ?? localPick;
  const open = now >= screen.opens_at;
  // Without a closing time the choice waits for the player: no clock, no meter.
  const timed = screen.closes_at !== null;
  const closes = screen.closes_at ?? Infinity;
  const closed = now >= closes;
  const live = open && !closed && picked === null;
  const has = (t: Trick) => screen.tricks.includes(t);
  const glued = has("kleber");

  const pick = (index: number) => {
    if (!live) return;
    const at = conn.serverNow();
    // An untimed choice never locks on the tap alone: a tap lost in a reconnect
    // would leave the button dead with no clock to end the wait. The host
    // ignores a repeat, and its next screen replaces this one.
    if (timed) setLocalPick(index);
    // no buzz on the tap itself: the host taking the pick buzzes ("accepted")
    conn.send({ t: "action", action: { t: "choose", index, at } });
  };

  // Kleber: the pick goes out once the hold has lasted, timed at that moment.
  useEffect(() => {
    if (hold && holdDone(hold.since, now)) {
      setHold(null);
      pick(hold.index);
    }
  });

  const press = (index: number) => {
    if (!glued) return pick(index);
    if (live && !hold) setHold({ index, since: conn.serverNow() });
  };
  const release = () => setHold(null);

  const onPointerDown = (e: PointerEvent<HTMLButtonElement>, index: number) => {
    // Captured, so a Wechselbad swap under the finger does not end the hold.
    // Capture is a nicety: a WebView that refuses it must not stop the hold.
    if (glued) {
      try {
        e.currentTarget.setPointerCapture(e.pointerId);
      } catch {
        // the hold still works, it just ends if the finger slides off
      }
    }
    press(index);
  };

  const onKey = (e: KeyboardEvent, index: number) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      if (!e.repeat) press(index);
    }
  };

  const left = Math.max(0, closes - now);
  const total = closes - screen.opens_at;
  const n = screen.options.length;
  const order = has("wechselbad") && open && picked === null ? slotOrder(n, screen.opens_at, now) : [...Array(n).keys()];

  return (
    <section className="screen choice">
      <header className="choice-head">
        <span>{screen.prompt}</span>
        {timed && (
          <span className="choice-clock" aria-live="polite">
            {!open ? `Gleich … ${Math.ceil((screen.opens_at - now) / 1000)}` : picked !== null ? "Eingeloggt" : `${Math.ceil(left / 1000)} s`}
          </span>
        )}
      </header>
      {screen.tricks.length > 0 && (
        <ul className="choice-tricks" aria-label="Tricks auf deinem Handy">
          {screen.tricks.map((t) => (
            <li key={t} className={`choice-trick choice-trick-${t}`}>
              {TRICK_NAMES[t]}
            </li>
          ))}
        </ul>
      )}
      {timed && (
        <div className="meter" aria-hidden="true">
          <div className="meter-fill" style={{ transform: `scaleX(${open ? left / total : 1})` }} />
        </div>
      )}
      {/* Kleber: a 1 s hold must not open the browser's long-press menu instead. */}
      <div className={`keys${open ? " keys-open" : ""}${glued ? " keys-glued" : ""}`} onContextMenu={glued ? (e) => e.preventDefault() : undefined}>
        {order.map((i) => {
          const o = screen.options[i];
          const label = has("schneckenpost") ? revealed(o.label, screen.opens_at, now) : o.label;
          const held = hold?.index === i ? Math.min(1, (now - hold.since) / HOLD_MS) : 0;
          return (
            <button
              key={i}
              className={`key key-${o.color}${picked === i ? " key-picked" : ""}${picked !== null && picked !== i ? " key-dim" : ""}${held > 0 ? " key-holding" : ""}`}
              style={held > 0 ? ({ "--hold": held } as CSSProperties) : undefined}
              aria-disabled={!live}
              aria-label={o.label}
              onPointerDown={(e) => onPointerDown(e, i)}
              onPointerUp={glued ? release : undefined}
              onPointerCancel={glued ? release : undefined}
              onKeyDown={(e) => onKey(e, i)}
              onKeyUp={glued ? release : undefined}
            >
              <span className="key-mark" aria-hidden="true">
                {ANSWER_MARKS[o.color]}
              </span>
              <span className={`key-label${has("spiegel") ? " key-label-mirror" : ""}`} aria-hidden="true">
                {label}
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
