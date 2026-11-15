import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import type { Connection } from "../../net";
import type { PlayerInfo } from "../../protocol/PlayerInfo";
import { TvFinal } from "../TvFinal";
import { TvSettingsPanel } from "../settings/TvSettingsPanel";
import { TableShell, type Stage } from "./table-shell";

/** Starts a game's stage in `container`, its chunk loaded with it: `live()` the latest scene and players, `now()` the server clock. */
export type StageLoader<S> = (container: HTMLElement, live: () => { scene: S; players: PlayerInfo[] }, now: () => number) => Promise<Stage>;

/**
 * A 3D game board on the TV: a full-screen WebGL canvas with the tags and banner drawn inside it (no HTML over the canvas
 * while playing, it cost the box frames), the settings over it on OK, the final ranking once the winner has been cheered.
 * three.js loads with the stage only. `ranking` is set once the game is over; `className` also names the wait line's class.
 */
export function TvTableShell<S>(props: {
  scene: S;
  players: PlayerInfo[];
  conn: Connection;
  ranking: number[] | null;
  load: StageLoader<S>;
  name: string;
  className: string;
  waiting: string;
  failed: string;
}) {
  const { players, conn, ranking, className } = props;
  const live = useRef(props);
  live.current = props;
  const [shell] = useState(() => new TableShell((el) => props.load(el, () => live.current, () => conn.serverNow()), props.name));
  const view = useSyncExternalStore(shell.subscribe, shell.view);
  const stage = useRef<HTMLElement>(null);

  useEffect(() => shell.load(stage.current!), [shell, view.round]);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => shell.press(e.key, e.repeat);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [shell]);
  const over = ranking !== null;
  useEffect(() => shell.watch(over), [shell, over]);

  if (ranking && view.cheered) {
    // the game's own ranking: unseated joiners are not in it
    const order = new Map(ranking.map((id, i) => [id, i]));
    const ranked = players.filter((p) => order.has(p.id)).sort((a, b) => order.get(a.id)! - order.get(b.id)!);
    return <TvFinal players={ranked} conn={conn} ranked jingle={false} />;
  }
  return (
    <section className={className} ref={stage}>
      {view.state !== "ready" && <p className={`${className}-wait`}>{view.state === "failed" ? props.failed : props.waiting}</p>}
      {view.settings && <TvSettingsPanel onClose={shell.closeSettings} />}
    </section>
  );
}
