import type { BauherrScene } from "../../protocol/BauherrScene";
import type { PlayerInfo } from "../../protocol/PlayerInfo";
import { taler } from "./bauherr-draw";

const PERMIT_WORD = {
  approved: "Genehmigt!",
  rejected: "Abgelehnt!",
  free: "Gratis!",
  double: "Doppelt!",
} as const;

/** What the turn is doing right now, in words, and the die to show with it. */
export function describeTurn(scene: BauherrScene, players: PlayerInfo[], now: number): { text: string; die: string | null } {
  const name = (id: number) => players.find((p) => p.id === id)?.name ?? "Jemand";
  const t = scene.turn;
  const hotel = (plot: number) => scene.board.plots[plot].name;
  switch (t.stage) {
    case "roll":
      return { text: `${name(t.player)} ist dran und würfelt auf dem Handy.`, die: null };
    case "rolled":
      return now < t.lands_at
        ? { text: `${name(t.player)} würfelt …`, die: "…" }
        : { text: `${name(t.player)} würfelt eine ${t.value}.`, die: String(t.value) };
    case "walking":
      return { text: `${name(t.player)} zieht ${t.value} Felder${t.bonus ? " und kassiert an der Bank." : "."}`, die: String(t.value) };
    case "guest":
      return now < t.lands_at
        ? { text: `${name(t.player)} übernachtet im ${hotel(t.plot)} – wie viele Nächte?`, die: "…" }
        : {
            text: `${name(t.player)} zahlt ${name(t.owner)} ${taler(t.amount)} für ${t.nights} Nächte im ${hotel(t.plot)}.`,
            die: String(t.nights),
          };
    case "permit":
      return now < t.lands_at
        ? { text: `Baugenehmigung für ${hotel(t.plot)} …`, die: "…" }
        : { text: `${hotel(t.plot)}: ${PERMIT_WORD[t.outcome]}`, die: PERMIT_WORD[t.outcome] };
    case "ask":
      return { text: `${name(t.player)}: ${t.prompt}`, die: null };
    case "note":
      return { text: t.text, die: null };
    case "final":
      return { text: `Das Spiel ist aus – ${name(t.winner)} gewinnt!`, die: null };
  }
}
