import { useState } from "react";
import { useServerNow, type Connection } from "../net";
import type { PriceKeypad } from "../protocol/PriceKeypad";
import { euro, toCents, typeKey } from "../ui/price";
import "./price-keypad.css";

const KEYS = ["1", "2", "3", "4", "5", "6", "7", "8", "9", ",", "0", "⌫"];

/** Preisfrage on the phone: the article, a calculator keypad and one send. Locked once the guess is out. */
export function PhonePriceKeypad({ keypad, conn }: { keypad: PriceKeypad; conn: Connection }) {
  const now = useServerNow(conn);
  const [text, setText] = useState("");
  const [sentHere, setSentHere] = useState<number | null>(null);
  const sent = keypad.sent ?? sentHere;
  const open = now >= keypad.opens_at && now < keypad.closes_at;
  const cents = toCents(text);
  const left = Math.max(0, keypad.closes_at - Math.max(now, keypad.opens_at));
  const send = () => {
    if (!open || cents === null || sent !== null) return;
    setSentHere(cents);
    conn.send({ t: "action", action: { t: "guess_price", cents } });
  };

  return (
    <section className="screen price-keypad">
      <header className="pk-head">
        <img className="pk-thumb" src={keypad.article.photo} alt="" />
        <div>
          <p className="pk-name">{keypad.article.name}</p>
          <p className="pk-round">
            Artikel {keypad.round} von {keypad.rounds}
            {open && sent === null ? ` · noch ${Math.ceil(left / 1000)} s` : ""}
          </p>
        </div>
      </header>
      <div className="meter" aria-hidden="true">
        <div className="meter-fill" style={{ transform: `scaleX(${left / (keypad.closes_at - keypad.opens_at)})` }} />
      </div>
      {sent !== null ? (
        <div className="pk-sent">
          <p className="screen-title">Dein Tipp</p>
          <p className="pk-sent-price">{euro(sent)}</p>
          <p className="screen-text">Warte auf die anderen …</p>
        </div>
      ) : (
        <>
          <output className={`pk-display${text ? "" : " is-empty"}`} aria-live="polite">
            {text || "0,00"} €
          </output>
          <div className="pk-keys">
            {KEYS.map((key) => (
              <button key={key} className="pk-key" disabled={!open} onClick={() => setText(typeKey(text, key, keypad.max_cents))}>
                {key}
              </button>
            ))}
          </div>
          <button className="cta pk-send" disabled={!open || cents === null} onClick={send}>
            {now < keypad.opens_at ? "Gleich geht's los …" : "Tipp abgeben"}
          </button>
        </>
      )}
    </section>
  );
}
