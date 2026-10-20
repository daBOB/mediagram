/**
 * While a cast plays on the TV, every page shows this bar at the bottom:
 * what is playing and a play/pause button. The text opens the remote; the bar
 * steps aside while the remote is open and returns when it closes.
 */

import { titleLine } from "../format.js";
import { ICONS, playLabel, setIcon } from "../playback/transport.js";
import { expectedPosition } from "../together/follow.js";
import { castElsewhere } from "../together/cast.js";
import { householdId } from "../household.js";
import { barLine } from "../together/cast-words.js";
import { onTogether, screen, serverNow, setForTogether, together, togetherNow } from "../together-now.js";
import { onRemote, openRemote, remoteOpen } from "./cast-remote.js";

const part = (name) => document.getElementById(`cast-bar${name ? `-${name}` : ""}`);

/** The live cast session this profile may see, on a TV of this device's household, or null. */
function castOn() {
  const { live } = togetherNow();
  const ours = live?.session.cast?.state === "on" && !castElsewhere(live.session, householdId());
  const set = ours ? setForTogether(live.session.set) : null;
  return set && { live, set };
}

function draw() {
  const found = remoteOpen() ? null : castOn();
  part("").hidden = !found;
  if (!found) return;
  const { live, set } = found;
  const { playing } = live.session;
  part("open").textContent = barLine(titleLine(set));
  const play = part("play");
  setIcon(play, playing ? ICONS.pause : ICONS.play);
  play.setAttribute("aria-label", playLabel(!playing));
}

export function mountCastBar() {
  part("open").addEventListener("click", () => {
    const found = castOn();
    if (found) openRemote(found.live);
  });
  part("play").addEventListener("click", () => {
    const found = castOn();
    if (!found) return;
    const { live } = found;
    const pos = expectedPosition(live.session, serverNow());
    void together({ op: "act", screen, id: live.id, change: { playing: !live.session.playing, pos } });
  });
  onTogether(draw);
  onRemote(draw);
  draw();
}
