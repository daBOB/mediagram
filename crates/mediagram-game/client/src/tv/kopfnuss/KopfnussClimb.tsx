import { useServerNow, type Connection } from "../../net";
import type { KopfnussStage } from "../../protocol/KopfnussStage";
import type { PlayerInfo } from "../../protocol/PlayerInfo";
import { Token } from "../../ui/Token";

type ClimbStage = Extract<KopfnussStage, { stage: "climb" }>;
type FactStage = Extract<KopfnussStage, { stage: "fact" }>;

/** Gipfelsturm: one lane per player, the avatar on its step, the top above. */
export function KopfnussClimb({ stage, players, conn }: { stage: ClimbStage; players: PlayerInfo[]; conn: Connection }) {
  const now = useServerNow(conn);
  const left = Math.max(0, stage.ends_at - now);
  const byId = new Map(players.map((p) => [p.id, p]));
  const share = (step: number) => `${(step / stage.steps) * 100}%`;

  return (
    <section className="kn-climb">
      <header className="tv-quiz-head">
        <span className="tv-cat">Gipfelsturm</span>
        <span className="tv-round">Antwortet auf euren Handys! · {Math.ceil(left / 1000)} s</span>
      </header>
      <div className="kn-mountain" style={{ gridTemplateColumns: `repeat(${stage.climbers.length}, 1fr)` }}>
        {stage.climbers.map((c) => {
          const p = byId.get(c.player);
          return (
            <div key={c.player} className="kn-lane">
              <div className="kn-track">
                <div className="kn-fill" style={{ height: share(c.step) }} />
                <div className="kn-climber" style={{ bottom: share(c.step) }}>
                  {p && <Token player={p} size="lg" />}
                </div>
              </div>
              <span className="kn-lane-name">{p?.name}</span>
              <span className="kn-lane-step">
                {c.step}/{stage.steps}
              </span>
            </div>
          );
        })}
      </div>
    </section>
  );
}

/** The winner on top, and the fact they take home. */
export function KopfnussFact({ stage, players }: { stage: FactStage; players: PlayerInfo[] }) {
  const winner = players.find((p) => p.id === stage.winner);
  return (
    <section className="kn-fact">
      {winner && <Token player={winner} size="lg" />}
      <h1 className="kn-fact-title">{winner ? `${winner.name} erreicht den Gipfel!` : "Gipfel erreicht!"}</h1>
      <article className="qcard">
        <p className="kn-fact-kicker">Wusstest du schon?</p>
        <p className="qtext">{stage.text}</p>
      </article>
    </section>
  );
}
