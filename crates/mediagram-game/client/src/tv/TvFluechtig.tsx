import { useEffect, useState, type CSSProperties } from "react";
import type { Connection } from "../net";
import type { FluechtigScene } from "../protocol/FluechtigScene";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import { figureOf } from "../ui/fluechtig-naming";
import { TICKET_COLOR, TICKET_SHORT, type TicketKey } from "../ui/fluechtig-tickets";
import { Token } from "../ui/Token";
import { FluechtigBoard } from "./fluechtig/FluechtigBoard";
import { FluechtigIntroCard } from "./fluechtig/FluechtigIntroCard";
import { FluechtigLegend } from "./fluechtig/FluechtigLegend";
import "./fluechtig/fluechtig.css";
import { TvFinal } from "./TvFinal";
import { TvSettingsPanel } from "./settings/TvSettingsPanel";

/** How long the route stays on the plan before the awards ceremony. */
const CHEER_MS = 8000;
const RUNNER_KEYS: TicketKey[] = ["taxi", "bus", "ubahn", "schatten", "doppel"];

/**
 * Flüchtig on the TV: the city plan edge to edge, with the round, whose turn
 * and the countdown to the next sighting on top, and the fugitive's travel
 * log and tickets below. The hunters stand on the plan as their players'
 * avatar heads. The fugitive's field is never in the scene until a reveal or
 * the end, so nothing here can show it early.
 */
export function TvFluechtig({ scene, players, conn }: { scene: FluechtigScene; players: PlayerInfo[]; conn: Connection }) {
  const [cheered, setCheered] = useState(false);
  const [settings, setSettings] = useState(false);
  const over = scene.turn.turn === "over";

  // OK on the remote opens the settings over the plan; the game runs on underneath
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Enter" && !e.repeat && !cheered) setSettings(true);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [cheered]);

  useEffect(() => {
    if (over && !cheered) {
      const t = window.setTimeout(() => {
        setSettings(false);
        setCheered(true);
      }, CHEER_MS);
      return () => clearTimeout(t);
    }
    // the same table started again without this TV seeing the lobby
    if (!over && cheered) setCheered(false);
  }, [over, cheered]);

  const byId = new Map(players.map((p) => [p.id, p]));
  const name = (id: number) => byId.get(id)?.name ?? "Jemand";
  const fugitive = byId.get(scene.fugitive);
  const runner = name(scene.fugitive);
  const turn = scene.turn;
  if (turn.turn === "over" && cheered) {
    return <TvFinal players={players} conn={conn} title={turn.winner === "fugitive" ? `${runner} ist entkommen!` : "Die Jäger gewinnen!"} />;
  }

  const piece = turn.turn === "hunter" ? figureOf(scene.hunters, turn.index) : null;
  const mover = byId.get(turn.turn === "hunter" ? scene.hunters[turn.index].player : scene.fugitive);
  const status =
    turn.turn === "over"
      ? `${runner} ist ${turn.winner === "fugitive" ? "entkommen" : "erwischt"}!`
      : turn.turn === "hunter"
        ? `${name(scene.hunters[turn.index].player)} ${piece ? `zieht Figur ${piece}` : "ist dran"}`
        : `${runner} fährt geheim${turn.double_left > 0 ? " (Doppelzug)" : ""} …`;
  const left = scene.next_reveal === null ? null : scene.next_reveal - scene.round;
  const reveals = new Set(scene.reveal_rounds);
  const intro = scene.intro;

  return (
    <section className={`fl${intro?.card === 3 ? " fl-log-lit" : ""}`}>
      <FluechtigBoard scene={scene} players={players} />
      <div className="fl-hud fl-tl">
        <b>Flüchtig</b>
        <span>
          Runde {scene.round} / {scene.rounds}
        </span>
      </div>
      {!intro && (
        <div className="fl-hud fl-tc" style={{ background: mover?.color }}>
          {mover && <Token player={mover} size="sm" />}
          {status}
        </div>
      )}
      {left !== null && !over && !intro && (
        <div className="fl-hud fl-tr">
          <b>{left > 0 ? left : "!"}</b>
          <span>
            {left > 0 ? (
              <>
                {left === 1 ? "Runde" : "Runden"}, dann
                <br />
                taucht {runner} auf
              </>
            ) : (
              <>
                Nach {turn.turn === "fugitive" && turn.double_left === 2 ? "der zweiten" : "dieser"} Fahrt
                <br />
                taucht {runner} auf
              </>
            )}
          </span>
        </div>
      )}
      <div className="fl-hud fl-bl">
        <p className="fl-news" aria-live="polite">
          {scene.news[scene.news.length - 1] ?? `${runner} flieht. Die Jäger suchen.`}
        </p>
        <div className="fl-trail">
          {fugitive && <Token player={fugitive} size="sm" />}
          <ol className="fl-rounds" aria-label="Fahrtenbuch" style={{ "--slot": scene.rounds > 18 ? "15px" : "18px" } as CSSProperties}>
            {Array.from({ length: scene.rounds }, (_, i) => i + 1).map((r) => {
              const e = scene.log.find((x) => x.round === r);
              const [a, b = a] = e?.tickets.map((t) => TICKET_COLOR[t]) ?? [];
              const cls = [reveals.has(r) && "fl-reveal", r === scene.round && !over && "fl-current"].filter(Boolean).join(" ");
              return (
                <li key={r} className={cls} style={a ? ({ background: `linear-gradient(90deg, ${a} 50%, ${b} 50%)` } as CSSProperties) : undefined}>
                  {e?.seen_at ?? ""}
                </li>
              );
            })}
          </ol>
          {RUNNER_KEYS.map((k) => (
            <span key={k} className="fl-mini" style={{ background: TICKET_COLOR[k] }} title={TICKET_SHORT[k]}>
              {scene.fugitive_tickets[k]}
            </span>
          ))}
        </div>
      </div>
      <FluechtigLegend runner={runner} />
      {intro && <FluechtigIntroCard scene={scene} intro={intro} players={players} />}
      {settings && <TvSettingsPanel onClose={() => setSettings(false)} />}
    </section>
  );
}
