// Renders the arena mockup for each name=query pair in headless Chrome at 1920×1080: out/<name>.png.
// usage: bun shoot-queries.mjs <out-dir> name='theme=studio&stage=fact&fx=1&zoom=4' ...
const [out, ...shots] = process.argv.slice(2);
const B = `${process.env.HOME}/.claude/skills/gstack/browse/dist/browse`;
const run = (...a) => {
  const r = Bun.spawnSync([B, ...a], { cwd: "/home/andre/Workspace/spieltisch/wt-dreifelder-merge" });
  return (r.stdout.toString() + r.stderr.toString()).trim();
};
run("viewport", "1920x1080");
for (const shot of shots) {
  const at = shot.indexOf("="), name = shot.slice(0, at), query = shot.slice(at + 1);
  run("goto", `http://127.0.0.1:8741/index.html?${query}&n=${Date.now()}`);
  let title = "";
  for (let i = 0; i < 160 && !title.startsWith("ready"); i++) {
    await Bun.sleep(250);
    title = run("js", "document.title").split("\n").at(-1);
  }
  if (!title.startsWith("ready")) console.log(name, "NOT READY", run("console", "--errors").slice(-600));
  if (query.includes("fx=1")) run("js", "setT(1.2)");
  run("screenshot", "--viewport", `${out}/${name}.png`);
  console.log(name, title);
}
