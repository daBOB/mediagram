import { useEffect, useState } from "react";
import type { Connection } from "../net";
import type { FluechtigPhone } from "../protocol/FluechtigPhone";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import { andList } from "../ui/fluechtig-naming";
import { avatarFigureUrl } from "../ui/Token";

/**
 * Colours the whole phone by role while Flüchtig runs: night for the
 * fugitive, police blue for the hunters. The page body is not this screen's,
 * so the colours go on `<html>` and leave with the screen (the final screen
 * is the house's green again).
 */
export function useRoleTheme(runs: boolean) {
  useEffect(() => {
    const root = document.documentElement;
    root.dataset.flRole = runs ? "runner" : "hunter";
    return () => {
      delete root.dataset.flRole;
    };
  }, [runs]);
}

/** On top of every Flüchtig screen: which side this phone plays. */
export function RoleBar({ phone }: { phone: FluechtigPhone }) {
  return phone.hiding !== null ? (
    <div className="flp-rolebar flp-rolebar-run">
      <b>Du bist flüchtig</b>
      <span>Nur du siehst dein Versteck</span>
    </div>
  ) : (
    <div className="flp-rolebar">
      <b>Du bist Jäger</b>
      <span>Fang {phone.fugitive}!</span>
    </div>
  );
}

/**
 * The phone while the rules cards run on the TV: who you are and how your
 * side wins. Only the fugitive's own card shows their hideout. The leader's
 * phone may skip the cards.
 */
export function RoleCard({ phone, me, conn }: { phone: FluechtigPhone; me: PlayerInfo; conn: Connection }) {
  const [skipped, setSkipped] = useState(false);
  const runs = phone.hiding !== null;
  const who = phone.fugitive;
  const skip = () => {
    if (skipped) return;
    setSkipped(true);
    navigator.vibrate?.(25);
    conn.send({ t: "action", action: { t: "choose", index: 0, at: conn.serverNow() } });
  };
  const fields = andList(phone.pieces.map((p) => p.at));

  return (
    <section className="flp flp-role">
      <small>FLÜCHTIG · DEINE ROLLE</small>
      {runs ? (
        <>
          <h1>Du bist flüchtig</h1>
          <div className="flp-role-box">
            <span className="flp-ring">{phone.hiding}</span>
            <p>
              Dein Versteck: <b>Feld {phone.hiding}</b> – nur du siehst es.
            </p>
          </div>
          <ul>
            <li>
              Fahr <b>unsichtbar</b> durch die Stadt. Die anderen sehen nur, <b>womit</b> du fährst.
            </li>
            <li>
              Halte <b>{phone.rounds} Runden</b> durch, ohne dass ein Jäger auf dein Feld fährt.
            </li>
            <li>
              In Runde <b>{andList(phone.reveal_rounds)}</b> tauchst du auf.
            </li>
          </ul>
        </>
      ) : (
        <>
          <h1>Du bist Jäger</h1>
          <div className="flp-role-box">
            {me.avatar ? <img src={avatarFigureUrl(me.avatar)} alt="" height={84} /> : null}
            <p>
              {phone.pieces.length > 1 ? "Deine zwei Figuren stehen" : "Deine Figur steht"}
              <br />
              auf <b>Feld {fields}</b>
            </p>
          </div>
          <ul>
            <li>
              Fang <b>{who}</b>: Fahr auf das Feld, auf dem {who} steckt.
            </li>
            <li>
              {who} ist unsichtbar. Die <b>orangen Kreise</b> auf dem Fernseher zeigen, wo {who} sein kann.
            </li>
            {phone.partners.length > 0 && (
              <li>
                <b>{andList(phone.partners)}</b> {phone.partners.length === 1 ? "jagt" : "jagen"} mit dir.
              </li>
            )}
          </ul>
        </>
      )}
      <p className="flp-tvhint">Die Regeln laufen gerade auf dem Fernseher.</p>
      {phone.may_skip && (
        <button type="button" className="flp-back flp-skip" aria-disabled={skipped} onClick={skip}>
          Regeln überspringen
        </button>
      )}
    </section>
  );
}
