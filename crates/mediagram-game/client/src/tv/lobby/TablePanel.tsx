import type { PlayerInfo } from "../../protocol/PlayerInfo";
import { Token } from "../../ui/Token";

/** A note line takes a seat's room: more than four seats, or more than two beside a note, go two to a row
 * with a smaller code, so the panel still ends above the posters. */
export const isCrowded = (seats: number, noted: boolean) => seats > (noted ? 2 : 4);

/**
 * Who sits at the table and how to join: the code is big while nobody has, smaller once someone
 * has (latecomers walk up to it). The settings button below takes the remote's Up.
 */
export function TablePanel({
  players,
  joinUrl,
  settingsFocused,
  onSettings,
  filmsAway,
  boardChip,
}: {
  players: PlayerInfo[];
  joinUrl: string;
  settingsFocused: boolean;
  onSettings: () => void;
  filmsAway: boolean;
  /** Set while Kritzelei is the picked game: whether the drawing board is connected. */
  boardChip?: boolean;
}) {
  const empty = players.length === 0;
  const crowded = isCrowded(players.length, filmsAway || boardChip !== undefined);
  return (
    <aside className={`table-panel${empty ? " is-empty" : ""}${crowded ? " is-crowded" : ""}`}>
      <figure className="table-qr">
        <img src="/qr.svg" alt={`QR-Code für ${joinUrl}`} />
      </figure>
      <p className="table-url">{joinUrl.replace(/^https?:\/\//, "").replace(/\/$/, "")}</p>
      {empty ? (
        <p className="table-empty">Noch niemand am Tisch.</p>
      ) : (
        <ul className="table-seats">
          {players.map((p) => (
            <li key={p.id} className="table-seat">
              <Token player={p} size="sm" />
              <span className="table-seat-name">
                {p.name}
                {p.leader && " ★"}
              </span>
            </li>
          ))}
        </ul>
      )}
      {filmsAway && <p className="table-note">Die Filme der Mediathek sind gerade nicht erreichbar.</p>}
      {boardChip !== undefined && <p className="table-note">{boardChip ? "Zeichenbrett bereit" : "Zeichenbrett fehlt"}</p>}
      <button className={`table-settings${settingsFocused ? " is-focused" : ""}`} tabIndex={-1} onClick={onSettings}>
        Einstellungen
      </button>
    </aside>
  );
}
