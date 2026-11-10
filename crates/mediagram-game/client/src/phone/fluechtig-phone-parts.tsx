import type { CSSProperties } from "react";
import type { FluechtigPhone } from "../protocol/FluechtigPhone";
import type { FluechtigPiece } from "../protocol/FluechtigPiece";
import type { FluechtigTickets } from "../protocol/FluechtigTickets";
import { TICKET_COLOR, TICKET_SHORT, type TicketKey } from "../ui/fluechtig-tickets";

export const RUNNER_KEYS: TicketKey[] = ["taxi", "bus", "ubahn", "schatten", "doppel"];
export const HUNTER_KEYS: TicketKey[] = ["taxi", "bus", "ubahn"];

const tint = (c: string) => ({ "--c": c }) as CSSProperties;

/** A round badge in a player's colour: a hunter piece's number or an initial. */
export function Disc({ color, label, size = 64 }: { color: string; label: string | number; size?: number }) {
  return (
    <span className="flp-disc" style={{ background: color, width: size, height: size, fontSize: size * 0.45 }} aria-hidden="true">
      {label}
    </span>
  );
}

/** The fugitive's own header: their field, drawn as a hideout ring. */
export function RunnerHead({ phone, seenAfter }: { phone: FluechtigPhone; seenAfter: boolean }) {
  // the reveal round still pending while not after this ride: the first ride of a double move
  const when = seenAfter
    ? "Nach dieser Fahrt wirst du gesehen!"
    : phone.next_reveal === phone.round
      ? "Nach der zweiten Fahrt wirst du gesehen"
      : phone.next_reveal !== null
        ? `In Runde ${phone.next_reveal} wirst du gesehen`
        : "Du wirst nicht mehr gesehen";
  return (
    <header className="flp-head">
      <span className="flp-ring">{phone.hiding}</span>
      <div>
        <small>Dein Versteck</small>
        <b>
          Runde {phone.round} von {phone.rounds}
        </b>
        <em className={seenAfter ? "flp-alarm" : undefined}>{when}</em>
      </div>
    </header>
  );
}

export function seenLine(phone: FluechtigPhone) {
  if (phone.seen) return `Zuletzt gesehen: Feld ${phone.seen.field} (Runde ${phone.seen.round})`;
  return phone.next_reveal !== null ? `Noch nie gesehen · auftauchen in Runde ${phone.next_reveal}` : "Noch nie gesehen";
}

/** Who moves now and who after, for every phone that is not asked. */
export function WaitHead({ phone }: { phone: FluechtigPhone }) {
  const { mover, next } = phone;
  if (!mover) return null;
  const after = !next ? "" : next.you ? `Danach bist du dran${next.piece ? ` mit Jäger ${next.piece}` : ""}` : `Danach: ${next.name}`;
  return (
    <header className="flp-head">
      <Disc color={mover.color} label={mover.piece ?? [...mover.name][0] ?? "?"} />
      <div>
        <small>
          Gerade dran · Runde {phone.round} von {phone.rounds}
        </small>
        <b>{mover.piece ? `${mover.name} zieht Jäger ${mover.piece}` : `${mover.name} fährt geheim …`}</b>
        <em>{after}</em>
      </div>
    </header>
  );
}

export function Wallet({ tickets, keys }: { tickets: FluechtigTickets; keys: TicketKey[] }) {
  return (
    <div className="flp-wallet">
      {keys.map((k) => (
        <span key={k} style={tint(TICKET_COLOR[k])}>
          <b>{tickets[k]}</b>
          {TICKET_SHORT[k]}
        </span>
      ))}
    </div>
  );
}

export function Pieces({ pieces, color }: { pieces: FluechtigPiece[]; color: string }) {
  return (
    <ul className="flp-pieces">
      {pieces.map((p) => (
        <li key={p.number}>
          <Disc color={color} label={p.number} size={36} />
          <div>
            <b>
              Jäger {p.number} · Feld {p.at}
            </b>
            {HUNTER_KEYS.map((k) => (
              <span key={k} className="flp-mini" style={{ background: TICKET_COLOR[k] }}>
                {p.tickets[k]} {TICKET_SHORT[k]}
              </span>
            ))}
          </div>
        </li>
      ))}
    </ul>
  );
}

/** The fugitive's travel log, all rounds: tickets spent, fields seen, the next reveal dashed. */
export function Log({ phone }: { phone: FluechtigPhone }) {
  return (
    <ol className="flp-log" aria-label="Fahrtenbuch">
      {Array.from({ length: phone.rounds }, (_, i) => i + 1).map((r) => {
        const e = phone.log.find((x) => x.round === r);
        const cls = [r === phone.next_reveal && "flp-reveal", r === phone.round && "flp-now"].filter(Boolean).join(" ");
        return (
          <li key={r} className={cls}>
            {r}
            {e?.tickets.map((t, j) => (
              <i key={j} style={{ background: TICKET_COLOR[t] }}>
                {t === "schatten" ? "?" : TICKET_SHORT[t]}
              </i>
            ))}
            {e?.seen_at != null && <b>{e.seen_at}</b>}
          </li>
        );
      })}
    </ol>
  );
}

/** Fields the fugitive may be on; past a dozen only their number, the TV rings them all. */
export function Hideouts({ phone }: { phone: FluechtigPhone }) {
  const n = phone.hideouts.length;
  if (n === 0) return null;
  if (n > 12) {
    return (
      <div className="flp-maybe">
        {phone.hiding !== null ? `Die Jäger suchen dich auf ${n} Feldern.` : `${phone.fugitive} kann auf ${n} Feldern sein.`} Der Fernseher zeigt sie.
      </div>
    );
  }
  return (
    <div className="flp-maybe">
      {phone.hiding !== null ? "Hier vermuten dich die Jäger:" : `${phone.fugitive} kann sein auf`}
      {phone.hideouts.map((f) => (
        <span key={f}>{f}</span>
      ))}
    </div>
  );
}
