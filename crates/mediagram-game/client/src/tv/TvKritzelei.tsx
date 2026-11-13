import { useServerNow, type Connection } from "../net";
import type { InkLog } from "../ink/ink-log";
import type { KritzeleiScene } from "../protocol/KritzeleiScene";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import { Token } from "../ui/Token";
import { TvFinal } from "./TvFinal";
import { InkCanvas } from "./kritzelei/InkCanvas";
import "./kritzelei/kritzelei.css";

type Props = { scene: KritzeleiScene; players: PlayerInfo[]; conn: Connection; ink: InkLog };

/** Kritzelei on the TV: the paper with the live line in the middle, the drawer and the clock left, the scores right. */
export function TvKritzelei({ scene, players, conn, ink }: Props) {
  const now = useServerNow(conn);
  const { stage } = scene;
  if (stage.stage === "final") return <TvFinal players={players} conn={conn} />;

  const drawer = players.find((p) => p.id === scene.drawer);
  const name = drawer?.name ?? "?";
  const clock = stage.stage === "drawing" || stage.stage === "picking" ? stage.clock : null;
  const left = clock ? (clock.ends_at === null ? clock.left_ms : Math.max(0, clock.ends_at - now)) : 0;
  const share = clock && clock.total_ms > 0 ? Math.min(1, left / clock.total_ms) : 0;

  const hint =
    stage.stage === "handover" ? "zeichnet gleich" : stage.stage === "drawing" ? "Ruft laut, was ihr seht!" : stage.stage === "picking" ? "Wer hat's erraten?" : "";
  const guesser = stage.stage === "reveal" ? players.find((p) => p.id === stage.guesser) : undefined;

  return (
    <section className="tv-kz">
      <aside className="kz-left">
        {drawer && <Token player={drawer} size="lg" />}
        <p className="kz-name">{stage.stage === "handover" ? `${name} ist dran` : name}</p>
        {clock && (
          <span className="kz-clock" style={{ background: `conic-gradient(var(--yellow) 0 ${share * 100}%, rgba(255,255,255,.12) 0)` }}>
            <b>{Math.ceil(left / 1000)}</b>
          </span>
        )}
        <p className="kz-hint">{hint}</p>
      </aside>
      <div className="kz-stage">
        {stage.stage === "reveal" && <h1 className="kz-word">{stage.word}</h1>}
        <InkCanvas ink={ink} />
        {stage.stage === "reveal" && (
          <p className="kz-result">{guesser ? `${guesser.name} +2 · ${name} +1` : "Niemand hat's erraten"}</p>
        )}
      </div>
      <ul className="kz-scores">
        {players.map((p) => (
          <li key={p.id} className={p.id === scene.drawer ? "is-drawer" : ""}>
            <Token player={p} size="sm" />
            <span>{p.name}</span>
            <b>{p.score}</b>
          </li>
        ))}
      </ul>
      {!scene.board && <p className="kz-banner">Das Zeichenbrett ist weg – die Uhr steht.</p>}
    </section>
  );
}
