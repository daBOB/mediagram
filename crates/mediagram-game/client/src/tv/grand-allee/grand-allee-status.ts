// What the turn is doing right now, in words for the HUD's banner, and the dice's result to show beside it.

import type { GrandAlleeScene } from "../../protocol/GrandAlleeScene";
import type { PlayerInfo } from "../../protocol/PlayerInfo";
import { taler } from "../bauherr/bauherr-status";

const PERMIT_WORD = { approved: "Genehmigt!", rejected: "Abgelehnt!", free: "Gratis!", double: "Doppelt!" } as const;
export const nights = (n: number) => `${n} ${n === 1 ? "Nacht" : "Nächte"}`;

/** Whom the banner is about: the mover, the seller at an auction, the winner at the end. */
export function onTurn(scene: GrandAlleeScene): number {
  const t = scene.turn;
  if (t.stage === "auction") return t.auction.seller;
  if (t.stage === "sold") return t.seller;
  if (t.stage === "final") return t.winner;
  return t.player;
}

export function describeTurn(scene: GrandAlleeScene, players: PlayerInfo[], now: number): { text: string; die: string | null } {
  const name = (id: number) => players.find((p) => p.id === id)?.name ?? "Jemand";
  const hotel = (h: number) => scene.board.hotels[h]?.name ?? "";
  const t = scene.turn;
  switch (t.stage) {
    case "ask":
      return { text: `${name(t.player)}: ${t.prompt}`, die: null };
    case "rolled":
      return now < t.lands_at ? { text: `${name(t.player)} würfelt …`, die: "…" } : { text: `${name(t.player)} würfelt eine ${t.value}.`, die: String(t.value) };
    case "walking":
      return { text: `${name(t.player)} zieht mit einer ${t.value}${t.bank ? " und kassiert an der Bank." : "."}`, die: String(t.value) };
    case "guest":
      return now < t.lands_at
        ? { text: `${name(t.player)} übernachtet im ${hotel(t.hotel)} – wie viele Nächte?`, die: "…" }
        : { text: `${name(t.player)} zahlt ${name(t.owner)} ${taler(t.amount)} für ${nights(t.nights)} im ${hotel(t.hotel)}.`, die: nights(t.nights) };
    case "permit":
      return now < t.lands_at
        ? { text: `Baugenehmigung für ${hotel(t.hotel)} …`, die: "…" }
        : { text: `${hotel(t.hotel)}: ${PERMIT_WORD[t.permit]}`, die: PERMIT_WORD[t.permit] };
    case "auction":
      return { text: `${name(t.auction.seller)} versteigert ${hotel(t.auction.hotel)}.`, die: null };
    case "sold":
      return t.winner === null
        ? { text: `Kein Gebot: Die Bank kauft ${hotel(t.hotel)} für ${taler(t.price)}.`, die: null }
        : { text: `${name(t.winner)} ersteigert ${hotel(t.hotel)} für ${taler(t.price)}.`, die: null };
    case "note":
      return { text: t.text, die: null };
    case "final":
      return { text: `Das Spiel ist aus – ${name(t.winner)} gewinnt!`, die: null };
  }
}
