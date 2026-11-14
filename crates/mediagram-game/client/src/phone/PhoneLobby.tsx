import type { CSSProperties } from "react";
import type { Connection } from "../net";
import type { LobbySettings } from "../protocol/LobbySettings";
import type { PhoneScreen } from "../protocol/PhoneScreen";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import { boardCapable, inApp } from "./app-bridge";
import { AvatarPicker } from "./AvatarPicker";
import { QUIZ_MODES, SPEEDS } from "../ui/lobby";
import { Token } from "../ui/Token";

type Lobby = Extract<PhoneScreen, { kind: "lobby" }>;

type Props = { me: PlayerInfo; screen: Lobby; conn: Connection; onBecomeBoard: () => void };

type ChoiceProps<T> = { label: string; options: { mode: T; label: string }[]; value: T; pick: (mode: T) => void };

/** One row of segment buttons under the chosen game: the quiz's questions, a timed game's tempo. */
function Choice<T extends string>({ label, options, value, pick }: ChoiceProps<T>) {
  return (
    <div className="seg" role="group" aria-label={label}>
      {options.map((m) => (
        <button
          key={m.mode}
          className={`seg-btn${value === m.mode ? " is-selected" : ""}`}
          aria-pressed={value === m.mode}
          onClick={() => pick(m.mode)}
        >
          {m.label}
        </button>
      ))}
    </div>
  );
}

export function PhoneLobby({ me, screen, conn, onBecomeBoard }: Props) {
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
          {screen.leader_name ? `${screen.leader_name} startet das Spiel …` : "Das Spiel wird gewählt …"}
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
      action: {
        t: "configure",
        game: next.game,
        quiz_mode: next.quiz_mode,
        speed: next.speed,
        kids_only: next.kids_only,
        adult: next.adult,
      },
    });
  };
  const picked = screen.games.find((g) => g.kind === settings.game);

  // The game is picked on the TV with the remote; the quiz's questions and a timed game's tempo have no place there, so they stay here.
  return (
    <section className="screen lobby lobby-leader" style={style}>
      <header className="lobby-head">
        <p className="kicker">Du startest</p>
        <h1 className="lobby-title">{picked?.name}</h1>
        <p className="hint">Gewählt wird am Fernseher.</p>
      </header>

      {settings.game === "quiz" && (
        <Choice label="Fragen" options={QUIZ_MODES} value={settings.quiz_mode} pick={(quiz_mode) => configure({ quiz_mode })} />
      )}
      {picked?.timed && (
        <Choice label="Tempo" options={SPEEDS} value={settings.speed} pick={(speed) => configure({ speed })} />
      )}

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

      {/* kids mode leaves the adult questions out altogether; the box switches them off with it */}
      {!settings.kids_only && (
        <div className="toggle-row">
          <div>
            <span id="adult-label">Matratzensport (ab 18)</span>
            <p className="toggle-hint">Fragen zu Sexualität in Quiz, Kopfnuss und Dreifelder</p>
          </div>
          <button
            className="switch"
            role="switch"
            aria-checked={settings.adult}
            aria-labelledby="adult-label"
            onClick={() => configure({ adult: !settings.adult })}
          />
        </div>
      )}

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
