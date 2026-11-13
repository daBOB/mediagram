import { useEffect, useRef, useState, type FormEvent } from "react";
import { useConnection, type Connection } from "../net";
import type { PhoneScreen } from "../protocol/PhoneScreen";
import type { PhoneView } from "../protocol/PhoneView";
import type { ServerMsg } from "../protocol/ServerMsg";
import { CardsDesk } from "./CardsDesk";
import { ChoiceScreen } from "./ChoiceScreen";
import { OutcomeScreen } from "./OutcomeScreen";
import { PhoneFluechtig } from "./PhoneFluechtig";
import { PhoneLobby } from "./PhoneLobby";
import { PhonePriceKeypad } from "./PhonePriceKeypad";
import { PhoneSheet } from "./PhoneSheet";
import { buzz, memory as store } from "./app-bridge";
import { rememberAvatar, rememberedAvatar } from "./avatar-memory";
import { buzzForView } from "./buzz-moments";
import { goFullscreen } from "./phone-fullscreen";

const NAME_KEY = "spieltisch.name";
const TOKEN_KEY = "spieltisch.token";

export function PhoneApp() {
  const [name, setName] = useState(() => store.get(NAME_KEY) ?? "");
  // A stored token means we had a seat: rejoin without asking again.
  const [joined, setJoined] = useState(() => !!store.get(NAME_KEY) && !!store.get(TOKEN_KEY));
  const [view, setView] = useState<PhoneView | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const joinedRef = useRef(joined);
  joinedRef.current = joined;
  const lastScreen = useRef<PhoneScreen | null>(null);
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
        {
          const kind = buzzForView(lastScreen.current, msg.view.screen, msg.view.buzz);
          lastScreen.current = msg.view.screen;
          if (kind) buzz(kind);
        }
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
    case "cards":
      return <CardsDesk desk={screen.desk} conn={conn} />;
    case "sheet":
      return <PhoneSheet screen={screen} conn={conn} />;
    case "fluechtig":
      return <PhoneFluechtig phone={screen.phone} me={me} conn={conn} />;
    case "price":
      // keyed by the article, so the next one starts with an empty keypad
      return <PhonePriceKeypad key={screen.keypad.opens_at} keypad={screen.keypad} conn={conn} />;
    case "outcome":
      return <OutcomeScreen screen={screen} score={me.score} />;
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

function Centered({ title, text }: { title: string; text?: string | null }) {
  return (
    <section className="screen">
      <h1 className="screen-title">{title}</h1>
      {text && <p className="screen-text">{text}</p>}
    </section>
  );
}
