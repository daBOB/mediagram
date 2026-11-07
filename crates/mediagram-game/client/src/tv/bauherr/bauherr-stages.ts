// Which parts of each hotel are shown, and how a stage rises: an unowned lot shows its for-sale sign, a bought one its
// raw ground with the owner's property line and sign, then each stage that stands adds its walls, owner pieces and
// shadow. A new stage goes up inside a scaffold in 2.8 s (walls rise from the ground; a facility is laid out along the lot).

import type { BauherrEstate } from "../../protocol/BauherrEstate";
import type { HotelRec } from "./bauherr-data";
import type { HotelView } from "./bauherr-world";

export const RISE_S = 2.8;
type Range = [number, number];
export interface Shown {
  base: Range;
  owner: Range;
  /** Index range of the shadow decals: those of every stage standing. */
  shadow: Range;
}

const smooth = (a: number, b: number, x: number) => {
  const t = Math.min(1, Math.max(0, (x - a) / (b - a)));
  return t * t * (3 - 2 * t);
};
const span = (stages: HotelRec["stages"], n: number, key: "p" | "o"): Range => (n > 0 ? [stages[0][key][0], stages.slice(0, n).reduce((a, s) => a + s[key][1], 0)] : [0, 0]);

/** What is drawn of a hotel with `built` stages standing; `owned` false shows the lot for sale. */
export function shownRanges(h: HotelRec, owned: boolean, built: number, shadowStages = built): Shown {
  const base: Range = !owned ? [h.sale[0], h.sale[1] + h.overlay[1]] : built === 0 ? h.overlay : span(h.stages, built, "p");
  const ownerEnd = owned ? h.own0[1] + (built > 0 ? span(h.stages, built, "o")[1] : 0) : 0;
  const withShadow = h.stages.slice(0, shadowStages).filter((s) => s.shadow[1] > 0);
  const last = withShadow[withShadow.length - 1];
  return { base, owner: [0, ownerEnd], shadow: [0, last ? last.shadow[0] + last.shadow[1] : 0] };
}

/** Moves a hotel's state on to time `t` (seconds) and applies it; `color` is the owner's colour. */
export function stepHotel(h: HotelView, estate: BauherrEstate | undefined, color: string | null, t: number) {
  const target = estate?.levels ?? 0, owned = estate !== undefined, rec = h.rec;
  if (h.owned === null || target < h.built) {   // first sight, or a new game: show it as it stands, with no building
    h.built = target;
    h.rising = null;
  }
  if (h.rising && t - h.rising.t0 >= RISE_S) {
    fade(h, h.rising.stage, 1);
    h.built = h.rising.stage + 1;
    h.rising = null;
  }
  if (!h.rising && target > h.built) h.rising = { stage: h.built, t0: t };
  if (color && h.tintKey !== color) {
    h.tint.value.set(color);
    h.tintKey = color;
  }
  h.owned = owned;

  const shown = shownRanges(rec, owned, h.built, h.built + (h.rising ? 1 : 0));
  h.base.geometry.setDrawRange(...shown.base);
  h.owner.geometry.setDrawRange(...shown.owner);
  h.shadow.geometry.setDrawRange(...shown.shadow);
  const r = h.rising;
  h.rise.visible = h.scaf.visible = r !== null;
  if (!r) return;
  const st = rec.stages[r.stage], v = Math.min(1, (t - r.t0) / RISE_S);
  h.rise.geometry.setDrawRange(...st.p);
  h.scaf.geometry.setDrawRange(...st.scaf);
  h.scaf.visible = st.scafTop > 0;
  if (st.axis === "y") {
    h.riseClip.normal.set(0, -1, 0);
    h.riseClip.constant = smooth(0.12, 0.9, v) * (st.top + 0.03);
    h.scafClip.constant = (v < 0.9 ? smooth(0, 0.14, v) : 1 - smooth(0.9, 0.995, v)) * (st.scafTop + 0.05);
  } else {
    const [dx, dz] = rec.dir;
    h.riseClip.normal.set(-dx, 0, -dz);
    h.riseClip.constant = dx * rec.x + dz * rec.z + st.x0 + v * (st.x1 - st.x0);
  }
  fade(h, r.stage, smooth(0.35, 1, v));
}
// a stage's shadow fades in as it rises: strength on the decal's four vertices
function fade(h: HotelView, stage: number, k: number) {
  const range = h.rec.stages[stage].shadow;
  if (range[1] === 0) return;
  const first = h.shadow.geometry.index!.getX(range[0]);
  h.shade.fill(k, first * 3, (first + 4) * 3);
  h.shadow.geometry.attributes.color.needsUpdate = true;
}
