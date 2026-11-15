import { useState } from "react";
import { useServerNow, type Connection } from "../net";
import type { LetztesWortPhone } from "../protocol/LetztesWortPhone";
import { Baton } from "../ui/Baton";
import { playBuzzer } from "../ui/buzzer-sound";
import { avatarHeadUrl } from "../ui/Token";
import "./letztes-wort-phone.css";

type Of<V extends LetztesWortPhone["view"]> = Extract<LetztesWortPhone, { view: V }>;

/** A press is not repeated for this long: the host's next screen answers it. Not locked for good,
 *  so a press the host never saw (a reconnect) can be made again. */
const PRESS_LOCK_MS = 1200;
/** Presses this early count, as on the host (clock-sync error). */
const EARLY_MS = 150;

const choose = (conn: Connection, index: number) =>
  conn.send({ t: "action", action: { t: "choose", index, at: conn.serverNow() } });

/** Letztes Wort on the phone: the buzzer, the stick, or the round host's desk. */
export function PhoneLetztesWort({ phone, conn }: { phone: LetztesWortPhone; conn: Connection }) {
  switch (phone.view) {
    case "buzzer":
      // keyed by the opening, so a press that lost the race does not lock the next one
      return <Buzzer key={phone.opens_at} screen={phone} conn={conn} />;
    case "stick":
      return <Stick screen={phone} />;
    case "host":
      return <HostDesk screen={phone} conn={conn} />;
  }
}

function Buzzer({ screen, conn }: { screen: Of<"buzzer">; conn: Connection }) {
  const now = useServerNow(conn);
  const [pressedAt, setPressedAt] = useState(-Infinity);
  const live = screen.live && now >= screen.opens_at - EARLY_MS && now - pressedAt > PRESS_LOCK_MS;
  const press = () => {
    if (!live) return;
    setPressedAt(now);
    playBuzzer();
    choose(conn, 0);
  };
  return (
    <section className="screen lw-phone">
      <p className="lw-question">{screen.question}</p>
      <p className="lw-line">
        <i aria-hidden="true" />
        {screen.line}
      </p>
      <div className="lw-buzz-area">
        {/* pointerdown for speed; click for screen readers, switch and voice access (the lock stops a double count) */}
        <button className={`lw-buzzer${live ? "" : " lw-locked"}`} onPointerDown={press} onClick={press} disabled={!live}>
          {live ? "BUZZ!" : screen.live ? "Gleich …" : screen.button}
        </button>
      </div>
    </section>
  );
}

function Stick({ screen }: { screen: Of<"stick"> }) {
  return (
    <section className="screen lw-phone lw-center">
      <div className="lw-holder">
        <span className="lw-head">{screen.avatar && <img src={avatarHeadUrl(screen.avatar)} alt="" />}</span>
        <Baton className="lw-baton" />
      </div>
      <h1 className="screen-title">{screen.title}</h1>
      <p className="screen-text">{screen.text}</p>
    </section>
  );
}

function HostDesk({ screen, conn }: { screen: Of<"host">; conn: Connection }) {
  const given = screen.answers.filter((a) => a.given_by !== null).length;
  const judging = screen.floor !== null;
  const falsch = screen.answers.length;
  return (
    <section className="screen lw-phone lw-host">
      <p className="lw-question">{screen.question}</p>
      {judging ? (
        <div className="lw-desk-floor">
          <b>{screen.floor} ist dran</b>
          <span>Tipp die genannte Antwort an</span>
        </div>
      ) : (
        <p className="lw-line lw-wait">Du moderierst – warte auf einen Buzzer …</p>
      )}
      <p className="lw-hint">
        {screen.answers.length} richtige Antworten · {given} schon gesagt
      </p>
      <div className="lw-answers">
        {screen.answers.map((a, i) => (
          <button key={a.text} className={`lw-answer${a.given_by ? " lw-given" : ""}`} disabled={!judging || a.given_by !== null} onClick={() => choose(conn, i)}>
            {a.text}
            {a.given_by && <small>{a.given_by}</small>}
          </button>
        ))}
      </div>
      <div className="lw-verdicts">
        <button className="lw-falsch" disabled={!judging} onClick={() => choose(conn, falsch)}>
          Falsch
        </button>
        <button className="lw-also" disabled={!judging} onClick={() => choose(conn, falsch + 1)}>
          Auch richtig
        </button>
      </div>
    </section>
  );
}
