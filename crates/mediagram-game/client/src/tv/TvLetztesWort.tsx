import { useEffect } from "react";
import type { LetztesWortScene } from "../protocol/LetztesWortScene";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import { Baton } from "../ui/Baton";
import { playBuzzer } from "../ui/buzzer-sound";
import { Token } from "../ui/Token";
import { effectsGain, getSettings } from "./settings/tv-settings";
import "./letztes-wort/letztes-wort.css";

type Props = { scene: LetztesWortScene; players: PlayerInfo[] };

/**
 * Letztes Wort on the TV: the question, who has the floor, the stick and its holder, and the
 * table with the host and who is out. Never an answer, never a clock.
 */
export function TvLetztesWort({ scene, players }: Props) {
  const { stage } = scene;
  // Each buzz sounds once, also for a TV that joins mid-round (it hears the current one).
  const buzzKey = stage.stage === "floor" ? `${scene.round}:${scene.host}:${stage.buzz}` : null;
  useEffect(() => {
    if (buzzKey) playBuzzer(effectsGain(getSettings()));
  }, [buzzKey]);

  if (stage.stage === "final") return null; // TvApp shows the awards ceremony

  const byId = new Map(players.map((p) => [p.id, p]));
  const nameOf = (id: number) => byId.get(id)?.name ?? "Jemand";
  const host = byId.get(scene.host);
  const holder = scene.holder === null ? undefined : byId.get(scene.holder);

  return (
    <section className={`tv-quiz lw lw-${stage.stage}`}>
      <header className="tv-quiz-head">
        <span className="tv-cat">Letztes Wort</span>
        <span className="tv-round">
          Runde {scene.round} von {scene.rounds}
        </span>
      </header>

      {stage.stage === "round_end" ? (
        <div className="lw-end">
          <p className="lw-zeit">Zeit!</p>
          {stage.winner !== null && <Holder player={byId.get(stage.winner)} />}
          <p className="lw-who">{stage.winner === null ? "Niemand hatte den Stab" : `${nameOf(stage.winner)} hatte das letzte Wort`}</p>
          <p className="lw-sub">
            {stage.winner === null ? "Kein Stern in dieser Runde" : "+1 Stern"}
            {stage.next_host !== null && ` · nächste Runde moderiert ${nameOf(stage.next_host)}`}
          </p>
        </div>
      ) : (
        <div className="lw-stage">
          <article className="qcard lw-qcard">
            <p className="qtext">{scene.question}</p>
            {host && (
              <p className="lw-mod">
                <Token player={host} size="sm" /> {host.name} moderiert
              </p>
            )}
          </article>
          <div className="lw-side">
            {stage.stage === "floor" && (
              <div className="lw-floor-banner">
                {byId.get(stage.player) && <Token player={byId.get(stage.player)!} />}
                <div>
                  <b>{nameOf(stage.player)}!</b>
                  <span>hat gebuzzert – sag's laut</span>
                </div>
              </div>
            )}
            {holder ? <Holder player={holder} /> : <div className="lw-holder lw-nobody" />}
            <p className="lw-who">{holder ? `${holder.name} hat den Stab` : "Wer holt sich den Stab?"}</p>
          </div>
        </div>
      )}

      <footer className="tv-table lw-table">
        {players.map((p) => {
          const isHost = p.id === scene.host;
          const out = scene.out.includes(p.id);
          return (
            <div key={p.id} className={`tv-seat${out ? " is-out" : ""}${p.id === scene.holder ? " has-stick" : ""}${isHost ? " is-host" : ""}`}>
              <Token player={p} />
              <span className="tv-seat-name">{p.name}</span>
              <span className="lw-pts">{isHost ? "moderiert" : out ? "raus" : `★ ${p.score}`}</span>
            </div>
          );
        })}
      </footer>
    </section>
  );
}

function Holder({ player }: { player: PlayerInfo | undefined }) {
  return (
    <div className="lw-holder">
      {player && <Token player={player} size="lg" />}
      <Baton className="lw-baton" />
    </div>
  );
}
