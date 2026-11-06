import { useState, type CSSProperties } from "react";
import type { PlayerInfo } from "../protocol/PlayerInfo";

export const avatarHeadUrl = (id: string) => `/avatars/${id}-head.webp`;

/** A player's pawn: their colour with their avatar's head, or the initial without one. */
export function Token({ player, size = "md" }: { player: PlayerInfo; size?: "sm" | "md" | "lg" }) {
  const initial = [...player.name.trim()][0]?.toUpperCase() ?? "?";
  // The image may be missing; remember which avatar failed and show the initial instead.
  const [failed, setFailed] = useState<string | null>(null);
  const head = player.avatar && failed !== player.avatar ? player.avatar : null;
  return (
    <span
      className={`token token-${size}${player.connected ? "" : " token-away"}`}
      style={{ "--player": player.color } as CSSProperties}
      aria-hidden="true"
    >
      {head ? <img className="token-head" src={avatarHeadUrl(head)} alt="" onError={() => setFailed(head)} /> : initial}
    </span>
  );
}
