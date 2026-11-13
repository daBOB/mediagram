import { useCallback, useEffect, useRef, useState } from "react";
import type { Connection } from "../../net";
import type { GameKind } from "../../protocol/GameKind";
import type { LobbySettings } from "../../protocol/LobbySettings";

/** How long the row rests on a poster before the room hears the pick, so holding Right sends one, not ten. */
const SETTLE_MS = 180;

/**
 * The focused poster and the room's pick, kept together. The TV tells the room once the row
 * settles; when the leader's phone picks another game, the row follows it. Picks the TV sent and
 * the host has not echoed yet are remembered in order, so their echoes, however late, never pull
 * the row back; any other change of the room's game is someone else's pick.
 */
export function useShelfPick(settings: LobbySettings, conn: Connection) {
  const [focus, setFocus] = useState<GameKind>(settings.game);
  const inFlight = useRef<GameKind[]>([]);
  const latest = useRef(settings);
  latest.current = settings;
  const lastSent = () => inFlight.current[inFlight.current.length - 1] ?? latest.current.game;

  useEffect(() => {
    const i = inFlight.current.indexOf(settings.game);
    if (i >= 0) {
      inFlight.current = inFlight.current.slice(i + 1);
      return;
    }
    inFlight.current = [];
    setFocus(settings.game);
  }, [settings.game]);

  const configure = useCallback(
    (game: GameKind) => {
      // a pick the socket dropped is never echoed: keep the list short
      inFlight.current = [...inFlight.current.slice(-7), game];
      const { quiz_mode, kids_only, adult } = latest.current;
      conn.send({ t: "action", action: { t: "configure", game, quiz_mode, kids_only, adult } });
    },
    [conn],
  );

  useEffect(() => {
    if (focus === lastSent()) return;
    // OK may have sent the pick already, while this waited.
    const t = setTimeout(() => focus !== lastSent() && configure(focus), SETTLE_MS);
    return () => clearTimeout(t);
  }, [focus, configure]);

  /**
   * Starts the focused game. Unless the host already has it, it is picked first (again, if an earlier
   * pick was lost to a dropped connection), and the host judges the start.
   */
  const start = () => {
    if (focus !== latest.current.game) configure(focus);
    conn.send({ t: "action", action: { t: "start_game" } });
  };

  return { focus, setFocus, heard: focus === settings.game, start };
}
