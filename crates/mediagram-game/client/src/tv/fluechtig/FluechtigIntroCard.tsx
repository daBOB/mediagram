import { useState, type CSSProperties, type ReactNode } from "react";
import type { FluechtigIntro } from "../../protocol/FluechtigIntro";
import type { FluechtigScene } from "../../protocol/FluechtigScene";
import type { FluechtigTicket } from "../../protocol/FluechtigTicket";
import type { PlayerInfo } from "../../protocol/PlayerInfo";
import { andList, genitive, ticketLine } from "../../ui/fluechtig-naming";
import { avatarFigureUrl } from "../../ui/Token";
import { HideoutMark, LINE_NAME, LineSwatch } from "./FluechtigLegend";
import { fieldRings } from "./plan-lines";
import "./fluechtig-intro.css";

/** A highlighted word on a card. */
const Y = ({ children }: { children: ReactNode }) => <b className="fl-y">{children}</b>;

/** A player's standing avatar figure with their name, or their initial without one. */
function Figure({ player }: { player?: PlayerInfo }) {
  const [failed, setFailed] = useState(false);
  const name = player?.name ?? "Jemand";
  return (
    <span className="fl-figure" style={{ "--c": player?.color ?? "#888" } as CSSProperties}>
      {player?.avatar && !failed ? (
        <img src={avatarFigureUrl(player.avatar)} alt="" onError={() => setFailed(true)} />
      ) : (
        <i>{[...name.trim()][0]?.toUpperCase() ?? "?"}</i>
      )}
      <b>{name}</b>
    </span>
  );
}

/** A field ring as the plan draws it. */
function Ring({ kind, field }: { kind: FluechtigTicket; field: number }) {
  return (
    <svg width={28} height={28} viewBox="-14 -14 28 28" aria-hidden="true">
      <circle className={`fl-field fl-field-${kind}`} r={11} />
      <text className="fl-num" y={4} fontSize={10}>
        {field}
      </text>
    </svg>
  );
}

/** What each line does, for the second card. */
const LINE_TEXT: Record<FluechtigTicket, (runner: string) => ReactNode> = {
  taxi: () => <>– die Straßen. Zum nächsten Feld, überall.</>,
  bus: () => (
    <>
      – weiter. Hält nur an <Y>grünen Ringen</Y>.
    </>
  ),
  ubahn: () => (
    <>
      – quer durch die Stadt. Nur an <Y>lila Ringen</Y>.
    </>
  ),
  schatten: (runner) => (
    <>
      – über den Fluss. Nur {runner}, mit einer <Y>Schattenfahrt</Y>.
    </>
  ),
};

/**
 * One of the rules cards the TV shows before round 1, over the dimmed plan.
 * Everything it says comes from the scene: the players, the rounds, the
 * sighting rounds, the fugitive's tickets and the city's own fields.
 */
export function FluechtigIntroCard({ scene, intro, players }: { scene: FluechtigScene; intro: FluechtigIntro; players: PlayerInfo[] }) {
  const player = (id: number) => players.find((p) => p.id === id);
  const runner = player(scene.fugitive)?.name ?? "Jemand";
  const hunters = [...new Set(scene.hunters.map((h) => h.player))];
  const rings = fieldRings(scene.board);
  const lowest = (kind: FluechtigTicket) =>
    kind === "taxi" ? scene.board.spots.findIndex((_, i) => !rings.has(i + 1)) + 1 : Math.min(...[...rings].filter(([, k]) => k === kind).map(([f]) => f));

  const body = [
    <>
      <div className="fl-cast">
        <div className="fl-side fl-side-run">
          <Figure player={player(scene.fugitive)} />
          <p>
            <Y>flüchtig</Y> · unsichtbar
          </p>
        </div>
        <div className="fl-side">
          <span className={`fl-row${hunters.length > 3 ? " fl-row-many" : ""}`}>
            {hunters.map((id) => (
              <Figure key={id} player={player(id)} />
            ))}
          </span>
          <p>
            <Y>Jäger</Y> · {hunters.length === 1 ? "mit zwei Figuren" : "jeder mit einer Figur"}
          </p>
        </div>
      </div>
      <ul>
        <li>
          <span className="fl-dot" />
          <span>
            Fährt ein Jäger auf <Y>{genitive(runner)} Feld</Y>, ist {runner} gefangen.
          </span>
        </li>
        <li>
          <span className="fl-dot" />
          <span>
            Hält {runner} <Y>{scene.rounds} Runden</Y> durch, ist {runner} <Y>entkommen</Y>.
          </span>
        </li>
      </ul>
    </>,
    <>
      <p className="fl-lead">
        Jede Fahrt kostet ein <Y>Ticket</Y> und geht bis zum <Y>nächsten Halt</Y> der Linie.
      </p>
      <table className="fl-lines">
        <tbody>
          {(["taxi", "bus", "ubahn", "schatten"] as const).map((kind) => {
            const field = lowest(kind);
            if (!Number.isFinite(field) || field < 1) return null;
            return (
              <tr key={kind}>
                <td>
                  <LineSwatch kind={kind} />
                </td>
                <td>
                  <Ring kind={kind} field={field} />
                </td>
                <td>
                  <Y>{LINE_NAME[kind]}</Y> {LINE_TEXT[kind](runner)}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </>,
    <ul>
      <li>
        <span className="fl-dot" />
        <span>
          Alle sehen nur, <Y>womit</Y> {runner} fährt: Jedes Ticket kommt unten ins <Y>Fahrtenbuch</Y>.
        </span>
      </li>
      <li>
        <span className="fl-dot" />
        <span>
          In Runde <Y>{andList(scene.reveal_rounds)}</Y> taucht {runner} <Y>auf</Y> und steht kurz auf dem Plan.
        </span>
      </li>
      <li>
        <HideoutMark size={26} />
        <span>
          <Y>Orange Kreise</Y>: Hier kann {runner} gerade sein.
        </span>
      </li>
      <li>
        <span className="fl-dot" />
        <span>{ticketLine(scene.fugitive_tickets.schatten, scene.fugitive_tickets.doppel, runner)}</span>
      </li>
    </ul>,
  ];
  const titles = ["Wer ist wer?", "Die Linien", "Unsichtbar – fast immer"];
  const i = Math.min(intro.card, titles.length) - 1;

  return (
    <div className={`fl-intro${intro.card === 3 ? " fl-intro-log" : ""}`}>
      <div className="fl-intro-dim" />
      {/* five hunters need a wider first card to stand side by side */}
      <article className={`fl-card${intro.card === 1 && hunters.length > 3 ? " fl-card-wide" : ""}`}>
        <small>
          SO GEHT FLÜCHTIG · {intro.card} / {intro.cards}
        </small>
        <h1>{titles[i]}</h1>
        {body[i]}
        <footer>
          {Array.from({ length: intro.cards }, (_, n) => (
            <i
              key={`${n}-${intro.card}`}
              className={n + 1 < intro.card ? "fl-done" : n + 1 === intro.card ? "fl-now" : ""}
              style={{ "--ms": `${intro.closes_at - intro.opened_at}ms` } as CSSProperties}
            />
          ))}
          {intro.leader && <span>{intro.leader} kann am Handy überspringen</span>}
        </footer>
      </article>
    </div>
  );
}
