import type { CSSProperties } from "react";
import { useServerNow, type Connection } from "../net";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import type { PreisfrageScene } from "../protocol/PreisfrageScene";
import { Token } from "../ui/Token";
import { euro, linePlace, stackPins } from "../ui/price";
import { TvFinal } from "./TvFinal";
import "./preisfrage/preisfrage.css";

type Props = { scene: PreisfrageScene; players: PlayerInfo[]; conn: Connection };

/** Two pins closer than this (a share of the line, about one price label) stand in rows above each other. */
const PIN_GAP = 0.18;

/**
 * Preisfrage on the TV, the shop window look: the article's photo large on the left, its name and who has
 * guessed on the right. At the reveal the price stands under the name and every guess is a pin on a line
 * from 0 to twice the price, the price itself a flag.
 */
export function TvPreisfrage({ scene, players, conn }: Props) {
  const now = useServerNow(conn);
  const { stage, article } = scene;
  // Running totals are the players' scores, so the ceremony ranks by them and equal totals share a place.
  if (stage.stage === "final") return <TvFinal players={players.filter((p) => stage.standings.some((s) => s.player === p.id))} conn={conn} />;

  const byId = new Map(players.map((p) => [p.id, p]));
  const intro = stage.stage === "guessing" && now < stage.opens_at;
  const left = stage.stage === "guessing" ? Math.max(0, stage.closes_at - Math.max(now, stage.opens_at)) : 0;
  const share = stage.stage === "guessing" ? left / (stage.closes_at - stage.opens_at) : 0;

  return (
    <section className="tv-pf">
      <header className="pf-top">
        <span className="pf-brand">
          Preis<span>frage</span>
        </span>
        <span className="pf-count">
          Artikel {scene.round} von {scene.rounds}
          {stage.stage === "guessing" && !intro && (
            <span className="pf-clock" style={{ background: `conic-gradient(var(--yellow) 0 ${share * 100}%, rgba(255,255,255,.12) 0)` }}>
              <b>{Math.ceil(left / 1000)}</b>
            </span>
          )}
        </span>
      </header>
      <figure className="pf-photo">
        <img src={article.photo} alt="" />
      </figure>
      <div className="pf-side">
        <h1 className="pf-name">{article.name}</h1>
        {intro && stage.stage === "guessing" && (
          <>
            <p className="pf-ask">Was kostet das?</p>
            <p className="pf-countdown">{Math.ceil((stage.opens_at - now) / 1000)}</p>
          </>
        )}
        {!intro && stage.stage === "guessing" && (
          <>
            <p className="pf-ask">Was kostet das? Tippt den Preis aufs Handy.</p>
            <ul className="pf-players">
              {players
                .filter((p) => p.connected)
                .map((p) => (
                  <li key={p.id} className={stage.guessed.includes(p.id) ? "is-done" : "is-waiting"}>
                    <Token player={p} size="lg" />
                    <span>{p.name}</span>
                  </li>
                ))}
            </ul>
          </>
        )}
        {stage.stage === "reveal" && <Reveal cents={stage.cents} checked={stage.checked} rows={stage.rows} byId={byId} />}
      </div>
    </section>
  );
}

type RevealProps = { cents: number; checked: string; rows: Extract<PreisfrageScene["stage"], { stage: "reveal" }>["rows"]; byId: Map<number, PlayerInfo> };

function Reveal({ cents, checked, rows, byId }: RevealProps) {
  const closest = rows.filter((r) => r.closest).map((r) => byId.get(r.player)?.name ?? "?");
  const guessed = rows.filter((r) => r.cents !== null);
  const places = guessed.map((r) => linePlace(r.cents!, cents));
  const stack = stackPins(places, PIN_GAP);
  return (
    <>
      <p className="pf-price">{euro(cents)}</p>
      <p className="pf-checked">
        {checked ? `Preis vom ${checked}` : "Preis bei Amazon"}
        {closest.length > 0 && ` · am nächsten: ${closest.join(" und ")}`}
      </p>
      <div className="pf-line">
        <div className="pf-rail" />
        {[0, 1].map((t) => (
          <span key={t} className="pf-tick" style={{ left: `${t * 100}%` }}>
            {euro(Math.round(2 * cents * t))}
          </span>
        ))}
        <span className="pf-flag" style={{ left: "50%" }}>
          <b>{euro(cents)}</b>
        </span>
        {guessed.map((r, i) => {
          const p = byId.get(r.player);
          return p ? (
            <span key={r.player} className={`pf-pin${r.closest ? " is-closest" : ""}`} style={{ left: `${places[i] * 100}%`, "--row": stack[i] } as CSSProperties}>
              <b>{euro(r.cents!)}</b>
              <Token player={p} size="md" />
              <i />
            </span>
          ) : null;
        })}
      </div>
      <ul className="pf-points">
        {rows.map((r) => (
          <li key={r.player} className={r.closest ? "is-closest" : ""}>
            {byId.get(r.player)?.name ?? "?"} +{r.points}
            {r.exact && " · auf den Cent!"}
            {r.cents === null && " · kein Tipp"}
          </li>
        ))}
      </ul>
    </>
  );
}
