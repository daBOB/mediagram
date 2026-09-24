/** Terminal IO for setup; importing this module does not open stdin. */
import { createInterface } from "node:readline/promises";
import { stderr, stdin } from "node:process";
import { Writable } from "node:stream";

export interface LoginPrompts {
  ask(question: string): Promise<string>;
  hidden(question: string): Promise<string>;
  close(): void;
}

export function loginPrompts(): LoginPrompts {
  let hidden = false;
  const output = new Writable({
    write(chunk, _encoding, done) {
      if (!hidden) stderr.write(chunk);
      done();
    },
  });
  Object.defineProperty(output, "columns", { get: () => stderr.columns });
  const resized = () => { output.emit("resize"); };
  stderr.on("resize", resized);
  const rl = createInterface({ input: stdin, output, terminal: true });
  return {
    ask: (question) => rl.question(question),
    async hidden(question) {
      // Gate the public output stream: Bun does not use readline's private
      // _writeToOutput hook. Keep one stdin reader for both kinds of prompt.
      hidden = true;
      stderr.write(question);
      try { return await rl.question(""); }
      finally { hidden = false; stderr.write("\n"); }
    },
    close() { rl.close(); stderr.off("resize", resized); output.destroy(); },
  };
}
