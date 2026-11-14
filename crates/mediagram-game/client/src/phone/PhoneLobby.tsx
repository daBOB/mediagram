import type { CSSProperties } from "react";
import type { Connection } from "../net";
import type { LobbySettings } from "../protocol/LobbySettings";
import type { PhoneScreen } from "../protocol/PhoneScreen";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import { boardCapable, inApp } from "./app-bridge";
import { AvatarPicker } from "./AvatarPicker";
import { QUIZ_MODES } from "../ui/lobby";
import { Token } from "../ui/Token";

type Lobby = Extract<PhoneScreen, { kind: "lobby" }>;

type Props = { me: PlayerInfo; screen: Lobby; conn: Connection; onBecomeBoard: () => void };

export function PhoneLobby({ me, screen, conn, onBecomeBoard }: Props) {
  const { settings } = screen;
  const style = { "--player": me.color } as CSSProperties;
  // A plain-HTTP page cannot keep the screen on (no Wake Lock outside HTTPS, and Chrome ignored a
  // hidden looping video), so the player turns the phone's screen timeout off themselves. The
  // controller apps keep the screen on, so they skip the hint.
  // spike: the iPad's ink view calls this with {points, t}; t is the touch's epoch ms
  (window as any).__spikeInk = (json: string) => {
    const { points, t } = JSON.parse(json);
    conn.send({ t: "action", action: { t: "spike_ink", points, at: conn.serverNow() - (Date.now() - t) } } as never);
  };
  const picker = (
    <>
      {inApp() && <button type="button" onClick={() => (window as any).mediagramApp.spike()}>Latenztest</button>}
      <AvatarPicker avatars={screen.avatars} mine={me.avatar} conn={conn} />
      {!inApp() && (
        <p className="hint">Tipp: Schalte für den Spieleabend die automatische Bildschirmsperre deines Handys aus, sonst geht es mitten im Spiel aus.</p>
      )}
      {boardCapable() && settings.game === "kritzelei" && (
        <button className="cta" onClick={onBecomeBoard}>
          Als Zeichenbrett verwenden
        </button>
      )}
    </>
  );

  if (!screen.is_leader) {
    return (
      <section className="screen lobby" style={style}>
        <Token player={me} size="lg" />
        <h1 className="screen-title">{me.name}</h1>
        <p className="screen-text">
          {screen.leader_name ? `${screen.leader_name} wählt das Spiel …` : "Das Spiel wird gewählt …"}
        </p>
        <p className="hint">
          {screen.players === 1 ? "Du bist allein am Tisch." : `Ihr seid zu ${screen.players}.`}
        </p>
        {picker}
      </section>
    );
  }

  // Every tap sends the whole choice, so the host never merges partial updates.
  const configure = (patch: Partial<LobbySettings>) => {
    const next = { ...settings, ...patch };
    conn.send({
      t: "action",
      action: { t: "configure", game: next.game, quiz_mode: next.quiz_mode, kids_only: next.kids_only, adult: next.adult },
    });
  };
  const quiz = settings.game === "quiz";

  return (
    <section className="screen lobby lobby-leader" style={style}>
      <header className="lobby-head">
        <p className="kicker">Du startest</p>
        <h1 className="lobby-title">Was spielen wir?</h1>
      </header>

      {screen.games.map(({ kind, name }) => (
        <div key={kind} className={`game${settings.game === kind ? " is-selected" : ""}`}>
          <button className="game-pick" aria-pressed={settings.game === kind} onClick={() => configure({ game: kind })}>
            {name}
          </button>
          {quiz && kind === "quiz" && (
            <div className="seg" role="group" aria-label="Fragen">
              {QUIZ_MODES.map((m) => (
                <button
                  key={m.mode}
                  className={`seg-btn${settings.quiz_mode === m.mode ? " is-selected" : ""}`}
                  aria-pressed={settings.quiz_mode === m.mode}
                  onClick={() => configure({ quiz_mode: m.mode })}
                >
                  {m.label}
                </button>
              ))}
            </div>
          )}
        </div>
      ))}

      <div className="toggle-row">
        <div>
          <span id="kids-label">Nur für Kinder</span>
          {settings.kids_locked && <p className="toggle-hint">Auf diesem Profil immer an</p>}
        </div>
        <button
          className="switch"
          role="switch"
          aria-checked={settings.kids_only}
          aria-labelledby="kids-label"
          disabled={settings.kids_locked}
          onClick={() => configure({ kids_only: !settings.kids_only })}
        />
      </div>

      <div className="toggle-row">
        <div>
          <span id="adult-label">Matratzensport (ab 18)</span>
          <p className="toggle-hint">
            {settings.kids_only ? "Nicht im Kindermodus" : "Fragen zu Sexualität in Quiz, Kopfnuss und Dreifelder"}
          </p>
        </div>
        <button
          className="switch"
          role="switch"
          aria-checked={settings.adult}
          aria-labelledby="adult-label"
          disabled={settings.kids_only}
          onClick={() => configure({ adult: !settings.adult })}
        />
      </div>

      {picker}

      {screen.blocked && <p className="lobby-blocked">{screen.blocked}</p>}
      <button
        className="cta"
        disabled={!screen.can_start}
        onClick={() => conn.send({ t: "action", action: { t: "start_game" } })}
      >
        Los geht's
      </button>
    </section>
  );
}
