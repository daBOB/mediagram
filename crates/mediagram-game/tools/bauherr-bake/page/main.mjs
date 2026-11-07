// Entry of the bake page: builds the hotels, the landscape and the ground print, then hands every image and the geometry
// to the driver. Runs once in headless Chrome (tools/bauherr-bake/bake.mjs); the TV never paints any of this.
import { FIELDS, LOTS, HOTEL_NAMES, GROUND } from '../../../client/src/tv/bauherr/bauherr-layout.ts';
import { makeLight } from './light.mjs';
import { loadTextures, setLight } from './paint.mjs';
import { Atlas, Part } from './atlas.mjs';
import { buildHotel, SCALE } from './hotel-kit.mjs';
import { hotelShadows } from './hotel-shadows.mjs';
import { kastanienhof, seeblick, waldhaus, alpenrose } from './hotels-a.mjs';
import { stadtpalais, sternwarte, kurhaus, leuchtturm } from './hotels-b.mjs';
import { mountains, hill, coast, cityBlocks, bridge } from './scenery-terrain.mjs';
import { plant, lamps, plaza, park } from './scenery-trees.mjs';
import { paintGround, paintOuter, OUTER } from './ground.mjs';
import * as K from './props.mjs';
import * as OP from './owner-props.mjs';
import { Pack, saveImage } from './export.mjs';
import { ivoryDie, oakDie } from './dice-faces.mjs';
import { look } from './look.mjs';
import { log, post, save, reseed } from './util.mjs';

const DEFS = { Kastanienhof: kastanienhof, Seeblick: seeblick, Waldhaus: waldhaus, Stadtpalais: stadtpalais, Alpenrose: alpenrose, Sternwarte: sternwarte, Kurhaus: kurhaus, Leuchtturm: leuchtturm };
const t0 = performance.now(), mark = (m) => log(`${((performance.now() - t0) / 1000).toFixed(1)} s  ${m}`);

async function main() {
  await Promise.all(['700', '800'].map((w) => document.fonts.load(`${w} 30px "Bricolage Grotesque"`)));
  await loadTextures();
  reseed(7);
  const light = makeLight(look.light); setLight(light);
  const pack = new Pack(), world = { hotels: [], fields: FIELDS.map((f) => [+f.x.toFixed(3), +f.z.toFixed(3), +f.ang.toFixed(4)]), ground: GROUND, up: light.up };
  const sh = { polys: [], trees: [], lines: [], ao: [] };

  // ---------- hotels ----------
  for (const lot of LOTS) {
    const name = HOTEL_NAMES[lot.plot], def = DEFS[name](name), H = buildHotel(def, lot, light, look, def.atlas);
    H.A.pack();
    const base = pack.mesh(H.P.finish()), owner = pack.mesh(H.O.finish()), shadows = hotelShadows(H, light);
    const image = `hotel-${lot.plot}`;
    await saveImage(image, H.A.draw());
    await saveImage(`${image}-shadow`, shadows.canvas, 0.8);
    const shadow = pack.mesh({ pos: shadows.pos, uv: shadows.uv, idx: shadows.idx });
    const c = Math.cos(lot.rot), s = Math.sin(lot.rot);
    world.hotels.push({
      plot: lot.plot, name, x: lot.x, z: lot.z, rot: lot.rot, dir: [c, -s], image, base, owner, shadow, sale: H.sale, overlay: H.overlay, own0: H.own0,
      stages: H.stages.map((st, i) => ({ p: st.p, o: st.o, top: +((st.top ?? 0.3) * SCALE).toFixed(3), axis: st.axis ?? 'y', x0: +((st.x0 ?? 0) * SCALE).toFixed(3), x1: +((st.x1 ?? 0) * SCALE).toFixed(3),
        scaf: st.scafRange ?? [0, 0], scafTop: st.scaf ? +(st.scaf.h * SCALE).toFixed(3) : 0, shadow: shadows.ranges[i] })),
    });
    mark(`${name}: ${Math.round(H.A.scale * 240 / SCALE)} px/unit, atlas ${Math.round(H.A.used * 100)} % used`);
  }

  // ---------- the landscape round the board ----------
  const TA = new Atlas(2048, 2048, 200), TP = new Part(TA, light);
  const faces = { leaves: TA.face(1, 1, K.leavesPaint(look.leaves), 256), firs: TA.face(1, 1, K.leavesPaint(look.firs), 256) };
  mountains(TP, sh); hill(TP, sh); coast(TP, sh); cityBlocks(TP, sh, look); bridge(TP, sh);
  plant(TP, sh, faces);
  world.halos = [...lamps(TP, sh), ...plaza(TP, sh), ...park(TP, sh)];
  TA.pack();
  await saveImage('town', TA.draw());
  world.town = pack.mesh(TP.finish());
  mark(`town: ${TP.idx.length / 3} triangles, atlas ${Math.round(TA.used * 100)} % used`);

  // entrance canopies, drawn once at the origin and placed by the TV in each owner's colour
  const CA = new Atlas(512, 512, 200), CO = new Part(CA, light, { tint: 1 });
  OP.canopy(CO);
  CA.pack();
  await saveImage('canopy', CA.draw());
  world.canopy = pack.mesh(CO.finish());

  // ---------- the ground print, with every static shadow in it ----------
  const ground = paintGround(light, sh);
  mark('ground painted');
  await saveImage('ground', ground, 0.84);
  await saveImage('outer', paintOuter(), 0.8);
  world.outer = OUTER;

  await saveImage('die-ivory', ivoryDie(), 0.9);
  await saveImage('die-oak', oakDie(), 0.9);

  await save('world.bin', pack.blob());
  await save('world.json', new Blob([JSON.stringify(world)]));
  mark('done');
}
main().then(() => post('/done', 'ok'), (e) => post('/done', String(e?.stack ?? e)));
