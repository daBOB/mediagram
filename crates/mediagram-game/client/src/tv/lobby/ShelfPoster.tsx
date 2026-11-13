import { useState, type CSSProperties } from "react";
import { gameArtUrl } from "../../ui/shelf";
import { BADGE_TEXT, type ShelfItem } from "../../ui/shelf-games";

/**
 * One game's poster, which carries its own title; a game without one shows its name on the felt.
 * A game not playable yet wears its badge, WIP or Coming soon, at its foot. `reveal` is its place in
 * the row's rise-in, from 0.
 */
export function ShelfPoster({ item, reveal, focused, picked, onClick }: { item: ShelfItem; reveal: number; focused: boolean; picked: boolean; onClick: () => void }) {
  const [missing, setMissing] = useState(false);
  const cls = ["shelf-poster", item.badge && "is-upcoming", picked && "is-picked", focused && "is-focused"].filter(Boolean).join(" ");
  return (
    <li className={cls} style={{ "--reveal": reveal } as CSSProperties} aria-current={picked || undefined} onClick={onClick}>
      {missing ? (
        <span className="shelf-poster-name">{item.name}</span>
      ) : (
        <img src={gameArtUrl(item.key, "poster")} alt={item.name} onError={() => setMissing(true)} />
      )}
      {item.badge && <span className={`shelf-poster-badge is-${item.badge}`}>{BADGE_TEXT[item.badge]}</span>}
    </li>
  );
}
