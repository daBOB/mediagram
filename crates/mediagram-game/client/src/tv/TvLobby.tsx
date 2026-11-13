import { useEffect, useMemo, useState } from "react";
import type { Connection } from "../net";
import type { TvScene } from "../protocol/TvScene";
import type { TvView } from "../protocol/TvView";
import { gameLine, seatsLine } from "../ui/lobby";
import { okStarts, shelfLine, stepIndex } from "../ui/shelf";
import { BADGE_TEXT, GROUPS, shelfItems, upcomingLine, type ShelfGroup, type ShelfItem } from "../ui/shelf-games";
import { ShelfBackdrop } from "./lobby/ShelfBackdrop";
import { ShelfRow } from "./lobby/ShelfRow";
import { ShelfTabs } from "./lobby/ShelfTabs";
import { TablePanel } from "./lobby/TablePanel";
import { useShelfPick } from "./lobby/use-shelf-pick";
import { TvSettingsPanel } from "./settings/TvSettingsPanel";
import "./lobby/tv-shelf.css";

type Lobby = Extract<TvScene, { kind: "lobby" }>;

// The box's app adds `?films=0` when the media app did not answer this night.
const filmsAway = new URLSearchParams(location.search).get("films") === "0";

/**
 * The night's home screen: the games as a poster row over the focused one's backdrop, and the
 * table at the bottom right, with the code to join, who sits there and the TV's settings. The row
 * shows one half of the shelf, the minigames or the full games, each ending with the games not
 * playable yet; focusing one of those shows what it will be, and the room's pick stays where it was.
 * Left and Right pick a game, OK starts it; Up reaches the switch between the halves, Up again the
 * settings. After a game the room comes back here.
 */
export function TvLobby({ view, scene, conn }: { view: TvView; scene: Lobby; conn: Connection }) {
  const { games, settings } = scene;
  const items = useMemo(() => shelfItems(games), [games]);
  const { focus, setFocus, heard, start } = useShelfPick(settings, conn);
  // The key of a not-yet-playable game while one is focused. Any change of the room's pick, ours or
  // a phone's, brings the row back to it.
  const [browsing, setBrowsing] = useState<string | null>(null);
  useEffect(() => setBrowsing(null), [focus]);
  const item = items.find((i) => i.key === (browsing ?? focus)) ?? items[0];
  const game = item.game;
  // the row shows the focused game's half, so a pick from a phone brings its half along
  const row = items.filter((i) => i.group === item.group);
  const index = row.indexOf(item);
  const facts = {
    seated: view.players.filter((p) => p.connected).length,
    minPlayers: scene.min_players,
    name: item.name,
    heard,
    blocked: scene.blocked,
    leader: scene.leader_name,
  };
  const line = item.badge ? upcomingLine(item.badge) : shelfLine(facts);
  const startsNow = () => !!game && okStarts(facts);
  const pick = (next: ShelfItem) => {
    setBrowsing(next.game ? null : next.key);
    if (next.game) setFocus(next.game.kind);
  };
  // the other half opens on its first game, which is playable unless none of it is yet
  const switchTo = (group: ShelfGroup) => group !== item.group && pick(items.find((i) => i.group === group)!);
  const [zone, setZone] = useState<"row" | "tabs" | "settings">("row");
  const [settingsOpen, setSettingsOpen] = useState(false);

  // Registered afresh each render, so the keys act on what is on screen now.
  useEffect(() => {
    if (settingsOpen) return;
    const onKey = (e: KeyboardEvent) => {
      const dx = e.key === "ArrowLeft" ? -1 : e.key === "ArrowRight" ? 1 : 0;
      if (dx) {
        if (zone === "row") pick(row[stepIndex(index, dx, row.length)]);
        else if (zone === "tabs") switchTo(GROUPS[stepIndex(GROUPS.indexOf(item.group), dx, GROUPS.length)]);
      } else if (e.key === "ArrowUp") setZone(zone === "row" ? "tabs" : "settings");
      else if (e.key === "ArrowDown") setZone(zone === "settings" ? "tabs" : "row");
      else if (e.key === "Enter") {
        // a held OK repeats: one start, one panel
        if (e.repeat) return e.preventDefault();
        if (zone === "settings") setSettingsOpen(true);
        else if (zone === "tabs") setZone("row");
        else if (startsNow()) start();
      } else return;
      e.preventDefault();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  const closeSettings = () => {
    setSettingsOpen(false);
    setZone("row");
  };
  const pickOrStart = (i: number) => {
    if (i !== index) pick(row[i]);
    else if (startsNow()) start();
  };

  return (
    <section className="tv-shelf">
      <ShelfBackdrop art={item.key} />
      <header className="shelf-head">
        <p className="kicker kicker-tv">Spieleabend</p>
        <h1 className="shelf-title">{item.name}</h1>
        <p className="shelf-sub">
          {game ? gameLine(game, settings.quiz_mode) : seatsLine(item.min, item.max)}
          {item.badge && <span className={`badge badge-${item.badge}`}>{BADGE_TEXT[item.badge]}</span>}
          {settings.kids_only && <span className="badge badge-on">Nur für Kinder</span>}
          {settings.adult && <span className="badge badge-on">Matratzensport · ab 18</span>}
        </p>
        <p className="shelf-blurb">{item.blurb}</p>
        {line && <p className="shelf-line">{line}</p>}
      </header>
      <TablePanel
        players={view.players}
        joinUrl={view.join_url}
        settingsFocused={zone === "settings" && !settingsOpen}
        onSettings={() => setSettingsOpen(true)}
        filmsAway={filmsAway}
      />
      <ShelfTabs group={item.group} focused={zone === "tabs" && !settingsOpen} onPick={switchTo} />
      <ShelfRow key={item.group} items={row} index={index} focused={zone === "row"} onPoster={pickOrStart} />
      {settingsOpen && <TvSettingsPanel onClose={closeSettings} />}
    </section>
  );
}
