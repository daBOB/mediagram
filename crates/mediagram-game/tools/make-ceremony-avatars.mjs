// Makes the awards-ceremony copy of each game-night avatar from its 2K game copy: only the five clips the podium
// plays, all ~51k triangles (the TV box draws three at ~44 fps at full resolution; the user's floor is 30, and 30k
// looked low-poly), colour and normal maps re-unwrapped into padded charts and baked from the 4K master's maps
// (rebake-texture-atlas.mjs) into WebP (EXT_texture_webp, which GLTFLoader reads natively): colour 2048, normal 1024
// (fabric folds and fur relief; an avatar is ~600 px tall on the TV). Compressed with EXT_meshopt_compression (the
// page needs MeshoptDecoder). No metallic/roughness map: the look is matte Lambert.
// Usage: bun crates/mediagram-game/tools/make-ceremony-avatars.mjs [--tris=N] [--tex=2048] [--ntex=1024] [--out=dir] [id ...]
// Reads assets/game-night/avatars/<id>/<id>.glb and <id>-master.glb (kept out of git), writes
// android/app/src/main/assets/game-avatars/<id>.glb (generated and gitignored; the TV app serves it to the game page).
// Resolves its packages from the client's (bun install in crates/mediagram-game/client).
import { existsSync, mkdirSync, readdirSync, statSync } from 'node:fs';
import { createRequire } from 'node:module';
import { padTextureIslands } from './pad-texture-islands.mjs';
import { rebakeTextureAtlas } from './rebake-texture-atlas.mjs';

const here = (p) => new URL(p, import.meta.url).pathname;
const IN_DIR = here('../../../assets/game-night/avatars/');
const KEEP = ['Idle_15', 'victory', 'Happy_Jump', 'Cheer_With_One_Hand_Up', 'Shrug'];

const opt = (name, dflt) => process.argv.find((a) => a.startsWith(`--${name}=`))?.slice(name.length + 3) ?? dflt;
const TRIS = Number(opt('tris', Infinity));
const TEX = Number(opt('tex', 2048));
const NTEX = Number(opt('ntex', 1024));
const OUT_DIR = opt('out', here('../../../android/app/src/main/assets/game-avatars/')).replace(/\/?$/, '/');
if (!(TRIS > 0) || !(TEX > 0) || !(NTEX > 0)) throw new Error('--tris, --tex and --ntex must be positive numbers');

const req = createRequire(here('../client/package.json'));
let core, extensions, functions, sharp, MeshoptEncoder, MeshoptDecoder, MeshoptSimplifier;
try {
  core = req('@gltf-transform/core');
  extensions = req('@gltf-transform/extensions');
  functions = req('@gltf-transform/functions');
  sharp = req('sharp');
  ({ MeshoptEncoder, MeshoptDecoder, MeshoptSimplifier } = req('meshoptimizer'));
} catch (e) {
  throw new Error(`packages are not installed: run bun install in crates/mediagram-game/client (${e.message})`);
}
const { NodeIO } = core;
const { prune, dedup, weld, simplify, quantize, textureCompress, meshopt } = functions;
const io = new NodeIO().registerExtensions(extensions.ALL_EXTENSIONS)
  .registerDependencies({ 'meshopt.encoder': MeshoptEncoder, 'meshopt.decoder': MeshoptDecoder });

const triangles = (root) => root.listMeshes().flatMap((m) => m.listPrimitives())
  .reduce((n, p) => n + p.getIndices().getCount() / 3, 0);

const all = readdirSync(IN_DIR).filter((id) => existsSync(`${IN_DIR}${id}/${id}.glb`)).sort();
const ids = process.argv.slice(2).filter((a) => !a.startsWith('--'));
if (ids.length === 0) ids.push(...all);
for (const id of ids) if (!all.includes(id)) throw new Error(`no ${IN_DIR}${id}/${id}.glb`);
mkdirSync(OUT_DIR, { recursive: true });
await Promise.all([MeshoptEncoder.ready, MeshoptDecoder.ready, MeshoptSimplifier.ready]);

/** The rebake samples the 4K colour and normal maps of `<id>-master.glb` instead of the game copy's 2K ones; both
 *  files carry the same mesh and texture coordinates, which is checked. */
