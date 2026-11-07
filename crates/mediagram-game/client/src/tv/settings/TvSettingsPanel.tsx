import { useEffect, useState } from "react";
import { LEVELS, getSettings, subscribeSettings, updateSettings, type TvSettings } from "./tv-settings";
import "./tv-settings-panel.css";

declare global {
  interface Window {
    /** Called by the TV app on Back: true when the page used the key (closed the panel). */
    tvBack?: () => boolean;
  }
}

const ROWS = ["muted", "music", "effects", "figures"] as const;
const LABELS = { muted: "Ton", music: "Musik", effects: "Effekte", figures: "Spielfiguren" };

/** Steps the focused row's value by `dir` (-1 / +1). */
function step(row: (typeof ROWS)[number], dir: number, s: TvSettings) {
  if (row === "muted") updateSettings({ muted: !s.muted });
  else if (row === "figures") updateSettings({ figures: s.figures === "pawns" ? "avatars" : "pawns" });
  else updateSettings({ [row]: Math.min(LEVELS, Math.max(0, s[row] + dir)) });
}

function useSettings() {
  const [s, setS] = useState(getSettings);
  useEffect(() => subscribeSettings(setS), []);
  return s;
}

/**
 * The sound and figure settings, worked with the remote. Mounted = open: it
 * takes the arrow keys and OK while it is on screen and answers Back through
 * `window.tvBack`.
 */
export function TvSettingsPanel({ onClose }: { onClose: () => void }) {
  const s = useSettings();
  const [row, setRow] = useState(0);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const dx = e.key === "ArrowLeft" ? -1 : e.key === "ArrowRight" ? 1 : 0;
      if (e.key === "Escape" || e.key === "Backspace") onClose();
      else if (e.key === "ArrowDown") setRow((r) => (r + 1) % ROWS.length);
      else if (e.key === "ArrowUp") setRow((r) => (r + ROWS.length - 1) % ROWS.length);
      else if (dx) step(ROWS[row], dx, getSettings());
      // a held OK repeats: only the first press steps the value (and none flips sound on and off)
      else if (e.key === "Enter") {
        if (!e.repeat) step(ROWS[row], 1, getSettings());
      } else return;
      // the page behind (a focused lobby button, the board) must not hear these
      e.preventDefault();
      e.stopImmediatePropagation();
    };
    window.addEventListener("keydown", onKey, true);
    window.tvBack = () => (onClose(), true);
    return () => {
      window.removeEventListener("keydown", onKey, true);
      window.tvBack = () => false;
    };
  }, [row, onClose]);

  return (
    <div className="tv-set" role="dialog" aria-label="Einstellungen">
      <h2 className="tv-set-title">Einstellungen</h2>
      {ROWS.map((r, i) => (
        <div key={r} className={`tv-set-row${i === row ? " is-focus" : ""}`}>
          <span>{LABELS[r]}</span>
          {r === "music" || r === "effects" ? (
            <span className="tv-set-bar" aria-label={`${s[r]} von ${LEVELS}`}>
              {Array.from({ length: LEVELS }, (_, k) => (
                <i key={k} className={k < s[r] ? "" : "off"} />
              ))}
            </span>
          ) : (
            <span className="tv-set-pill">
              ◀ {r === "muted" ? (s.muted ? "Aus" : "An") : s.figures === "pawns" ? "Holzfiguren" : "Avatare"} ▶
            </span>
          )}
        </div>
      ))}
      <p className="tv-set-hint">◀▶ ändern · Zurück schließt</p>
    </div>
  );
}
