import { useState } from "react";
import { useConnection, useServerNow, type Connection } from "../net";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import type { QuizScene } from "../protocol/QuizScene";
import type { TvView } from "../protocol/TvView";
import { ANSWER_COLORS, ANSWER_MARKS } from "../ui/answers";
import { Token } from "../ui/Token";
import { TvBauherr } from "./TvBauherr";
import { TvFinal } from "./TvFinal";
import { TvLobby } from "./TvLobby";
import { TvPoster } from "./TvPoster";
import { TvCards } from "./TvCards";
import { TvRace } from "./TvRace";

/**
 * The TV screen: the box shows it full-screen in a WebView of its loopback
 * `/tv` page, drawing whatever `TvView` the host sends.
 */
export function TvApp() {
  const [view, setView] = useState<TvView | null>(null);
  const { conn, status } = useConnection(
    () => ({ t: "hello", role: "tv", name: null, token: null }),
    (msg) => {
      if (msg.t === "tv") setView(msg.view);
    },
  );

  return (
    <main className="tv">
      {!view ? (
        <p className="tv-wait">Verbinde mit dem Spieltisch …</p>
      ) : view.scene.kind === "lobby" ? (
        <TvLobby view={view} scene={view.scene} conn={conn} />
      ) : view.scene.kind === "poster" ? (
        <TvPoster scene={view.scene.scene} players={view.players} conn={conn} />
      ) : view.scene.kind === "race" ? (
        // a new table of players is a new game: build the board afresh
        <TvRace key={view.scene.scene.seats.map((s) => s.player).join()} scene={view.scene.scene} players={view.players} conn={conn} />
      ) : view.scene.kind === "cards" ? (
        <TvCards key={view.scene.scene.game + view.scene.scene.seats.map((s) => s.player).join()} scene={view.scene.scene} players={view.players} conn={conn} />
      ) : view.scene.kind === "bauherr" ? (
        <TvBauherr key={view.scene.scene.seats.map((s) => s.player).join()} scene={view.scene.scene} players={view.players} conn={conn} />
      ) : (
        <Quiz scene={view.scene.scene} players={view.players} conn={conn} />
      )}
      {status !== "open" && view && <p className="banner">Verbindung unterbrochen, verbinde neu …</p>}
    </main>
  );
}

function Quiz({ scene, players, conn }: { scene: QuizScene; players: PlayerInfo[]; conn: Connection }) {
  const now = useServerNow(conn);
  const { stage } = scene;

  if (stage.stage === "final") return <TvFinal players={players} conn={conn} />;

  const q = stage.question;
  const reveal = stage.stage === "reveal" ? stage : null;
  const answered = stage.stage === "open" ? new Set(stage.answered) : new Set<number>();
  const byId = new Map(players.map((p) => [p.id, p]));

  let meter = 1;
  let clock = "";
  if (stage.stage === "intro") {
    clock = `Gleich geht's los … ${Math.ceil((stage.opens_at - now) / 1000)}`;
  } else if (stage.stage === "open") {
    const left = Math.max(0, stage.closes_at - now);
    meter = left / (stage.closes_at - stage.opens_at);
    clock = `${Math.ceil(left / 1000)}`;
  } else {
    meter = 0;
  }

  return (
    <section className={`tv-quiz stage-${stage.stage}`}>
      <header className="tv-quiz-head">
        <span className="tv-cat">{q.category}</span>
        <span className="tv-round">
          Frage {scene.round} von {scene.rounds}
        </span>
      </header>

      <article className={q.image ? "qcard has-image" : "qcard"}>
        {q.image && <img className="qimg" src={q.image} alt="Bild zur Frage" />}
        <p className="qtext">{q.text}</p>
        <div className="qcard-foot">
          <div className="meter meter-card" aria-hidden="true">
            <div className="meter-fill" style={{ transform: `scaleX(${meter})` }} />
          </div>
          <span className="qclock">{clock}</span>
        </div>
      </article>

      <ol className="tv-answers">
        {q.options.map((label, i) => {
          const color = ANSWER_COLORS[i];
          const state = reveal ? (i === reveal.correct ? " is-correct" : " is-wrong") : "";
          const pickers = reveal ? reveal.results.filter((r) => r.choice === i) : [];
          return (
            <li key={i} className={`tv-answer key-${color}${state}`}>
              <span className="key-mark" aria-hidden="true">
                {ANSWER_MARKS[color]}
              </span>
              <span className="tv-answer-label">{label}</span>
              {pickers.length > 0 && (
                <span className="tv-pickers">
                  {pickers.map((r) => {
                    const p = byId.get(r.player);
                    return p ? <Token key={r.player} player={p} size="sm" /> : null;
                  })}
                </span>
              )}
            </li>
          );
        })}
      </ol>

      <footer className="tv-table">
        {players.map((p) => {
          const result = reveal?.results.find((r) => r.player === p.id);
          return (
            <div key={p.id} className={`tv-seat${answered.has(p.id) ? " is-locked" : ""}`}>
              <Token player={p} />
              <span className="tv-seat-name">{p.name}</span>
              <span className="tv-seat-score">
                {result && result.points > 0 ? `+${result.points}` : p.score}
              </span>
            </div>
          );
        })}
      </footer>
    </section>
  );
}
