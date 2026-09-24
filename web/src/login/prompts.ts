/** Terminal IO for setup; importing this module does not open stdin. */
import { createInterface } from "node:readline/promises";
import { stderr, stdin } from "node:process";

export interface LoginPrompts {
  ask(question: string): Promise<string>;
  hidden(question: string): Promise<string>;
  close(): void;
}

export function loginPrompts(): LoginPrompts {
  const rl = createInterface({ input: stdin, output: stderr, terminal: true });
  return {
    ask: (question) => rl.question(question),
    async hidden(question) {
      // A second reader of stdin competes with readline; mute its echo instead.
      const muted = rl as unknown as { _writeToOutput?: (text: string) => void };
      const original = muted._writeToOutput;
      muted._writeToOutput = (text) => { if (text.includes(question)) stderr.write(question); };
      try { return await rl.question(question); }
      finally { muted._writeToOutput = original; stderr.write("\n"); }
    },
    close: () => rl.close(),
  };
}
