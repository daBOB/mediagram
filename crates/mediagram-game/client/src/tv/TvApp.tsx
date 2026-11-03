import { useState } from "react";
import { useConnection, useServerNow, type Connection } from "../net";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import type { QuizScene } from "../protocol/QuizScene";
import type { TvView } from "../protocol/TvView";
import { ANSWER_COLORS, ANSWER_MARKS } from "../ui/answers";
import { Token } from "../ui/Token";

/**
 * Browser version of the TV screen. The Godot host will render the same
 * `TvView` natively; this one is for development and for TVs with a browser.
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
        <Lobby view={view} minPlayers={view.scene.min_players} conn={conn} />
      ) : (
        <Quiz scene={view.scene.scene} players={view.players} conn={conn} />
      )}
      {status !== "open" && view && <p className="banner">Verbindung unterbrochen, verbinde neu …</p>}
    </main>
  );
}

function Lobby({ view, minPlayers, conn }: { view: TvView; minPlayers: number; conn: Connection }) {
  const seated = view.players.filter((p) => p.connected);
  const canStart = seated.length >= minPlayers;
  return (
    <section className="tv-lobby">
      <div className="tv-lobby-intro">
        <h1 className="wordmark wordmark-tv">Spieltisch</h1>
        <p className="tv-lead">Handy-Kamera auf den Code richten und mitspielen.</p>
        <p className="tv-url">{view.join_url.replace(/^https?:\/\//, "").replace(/\/$/, "")}</p>
      </div>
      <figure className="qr-card">
        <img src="/qr.svg" alt={`QR-Code für ${view.join_url}`} />
      </figure>
      <div className="tv-seats">
        {view.players.length === 0 ? (
          <p className="tv-empty">Noch niemand am Tisch.</p>
        ) : (
          <ul className="seat-list">
            {view.players.map((p) => (
              <li key={p.id} className="seat">
                <Token player={p} />
                <span className="seat-name">{p.name}</span>
              </li>
            ))}
          </ul>
        )}
        <button
          className="cta cta-tv"
          autoFocus
          disabled={!canStart}
          onClick={() => conn.send({ t: "action", action: { t: "start_game" } })}
        >
          {canStart ? "Quiz starten" : `Wartet auf ${minPlayers - seated.length} Spieler`}
        </button>
      </div>
    </section>
  );
}

function Quiz({ scene, players, conn }: { scene: QuizScene; players: PlayerInfo[]; conn: Connection }) {
  const now = useServerNow(conn);
  const { stage } = scene;

  if (stage.stage === "final") return <Final players={players} conn={conn} />;

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

      <article className="qcard">
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

function Final({ players, conn }: { players: PlayerInfo[]; conn: Connection }) {
  const ranked = [...players].sort((a, b) => b.score - a.score);
  const rankOf = (p: PlayerInfo) => ranked.filter((o) => o.score > p.score).length + 1;
  return (
    <section className="tv-final">
      <h1 className="tv-final-title">
        {ranked.length > 0 ? `${ranked[0].name} gewinnt` : "Spiel beendet"}
      </h1>
      <ol className="podium">
        {ranked.map((p) => (
          <li key={p.id} className={`podium-row rank-${Math.min(rankOf(p), 4)}`}>
            <span className="podium-rank">{rankOf(p)}.</span>
            <Token player={p} />
            <span className="podium-name">{p.name}</span>
            <span className="podium-score">{p.score}</span>
          </li>
        ))}
      </ol>
      <button className="cta cta-tv" autoFocus onClick={() => conn.send({ t: "action", action: { t: "back_to_lobby" } })}>
        Zurück zur Lobby
      </button>
    </section>
  );
}
