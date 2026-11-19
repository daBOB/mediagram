// usage: bun shoot.mjs <out-dir> stage[:bare] ...   Screenshots the preview page at 1920x1080.
const [out, ...shots] = process.argv.slice(2);
const B = `${process.env.HOME}/.claude/skills/gstack/browse/dist/browse`;
const run = (...a) => {
  const r = Bun.spawnSync([B, ...a]);
  return (r.stdout.toString() + r.stderr.toString()).trim();
};
const VIEW = process.env.VIEW ?? "1920x1080";
run("viewport", VIEW);
for (const shot of shots) {
  const [stage, bare] = shot.split(":");
  run("goto", `http://127.0.0.1:8742/preview-kopfnuss.html?stage=${stage}${bare ? "&bare=1" : ""}&n=${Date.now()}`);
  let title = "";
  for (let i = 0; i < 200 && !title.startsWith("ready"); i++) {
    await Bun.sleep(250);
    title = run("js", "document.title").split("\n").at(-1);
  }
  await Bun.sleep(Number(process.env.SETTLE ?? 7000)); // avatars and the camera settle
  run("screenshot", "--viewport", `${out}/kopfnuss-${stage}${bare ? "-bare" : ""}${VIEW === "1920x1080" ? "" : "-" + VIEW}.png`);
  console.log(shot, title, run("console", "--errors").split("\n").slice(-3).join(" | ").slice(0, 300));
}
