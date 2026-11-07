import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { useConnection, useServerNow, type Connection } from "../net";
import type { PhoneScreen } from "../protocol/PhoneScreen";
import type { PhoneView } from "../protocol/PhoneView";
import type { ServerMsg } from "../protocol/ServerMsg";
import { ANSWER_MARKS } from "../ui/answers";
import { PhoneLobby } from "./PhoneLobby";
import { PhoneSheet } from "./PhoneSheet";
import { rememberAvatar, rememberedAvatar } from "./avatar-memory";
import { goFullscreen } from "./phone-fullscreen";

const NAME_KEY = "spieltisch.name";
const TOKEN_KEY = "spieltisch.token";

const store = {
  get: (k: string) => {
    try {
      return localStorage.getItem(k);
    } catch {
      return null;
    }
  },
  set: (k: string, v: string | null) => {
    try {
      if (v === null) localStorage.removeItem(k);
      else localStorage.setItem(k, v);
    } catch {
      /* private mode: seat is just not remembered */
    }
  },
};

export function PhoneApp() {
  const [name, setName] = useState(() => store.get(NAME_KEY) ?? "");
  // A stored token means we had a seat: rejoin without asking again.
  const [joined, setJoined] = useState(() => !!store.get(NAME_KEY) && !!store.get(TOKEN_KEY));
  const [view, setView] = useState<PhoneView | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const joinedRef = useRef(joined);
  joinedRef.current = joined;
  // a phone rejoining its seat by itself goes fullscreen too: without a tap this only arms it, the first tap enters
  useEffect(() => {
    if (joined) goFullscreen();
  }, [joined]);

  const onMessage = (msg: ServerMsg) => {
    switch (msg.t) {
      case "welcome":
        if (msg.token) store.set(TOKEN_KEY, msg.token);
        break;
      case "phone":
        // the first avatar a phone gets is its choice until its player picks one themselves
        if (msg.view.me.avatar) rememberAvatar(msg.view.me.avatar, true);
        setView(msg.view);
        break;
      case "error":
        if (msg.code === "name_required" || msg.code === "room_full") {
          store.set(TOKEN_KEY, null);
          setJoined(false);
          setView(null);
        }
        setToast(msg.message);
        break;
    }
  };

  const { conn, status } = useConnection(
    () =>
      joinedRef.current
        ? {
            t: "hello",
            role: "phone",
            name: store.get(NAME_KEY),
            token: store.get(TOKEN_KEY),
            avatar: rememberedAvatar(),
          }
        : null,
    onMessage,
  );

  useEffect(() => {
    if (!toast) return;
    const t = window.setTimeout(() => setToast(null), 3500);
    return () => clearTimeout(t);
  }, [toast]);

  const join = (e: FormEvent) => {
    e.preventDefault();
    const clean = name.trim();
    if (!clean) {
      setToast("Bitte gib einen Namen ein.");
      return;
    }
    goFullscreen();
    store.set(NAME_KEY, clean);
    joinedRef.current = true;
    setJoined(true);
    conn.rehello();
  };

  return (
    <main className="phone">
      {!joined ? (
        <JoinScreen name={name} setName={setName} onJoin={join} disabled={status !== "open"} />
      ) : view ? (
        <Screen view={view} conn={conn} />
      ) : (
        <Centered title="Einen Moment" text="Du wirst an den Tisch gesetzt." />
      )}
      {status !== "open" && joined && <p className="banner">Verbindung zum Fernseher wird wiederhergestellt …</p>}
      {toast && (
        <p className="toast" role="alert">
          {toast}
        </p>
      )}
    </main>
  );
}

function JoinScreen(props: {
  name: string;
  setName: (n: string) => void;
  onJoin: (e: FormEvent) => void;
  disabled: boolean;
}) {
  return (
    <form className="join" onSubmit={props.onJoin}>
      <h1 className="wordmark">Spieltisch</h1>
      <label className="field">
        <span>Wie heißt du?</span>
        <input
          value={props.name}
          onChange={(e) => props.setName(e.target.value)}
          maxLength={16}
          autoComplete="nickname"
          autoCapitalize="words"
          enterKeyHint="go"
          placeholder="Dein Name"
        />
      </label>
      <button className="cta" type="submit" disabled={props.disabled}>
        {props.disabled ? "Suche den Fernseher …" : "Mitspielen"}
      </button>
    </form>
  );
}