async function useMasterMaps(doc, id) {
  const prim = (d) => d.getRoot().listMeshes()[0].listPrimitives()[0];
  const master = prim(await io.read(`${IN_DIR}${id}/${id}-master.glb`)), copy = prim(doc);
  const a = master.getAttribute('TEXCOORD_0').getArray(), b = copy.getAttribute('TEXCOORD_0').getArray();
  if (a.length !== b.length || a.some((x, i) => x !== b[i])) throw new Error(`${id}: master and game copy differ in texture coordinates`);
  const [mm, cm] = [master.getMaterial(), copy.getMaterial()];
  for (const slot of ['BaseColor', 'Normal']) {
    const from = mm[`get${slot}Texture`](), to = cm[`get${slot}Texture`]();
    if (!from || !to) throw new Error(`${id}: no ${slot} map in the master or the game copy`);
    to.setImage(from.getImage()).setMimeType(from.getMimeType());
  }
}

let total = 0;
for (const id of ids) {
  const doc = await io.read(`${IN_DIR}${id}/${id}.glb`);
  const root = doc.getRoot();
  const names = root.listAnimations().map((a) => a.getName());
  for (const k of KEEP) if (!names.includes(k)) throw new Error(`${id}: clip ${k} is missing (has ${names.join(', ')})`);
  for (const a of root.listAnimations()) if (!KEEP.includes(a.getName())) a.dispose();
  const before = triangles(root);
  for (const m of root.listMaterials()) {
    // Meshy's metallic/roughness map is near-uniform: its mean as a factor looks the same (Lambert ignores both anyway).
    const mr = m.getMetallicRoughnessTexture();
    if (mr) {
      const { channels } = await sharp(Buffer.from(mr.getImage())).stats();
      m.setRoughnessFactor(m.getRoughnessFactor() * channels[1].mean / 255);
      m.setMetallicFactor(m.getMetallicFactor() * channels[2].mean / 255);
      m.setMetallicRoughnessTexture(null);
    }
  }
  await useMasterMaps(doc, id);
  await padTextureIslands(doc); // on the full-detail mesh, so island extents are exact
  await doc.transform(prune(), weld());
  if (TRIS < before) {
    // Seams stay put (the default): every triangle stays inside one of Meshy's islands, so the rebake below can
    // interpolate its old texture coordinates (collapsing across them also smeared the islands over the face).
    await doc.transform(simplify({ simplifier: MeshoptSimplifier, ratio: TRIS / before, error: 0.1 }));
  }
  const rebaked = await rebakeTextureAtlas(doc, TEX);
  await doc.transform(
    textureCompress({ encoder: sharp, targetFormat: 'webp', slots: /^baseColorTexture$/, resize: [TEX, TEX], quality: 90 }),
    textureCompress({ encoder: sharp, targetFormat: 'webp', slots: /^normalTexture$/, resize: [NTEX, NTEX], quality: 95 }),
    quantize(),
    prune(),
    dedup(),
    meshopt({ encoder: MeshoptEncoder, level: 'high' }),
  );
  const out = `${OUT_DIR}${id}.glb`;
  await io.write(out, doc);
  // read back: the file on disk is what the app ships
  const back = (await io.read(out)).getRoot();
  const clips = back.listAnimations().map((a) => a.getName()).sort().join();
  if (clips !== [...KEEP].sort().join()) throw new Error(`${id}: wrote clips ${clips}`);
  const mat = back.listMaterials()[0], colour = mat?.getBaseColorTexture(), normal = mat?.getNormalTexture();
  if (back.listTextures().length !== 2 || [colour, normal].some((t) => t?.getMimeType() !== 'image/webp')) {
    throw new Error(`${id}: expected a webp colour and a webp normal map, got ${back.listTextures().map((t) => t.getMimeType()).join()}`);
  }
  const size = statSync(out).size;
  total += size;
  console.log(`${id}.glb ${(size / 1024).toFixed(0)} KB, ${before} -> ${triangles(back)} triangles, ${rebaked}, ` +
    `colour ${colour.getSize()?.join('x')}, normal ${normal.getSize()?.join('x')}`);
}
console.log(`total ${(total / 1048576).toFixed(2)} MB for ${ids.length} avatars`);
