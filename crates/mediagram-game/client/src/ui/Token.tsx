import type { CSSProperties } from "react";
import type { PlayerInfo } from "../protocol/PlayerInfo";

/** A player's pawn: their colour with their initial. */
export function Token({ player, size = "md" }: { player: PlayerInfo; size?: "sm" | "md" | "lg" }) {
  const initial = [...player.name.trim()][0]?.toUpperCase() ?? "?";
  return (
    <span
      className={`token token-${size}${player.connected ? "" : " token-away"}`}
      style={{ "--player": player.color } as CSSProperties}
      aria-hidden="true"
    >
      {initial}
    </span>
  );
}
