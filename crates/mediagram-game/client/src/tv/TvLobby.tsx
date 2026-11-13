import { useEffect, useLayoutEffect, useRef, useState } from "react";
import type { Connection } from "../net";
import type { TvScene } from "../protocol/TvScene";
import type { TvView } from "../protocol/TvView";
import { gameLine } from "../ui/lobby";
import { okStarts, scrollToShow, shelfLine, stepIndex } from "../ui/shelf";
import { ShelfBackdrop } from "./lobby/ShelfBackdrop";
import { ShelfPoster } from "./lobby/ShelfPoster";
import { TablePanel } from "./lobby/TablePanel";
import { useShelfPick } from "./lobby/use-shelf-pick";
import { TvSettingsPanel } from "./settings/TvSettingsPanel";
import "./lobby/tv-shelf.css";

type Lobby = Extract<TvScene, { kind: "lobby" }>;

// The box's app adds `?films=0` when the media app did not answer this night.
const filmsAway = new URLSearchParams(location.search).get("films") === "0";

/**
 * The night's home screen: the games as a poster row over the focused one's backdrop, and the
 * table beside them, with the code to join, who sits there and the TV's settings. Left and Right
 * pick a game, OK starts it, Up reaches the settings. After a game the room comes back here.
 */
export function TvLobby({ view, scene, conn }: { view: TvView; scene: Lobby; conn: Connection }) {
  const { games, settings } = scene;
  const { focus, setFocus, heard, start } = useShelfPick(settings, conn);
  const index = Math.max(0, games.findIndex((g) => g.kind === focus));
  const game = games[index];
  const facts = {
    seated: view.players.filter((p) => p.connected).length,
    minPlayers: scene.min_players,
    name: game.name,
    heard,
    blocked: scene.blocked,
    leader: scene.leader_name,
  };
  const line = shelfLine(facts);
  const [zone, setZone] = useState<"row" | "settings">("row");
  const [settingsOpen, setSettingsOpen] = useState(false);

  // Registered afresh each render, so the keys act on what is on screen now.
  useEffect(() => {
    if (settingsOpen) return;
    const onKey = (e: KeyboardEvent) => {
      const dx = e.key === "ArrowLeft" ? -1 : e.key === "ArrowRight" ? 1 : 0;
      if (dx) {
        if (zone === "row") setFocus(games[stepIndex(index, dx, games.length)].kind);
      } else if (e.key === "ArrowUp") setZone("settings");
      else if (e.key === "ArrowDown") setZone("row");
      else if (e.key === "Enter") {
        // a held OK repeats: one start, one panel
        if (e.repeat) return e.preventDefault();
        if (zone === "settings") setSettingsOpen(true);
        else if (okStarts(facts)) start();
      } else return;
      e.preventDefault();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  // More games than the row shows: it scrolls to keep the focused poster, grown a little, in view.
  // Back from a game, the row opens on the game just played rather than gliding there.
  const row = useRef<HTMLUListElement>(null);
  const arrived = useRef(false);
  useLayoutEffect(() => {
    const el = row.current;
    const poster = el?.children[index] as HTMLElement | undefined;
    if (!el || !poster) return;
    const grow = poster.offsetWidth * 0.06;
    const left = scrollToShow(el.scrollLeft, el.clientWidth, poster.offsetLeft - grow, poster.offsetLeft + poster.offsetWidth + grow);
    if (left !== el.scrollLeft) el.scrollTo({ left, behavior: arrived.current ? "smooth" : "auto" });
    arrived.current = true;
  }, [index]);

  const closeSettings = () => {
    setSettingsOpen(false);
    setZone("row");
  };
  const pickOrStart = (i: number) => {
    if (i !== index) setFocus(games[i].kind);
    else if (okStarts(facts)) start();
  };

  return (
    <section className="tv-shelf">
      <ShelfBackdrop kind={game.kind} />
      <header className="shelf-head">
        <p className="kicker kicker-tv">Spieleabend</p>
        <h1 className="shelf-title">{game.name}</h1>
        <p className="shelf-sub">
          {gameLine(game, settings.quiz_mode)}
          {settings.kids_only && <span className="badge badge-on">Nur für Kinder</span>}
          {settings.adult && <span className="badge badge-on">Matratzensport · ab 18</span>}
        </p>
        {line && <p className="shelf-line">{line}</p>}
      </header>
      <TablePanel
        players={view.players}
        joinUrl={view.join_url}
        settingsFocused={zone === "settings" && !settingsOpen}
        onSettings={() => setSettingsOpen(true)}
        filmsAway={filmsAway}
        boardChip={game.kind === "kritzelei" ? scene.board : undefined}
      />
      <ul className="shelf-row" ref={row}>
        {games.map((g, i) => (
          <ShelfPoster key={g.kind} game={g} picked={i === index} focused={i === index && zone === "row"} onClick={() => pickOrStart(i)} />
        ))}
      </ul>
      {settingsOpen && <TvSettingsPanel onClose={closeSettings} />}
    </section>
  );
}
