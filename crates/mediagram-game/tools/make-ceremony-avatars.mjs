// Makes the small awards-ceremony copy of each game-night avatar: only the five clips the podium plays,
// compressed with EXT_meshopt_compression (the page needs MeshoptDecoder), no normal map (the look is matte Lambert, and normal maps cost the TV box ~16 % fps).
// Usage: bun crates/mediagram-game/tools/make-ceremony-avatars.mjs [id ...]   (no ids: all of them)
// Reads assets/game-night/avatars/<id>/<id>-tv.glb (kept out of git), writes android/app/src/main/assets/game-avatars/<id>.glb
// (generated and gitignored; the TV app serves it to the game page). Resolves @gltf-transform from the client's packages
// (bun install in crates/mediagram-game/client).
import { existsSync, mkdirSync, readdirSync, statSync } from 'node:fs';
import { createRequire } from 'node:module';

const here = (p) => new URL(p, import.meta.url).pathname;
const IN_DIR = here('../../../assets/game-night/avatars/');
const OUT_DIR = here('../../../android/app/src/main/assets/game-avatars/');
const KEEP = ['Idle', 'victory', 'Happy_Jump', 'Cheer_With_One_Hand_Up', 'Shrug'];

const req = createRequire(here('../client/package.json'));
let core, extensions, functions;
try {
  core = req('@gltf-transform/core');
  extensions = req('@gltf-transform/extensions');
  functions = req('@gltf-transform/functions');
} catch (e) {
  throw new Error(`@gltf-transform is not installed: run bun install in crates/mediagram-game/client (${e.message})`);
}
const { NodeIO } = core;
const { prune, dedup, meshopt } = functions;
const { MeshoptEncoder, MeshoptDecoder } = req('meshoptimizer');
const io = new NodeIO().registerExtensions(extensions.ALL_EXTENSIONS)
  .registerDependencies({ 'meshopt.encoder': MeshoptEncoder, 'meshopt.decoder': MeshoptDecoder });

const all = readdirSync(IN_DIR).filter((id) => existsSync(`${IN_DIR}${id}/${id}-tv.glb`)).sort();
const ids = process.argv.length > 2 ? process.argv.slice(2) : all;
for (const id of ids) if (!all.includes(id)) throw new Error(`no ${IN_DIR}${id}/${id}-tv.glb`);
mkdirSync(OUT_DIR, { recursive: true });

let total = 0;
for (const id of ids) {
  const doc = await io.read(`${IN_DIR}${id}/${id}-tv.glb`);
  const root = doc.getRoot();
  const names = root.listAnimations().map((a) => a.getName());
  for (const k of KEEP) if (!names.includes(k)) throw new Error(`${id}: clip ${k} is missing (has ${names.join(', ')})`);
  for (const a of root.listAnimations()) if (!KEEP.includes(a.getName())) a.dispose();
  for (const m of root.listMaterials()) m.setNormalTexture(null);
  await Promise.all([MeshoptEncoder.ready, MeshoptDecoder.ready]);
  await doc.transform(prune(), dedup(), meshopt({ encoder: MeshoptEncoder, level: 'high' }));
  const out = `${OUT_DIR}${id}.glb`;
  await io.write(out, doc);
  // read back: the file on disk is what the app ships
  const back = (await io.read(out)).getRoot();
  const clips = back.listAnimations().map((a) => a.getName()).sort().join();
  if (clips !== [...KEEP].sort().join()) throw new Error(`${id}: wrote clips ${clips}`);
  if (back.listMaterials().some((m) => m.getNormalTexture())) throw new Error(`${id}: normal map survived`);
  const size = statSync(out).size;
  total += size;
  console.log(`${id}.glb ${(size / 1024).toFixed(0)} KB, ${back.listTextures().length} texture(s)`);
}
console.log(`total ${(total / 1048576).toFixed(2)} MB for ${ids.length} avatars`);
