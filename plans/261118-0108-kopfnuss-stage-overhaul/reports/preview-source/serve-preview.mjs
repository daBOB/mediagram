// Serves the real client with a preview page for the Kopfnuss stages. Nothing is written into the repo.
import fs from "node:fs";
import path from "node:path";
// Found from this file's place in the checkout, so the preview runs from any worktree. The avatar copies are
// generated and not in git: a checkout without them shows the flat figures.
const ROOT = path.resolve(import.meta.dirname, "../../../..");
const CLIENT = ROOT + "/crates/mediagram-game/client";
const AVATARS = ROOT + "/android/spieltisch/src/main/assets/game-avatars";
const { createServer } = await import(CLIENT + "/node_modules/vite/dist/node/index.js");
const { createRequire } = await import("node:module");
const loaded = createRequire(CLIENT + "/package.json")("@vitejs/plugin-react");
const react = loaded.default ?? loaded;
const VIRTUAL = CLIENT + "/src/__kopfnuss_preview__.tsx";
const html = `<!doctype html><html lang="de"><head><meta charset="UTF-8"><title>loading</title>
<style>html.bare .kn-show-content{visibility:hidden}</style></head>
<body><div id="root"></div><script type="module" src="/src/__kopfnuss_preview__.tsx"></script></body></html>`;
const server = await createServer({
  root: CLIENT,
  configFile: false,
  plugins: [react(), {
    name: "kopfnuss-preview",
    enforce: "pre",
    resolveId(id) { if (id === "/src/__kopfnuss_preview__.tsx" || id === VIRTUAL) return VIRTUAL; },
    load(id) { if (id === VIRTUAL) return fs.readFileSync(import.meta.dirname + "/preview.tsx", "utf8"); },
    configureServer(s) {
      s.middlewares.use((req, res, next) => {
        const url = (req.url ?? "").split("?")[0];
        if (url.startsWith("/game-avatars/")) {
          const file = path.join(AVATARS, path.basename(url));
          if (fs.existsSync(file)) { res.setHeader("Content-Type", "model/gltf-binary"); fs.createReadStream(file).pipe(res); return; }
        }
        if (url === "/preview-kopfnuss.html") {
          s.transformIndexHtml(url, html).then((out) => { res.setHeader("Content-Type", "text/html"); res.end(out); });
          return;
        }
        next();
      });
    },
  }],
  server: { port: 8742, host: "127.0.0.1", strictPort: true },
});
await server.listen();
console.log("preview ready on http://127.0.0.1:8742/preview-kopfnuss.html");
