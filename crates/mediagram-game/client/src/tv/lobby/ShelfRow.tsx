import { useLayoutEffect, useRef } from "react";
import { windowStart } from "../../ui/shelf";
import type { ShelfItem } from "../../ui/shelf-games";
import { ShelfPoster } from "./ShelfPoster";

/** How many posters the row shows whole; `tv-shelf.css` sizes the row to this many and a peek of the next. */
const VISIBLE = 5;

/**
 * One half of the shelf as a poster row: five posters at a time, the next one peeking in. It moves
 * a whole poster at a time, only as far as it takes to keep the focused one on screen. Mounted
 * afresh for each half, so the posters rise in one after the other whenever a half opens, and it
 * opens on the focused poster rather than gliding there (back from a game, too).
 */
export function ShelfRow({ items, index, focused, onPoster }: { items: ShelfItem[]; index: number; focused: boolean; onPoster: (i: number) => void }) {
  const row = useRef<HTMLUListElement>(null);
  const opened = useRef<number | null>(null);
  if (opened.current === null) opened.current = windowStart(0, index, VISIBLE);
  const start = useRef(opened.current);
  const glide = useRef(false);

  useLayoutEffect(() => {
    const el = row.current;
    if (!el) return;
    start.current = windowStart(start.current, index, VISIBLE);
    const posters = el.children as HTMLCollectionOf<HTMLElement>;
    const left = posters[start.current].offsetLeft - posters[0].offsetLeft;
    if (left !== el.scrollLeft) el.scrollTo({ left, behavior: glide.current ? "smooth" : "auto" });
    glide.current = true;
  }, [index]);

  return (
    <ul className="shelf-row" ref={row}>
      {items.map((it, i) => (
        <ShelfPoster
          key={it.key}
          item={it}
          // the posters on screen when the half opens rise in order; the rest are already up
          reveal={Math.max(0, Math.min(VISIBLE, i - opened.current!))}
          picked={i === index}
          focused={i === index && focused}
          onClick={() => onPoster(i)}
        />
      ))}
    </ul>
  );
}
