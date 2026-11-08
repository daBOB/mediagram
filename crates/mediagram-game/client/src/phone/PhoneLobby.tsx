import type { CSSProperties } from "react";
import type { Connection } from "../net";
import type { LobbySettings } from "../protocol/LobbySettings";
import type { PhoneScreen } from "../protocol/PhoneScreen";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import { inApp } from "./app-bridge";
import { AvatarPicker } from "./AvatarPicker";
import { GAME_NAMES, QUIZ_MODES } from "../ui/lobby";
import { Token } from "../ui/Token";

type Lobby = Extract<PhoneScreen, { kind: "lobby" }>;

export function PhoneLobby({ me, screen, conn }: { me: PlayerInfo; screen: Lobby; conn: Connection }) {
  const { settings } = screen;
  const style = { "--player": me.color } as CSSProperties;
  // A plain-HTTP page cannot keep the screen on (no Wake Lock outside HTTPS, and Chrome ignored a
  // hidden looping video), so the player turns the phone's screen timeout off themselves. The
  // controller apps keep the screen on, so they skip the hint.
  const picker = (
    <>
      <AvatarPicker avatars={screen.avatars} mine={me.avatar} conn={conn} />
      {!inApp() && (
        <p className="hint">Tipp: Schalte für den Spieleabend die automatische Bildschirmsperre deines Handys aus, sonst geht es mitten im Spiel aus.</p>
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
      action: { t: "configure", game: next.game, quiz_mode: next.quiz_mode, kids_only: next.kids_only },
    });
  };
  const quiz = settings.game === "quiz";

  return (
    <section className="screen lobby lobby-leader" style={style}>
      <header className="lobby-head">
        <p className="kicker">Du startest</p>
        <h1 className="lobby-title">Was spielen wir?</h1>
      </header>

      <div className={`game${quiz ? " is-selected" : ""}`}>
        <button className="game-pick" aria-pressed={quiz} onClick={() => configure({ game: "quiz" })}>
          Buzzer-Quiz
        </button>
        {quiz && (
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
      {(["poster", "kopfnuss", "race", "bauherr", "grand_allee", "mau_mau", "schwimmen", "durak"] as const).map((game) => (
        <div key={game} className={`game${settings.game === game ? " is-selected" : ""}`}>
          <button className="game-pick" aria-pressed={settings.game === game} onClick={() => configure({ game })}>
            {GAME_NAMES[game]}
          </button>
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
