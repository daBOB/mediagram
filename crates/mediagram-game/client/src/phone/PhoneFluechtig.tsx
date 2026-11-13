import { useEffect, useRef, useState, type CSSProperties } from "react";
import type { Connection } from "../net";
import type { FluechtigPhone } from "../protocol/FluechtigPhone";
import type { FluechtigTicket } from "../protocol/FluechtigTicket";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import { TICKET_COLOR, TICKET_NAME, TICKET_SHORT } from "../ui/fluechtig-tickets";
import { RoleBar, RoleCard, useRoleTheme } from "./FluechtigRole";
import { Disc, HUNTER_KEYS, Hideouts, Log, Pieces, RUNNER_KEYS, RunnerHead, WaitHead, Wallet, seenLine } from "./fluechtig-phone-parts";
import "./fluechtig-phone.css";

/**
 * After a tap the buttons stay dead until the next question arrives (a new
 * question unlocks them), so a slow screen cannot let a second tap answer it;
 * this long at most, in case the host ignored the tap.
 */
const TAP_LOCK_MS = 3000;

/** A shadow ride reaches every field, so a tile names it only when nothing else gets there. */
const chips = (ways: FluechtigTicket[]) => (ways.some((w) => w !== "schatten") ? ways.filter((w) => w !== "schatten") : ways);

/**
 * Flüchtig on a phone. The mover picks where to from number tiles drawn like
 * the TV's fields, then, when more than one ticket gets there, the ticket,
 * which says what the room will see. Every other phone reads the chase: who
 * moves, its own pieces or hideout, the travel log and where the fugitive may be.
 */
export function PhoneFluechtig({ phone, me, conn }: { phone: FluechtigPhone; me: PlayerInfo; conn: Connection }) {
  const { ask } = phone;
  const runs = phone.hiding !== null;
  useRoleTheme(runs);
  const opensAt = ask?.opens_at ?? null;
  // a timer opens the question; no clock re-renders the screen all game long
  const [live, setLive] = useState(false);
  const lockedUntil = useRef(0);
  useEffect(() => {
    lockedUntil.current = 0; // a new question starts unlocked
    if (opensAt === null) {
      setLive(false);
      return;
    }
    const wait = opensAt - conn.serverNow();
    setLive(wait <= 0);
    if (wait <= 0) return;
    const t = window.setTimeout(() => setLive(true), wait);
    return () => clearTimeout(t);
  }, [opensAt, ask?.prompt, conn]);

  const pick = (index: number) => {
    if (!live || Date.now() < lockedUntil.current) return;
    lockedUntil.current = Date.now() + TAP_LOCK_MS;
    navigator.vibrate?.(25);
    conn.send({ t: "action", action: { t: "choose", index, at: conn.serverNow() } });
  };
  // a click, not a pointer-down: a scroll that starts on a tile must not move the piece
  const press = (index: number) => ({ type: "button" as const, "aria-disabled": !live, onClick: () => pick(index) });

  if (phone.intro) return <RoleCard phone={phone} me={me} conn={conn} />;

  if (!ask) {
    return (
      <section className="flp">
        <RoleBar phone={phone} />
        <WaitHead phone={phone} />
        {runs ? (
          <>
            <RunnerHead phone={phone} seenAfter={false} />
            <Wallet tickets={phone.fugitive_tickets} keys={RUNNER_KEYS} />
          </>
        ) : (
          <>
            <h3>{phone.pieces.length > 1 ? "Deine Figuren" : "Deine Figur"}</h3>
            <Pieces pieces={phone.pieces} color={me.color} avatar={me.avatar} />
          </>
        )}
        <h3>{runs ? "Dein Fahrtenbuch" : `Fahrtenbuch von ${phone.fugitive}`}</h3>
        <Log phone={phone} />
        <Hideouts phone={phone} />
      </section>
    );
  }

  const head = runs ? (
    <RunnerHead phone={phone} seenAfter={ask.seen_after} />
  ) : (
    <header className="flp-head">
      <Disc color={me.color} label={phone.mover?.piece ?? [...me.name][0] ?? "?"} avatar={me.avatar} />
      <div>
        <small>
          Du ziehst · Runde {phone.round} von {phone.rounds}
        </small>
        <b>{phone.mover?.piece ? `Figur ${phone.mover.piece} · Feld ${ask.at}` : `Deine Figur · Feld ${ask.at}`}</b>
        <em>{seenLine(phone)}</em>
      </div>
    </header>
  );

  if (!ask.options.some((o) => o.kind === "field")) {
    const back = ask.options.findIndex((o) => o.kind === "back");
    return (
      <section className={`flp${live ? "" : " flp-shut"}`}>
        <RoleBar phone={phone} />
        {head}
        <h2 className="flp-prompt">{ask.prompt}</h2>
        <div className="flp-tickets">
          {ask.options.map(
            (o, i) =>
              o.kind === "ticket" && (
                <button key={i} className="flp-ticket" style={{ "--c": TICKET_COLOR[o.ticket] } as CSSProperties} {...press(i)}>
                  <b>{TICKET_NAME[o.ticket]}</b>
                  <span>noch {o.left}</span>
                  {runs && <small>{o.ticket === "schatten" ? "Niemand sieht, womit du fährst" : `Alle sehen: ${TICKET_NAME[o.ticket]}`}</small>}
                </button>
              ),
          )}
        </div>
        {back >= 0 && (
          <button className="flp-back" {...press(back)}>
            Zurück
          </button>
        )}
      </section>
    );
  }

  return (
    <section className={`flp${live ? "" : " flp-shut"}`}>
      <RoleBar phone={phone} />
      {head}
      {!runs && <Hideouts phone={phone} />}
      <Wallet tickets={ask.tickets} keys={runs ? RUNNER_KEYS : HUNTER_KEYS} />
      <h2 className="flp-prompt">{ask.prompt}</h2>
      <div className="flp-tiles">
        {ask.options.map(
          (o, i) =>
            o.kind === "field" && (
              <button key={i} className="flp-tile" style={{ "--ring": TICKET_COLOR[o.ring] } as CSSProperties} {...press(i)}>
                <b>{o.field}</b>
                <span>
                  {chips(o.ways).map((w) => (
                    <i key={w} style={{ background: TICKET_COLOR[w] }}>
                      {TICKET_SHORT[w]}
                    </i>
                  ))}
                </span>
              </button>
            ),
        )}
      </div>
      {ask.options.map(
        (o, i) =>
          o.kind === "double" && (
            <button key={i} className="flp-wide" {...press(i)}>
              Doppelzug ansagen
              <small>noch {o.left} · zwei Fahrten hintereinander</small>
            </button>
          ),
      )}
    </section>
  );
}