function Screen({ view, conn }: { view: PhoneView; conn: Connection }) {
  const { me, screen } = view;
  const act = (action: Parameters<Connection["send"]>[0]) => conn.send(action);

  switch (screen.kind) {
    case "lobby":
      return <PhoneLobby me={me} screen={screen} conn={conn} />;
    case "wait":
      return <Centered title={screen.title} text={screen.text} />;
    case "choice":
      return <ChoiceScreen key={screen.opens_at} screen={screen} conn={conn} />;
    case "sheet":
      return <PhoneSheet screen={screen} conn={conn} />;
    case "outcome":
      return (
        <section className={`screen outcome tone-${screen.tone}`}>
          <h1 className="screen-title">{screen.title}</h1>
          {screen.points > 0 && <p className="points">+{screen.points}</p>}
          {screen.text && <p className="screen-text">{screen.text}</p>}
          <p className="hint">Stand: {me.score} Punkte</p>
        </section>
      );
    case "final":
      return (
        <section className="screen final">
          <p className="rank" aria-label={`Platz ${screen.rank} von ${screen.of}`}>
            {screen.rank}.
          </p>
          <p className="screen-text">
            Platz {screen.rank} von {screen.of} mit {screen.score} Punkten
          </p>
          {screen.can_restart ? (
            <button className="cta" onClick={() => act({ t: "action", action: { t: "back_to_lobby" } })}>
              Zurück zur Lobby
            </button>
          ) : (
            <p className="hint">Die Spielleitung entscheidet, wie es weitergeht.</p>
          )}
        </section>
      );
  }
}

type Choice = Extract<PhoneScreen, { kind: "choice" }>;

function ChoiceScreen({ screen, conn }: { screen: Choice; conn: Connection }) {
  const now = useServerNow(conn);
  const [localPick, setLocalPick] = useState<number | null>(null);
  const picked = screen.picked ?? localPick;
  const open = now >= screen.opens_at;
  // Without a closing time the choice waits for the player: no clock, no meter.
  const timed = screen.closes_at !== null;
  const closes = screen.closes_at ?? Infinity;
  const closed = now >= closes;
  const live = open && !closed && picked === null;

  const pick = (index: number) => {
    if (!live) return;
    const at = conn.serverNow();
    // An untimed choice never locks on the tap alone: a tap lost in a reconnect
    // would leave the button dead with no clock to end the wait. The host
    // ignores a repeat, and its next screen replaces this one.
    if (timed) setLocalPick(index);
    navigator.vibrate?.(25);
    conn.send({ t: "action", action: { t: "choose", index, at } });
  };

  const onKey = (e: KeyboardEvent, index: number) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      pick(index);
    }
  };

  const left = Math.max(0, closes - now);
  const total = closes - screen.opens_at;

  return (
    <section className="screen choice">
      <header className="choice-head">
        <span>{screen.prompt}</span>
        {timed && (
          <span className="choice-clock" aria-live="polite">
            {!open ? `Gleich … ${Math.ceil((screen.opens_at - now) / 1000)}` : picked !== null ? "Eingeloggt" : `${Math.ceil(left / 1000)} s`}
          </span>
        )}
      </header>
      {timed && (
        <div className="meter" aria-hidden="true">
          <div className="meter-fill" style={{ transform: `scaleX(${open ? left / total : 1})` }} />
        </div>
      )}
      <div className={`keys${open ? " keys-open" : ""}`}>
        {screen.options.map((o, i) => (
          <button
            key={i}
            className={`key key-${o.color}${picked === i ? " key-picked" : ""}${picked !== null && picked !== i ? " key-dim" : ""}`}
            aria-disabled={!live}
            onPointerDown={() => pick(i)}
            onKeyDown={(e) => onKey(e, i)}
          >
            <span className="key-mark" aria-hidden="true">
              {ANSWER_MARKS[o.color]}
            </span>
            <span className="key-label">{o.label}</span>
          </button>
        ))}
      </div>
    </section>
  );
}

function Centered({ title, text }: { title: string; text?: string | null }) {
  return (
    <section className="screen">
      <h1 className="screen-title">{title}</h1>
      {text && <p className="screen-text">{text}</p>}
    </section>
  );
}
