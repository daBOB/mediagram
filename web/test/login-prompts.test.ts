import { expect, test } from "bun:test";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

test("real terminal hides a password and restores ordinary prompt echo", async () => {
  const root = await mkdtemp(join(tmpdir(), "login-prompts-"));
  const resultPath = join(root, "answers.json");
  const modulePath = new URL("../src/login/prompts.ts", import.meta.url).pathname;
  const password = "pty-only-DUMMY-PASSWORD-8v!2";
  const ordinary = "visible-answer-4x";
  let output = "";
  try {
    const child = Bun.spawn([process.execPath, "-e", `
      import { loginPrompts } from ${JSON.stringify(modulePath)};
      import { writeFile } from "node:fs/promises";
      if (!process.stdin.isTTY || !process.stderr.isTTY) throw new Error("a real terminal is required");
      const prompts = loginPrompts();
      try {
        const hidden = await prompts.hidden("2FA password (hidden): ");
        const ordinary = await prompts.ask("Ordinary answer: ");
        await writeFile(${JSON.stringify(resultPath)}, JSON.stringify({ hidden, ordinary }));
      } finally { prompts.close(); }
    `], {
      cwd: root,
      env: { TERM: "xterm-256color" },
      timeout: 5000,
      killSignal: "SIGKILL",
      terminal: { cols: 120, rows: 24, data: (_terminal, data) => { output += Buffer.from(data).toString("utf8"); } },
    });
    const terminal = child.terminal;
    const waitFor = async (text: string) => {
      const deadline = Date.now() + 2000;
      while (!output.includes(text)) {
        if (Date.now() >= deadline || child.exitCode !== null) throw new Error(`Missing prompt ${text}: ${output}`);
        await Bun.sleep(5);
      }
    };
    try {
      expect(terminal).not.toBeNull();
      await waitFor("2FA password (hidden): ");
      terminal!.write(password + "\n");
      await waitFor("Ordinary answer: ");
      terminal!.write(ordinary + "\n");
      await waitFor(ordinary);
      expect(await child.exited).toBe(0);
      expect(JSON.parse(await readFile(resultPath, "utf8"))).toEqual({ hidden: password, ordinary });
      expect(output).not.toContain(password);
      expect(output).toContain(ordinary);
    } finally {
      const forceKill = setTimeout(() => child.kill("SIGKILL"), 250);
      try { child.kill(); await child.exited; }
      finally { clearTimeout(forceKill); terminal?.close(); }
    }
    expect(terminal?.closed).toBe(true);
  } finally { await rm(root, { recursive: true, force: true }); }
}, 8000);
