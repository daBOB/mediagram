import type { Connection } from "../net";
import type { AvatarSlot } from "../protocol/AvatarSlot";
import { avatarHeadUrl } from "../ui/Token";
import { rememberAvatar } from "./avatar-memory";

/** The catalogue as a grid of heads: mine highlighted, taken ones greyed out. */
export function AvatarPicker({ avatars, mine, conn }: { avatars: AvatarSlot[]; mine: string | null; conn: Connection }) {
  return (
    <div className="avatar-picker">
      <h2 className="avatar-title" id="avatar-title">
        Wähle deinen Avatar
      </h2>
      <div className="avatar-grid" role="group" aria-labelledby="avatar-title">
        {avatars.map((a) => (
          <button
            key={a.id}
            className={`avatar-pick${a.id === mine ? " is-selected" : ""}`}
            aria-pressed={a.id === mine}
            aria-label={a.id}
            disabled={a.taken}
            onClick={() => {
              if (a.id === mine) return;
              rememberAvatar(a.id);
              conn.send({ t: "action", action: { t: "pick_avatar", id: a.id } });
            }}
          >
            <img src={avatarHeadUrl(a.id)} alt="" loading="lazy" onError={(e) => (e.currentTarget.style.visibility = "hidden")} />
          </button>
        ))}
      </div>
    </div>
  );
}
