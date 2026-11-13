import { useState } from "react";
import type { GameEntry } from "../../protocol/GameEntry";
import { gameArtUrl } from "../../ui/shelf";

/** One game's poster, which carries its own title; a game without one shows its name on the felt. */
export function ShelfPoster({ game, focused, picked, onClick }: { game: GameEntry; focused: boolean; picked: boolean; onClick: () => void }) {
  const [missing, setMissing] = useState(false);
  const cls = ["shelf-poster", picked && "is-picked", focused && "is-focused"].filter(Boolean).join(" ");
  return (
    <li className={cls} aria-current={picked || undefined} onClick={onClick}>
      {missing ? (
        <span className="shelf-poster-name">{game.name}</span>
      ) : (
        <img src={gameArtUrl(game.kind, "poster")} alt={game.name} onError={() => setMissing(true)} />
      )}
    </li>
  );
}
