// Turns the baked GLBs' WebP atlases into KTX2 (KHR_texture_basisu: Basis ETC1S, sRGB, mipmaps, no alpha) at quality
// 255. The TV box transcodes them in a worker straight to GPU-compressed
// ETC1 (half a byte per texel, an eighth of RGBA), instead of decoding WebP, uploading it as RGBA and building mipmaps.
// Usage: bun glb-to-ktx2.mjs <out-dir> <in.glb> ...   (writes <out-dir>/<same name>.glb; the dice stay WebP: 256² is tiny)
// Needs bun install in client/ and toktx from KTX-Software 4.x on PATH (or TOKTX=/path/to/toktx); KTX_QLEVEL to try others.
import { mkdirSync, mkdtempSync, rmSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, join } from 'node:path';

const [outDir, ...inputs] = process.argv.slice(2);
if (!outDir || !inputs.length) {
  console.error('usage: bun glb-to-ktx2.mjs <out-dir> <in.glb> ...');
  process.exit(2);
}
const clientDir = new URL('../../client/', import.meta.url).pathname;
const esm = (name) => import(Bun.resolveSync(name, clientDir));
const [core, extensions, { default: sharp }] = await Promise.all(['@gltf-transform/core', '@gltf-transform/extensions', 'sharp'].map(esm));
const TOKTX = process.env.TOKTX ?? 'toktx';
// the most ETC1S takes: the GPU memory is the same at any level, and 192 saved only 5-10 % of the file
const QLEVEL = process.env.KTX_QLEVEL ?? '255';

const io = new core.NodeIO().registerExtensions(extensions.ALL_EXTENSIONS);
const work = mkdtempSync(join(tmpdir(), 'grand-allee-ktx2-'));
mkdirSync(outDir, { recursive: true });
try {
  for (const path of inputs) {
    const doc = await io.read(path), sizes = [];
    for (const [i, texture] of doc.getRoot().listTextures().entries()) {
      if (texture.getMimeType() === 'image/ktx2') continue;
      const png = join(work, `${i}.png`), ktx2 = join(work, `${i}.ktx2`);
      // the bake has no alpha: RGB only, so three transcodes to ETC1 rather than ETC2 with an alpha block
      await sharp(Buffer.from(texture.getImage())).removeAlpha().png().toFile(png);
      const args = ['--t2', '--encode', 'etc1s', '--clevel', '4', '--qlevel', QLEVEL, '--genmipmap', '--assign_oetf', 'srgb',
        '--assign_primaries', 'bt709', '--target_type', 'RGB', ktx2, png];
      const run = Bun.spawnSync([TOKTX, ...args], { stdout: 'inherit', stderr: 'inherit' });
      if (run.exitCode !== 0) throw new Error(`toktx failed on ${basename(path)} texture ${i}`);
      sizes.push(`${texture.getSize().join('x')} ${(texture.getImage().byteLength / 1024).toFixed(0)} -> ${(statSync(ktx2).size / 1024).toFixed(0)} KB`);
      texture.setImage(new Uint8Array(await Bun.file(ktx2).arrayBuffer())).setMimeType('image/ktx2');
    }
    if (sizes.length) {
      doc.createExtension(extensions.KHRTextureBasisu).setRequired(true);
      for (const e of doc.getRoot().listExtensionsUsed()) if (e.extensionName === 'EXT_texture_webp') e.dispose();
    }
    const out = join(outDir, basename(path));
    await io.write(out, doc);
    console.log(`${out}: ${sizes.join(', ') || 'no WebP textures'}; ${(statSync(path).size / 1024).toFixed(0)} -> ${(statSync(out).size / 1024).toFixed(0)} KB`);
  }
} finally {
  rmSync(work, { recursive: true, force: true });
}
