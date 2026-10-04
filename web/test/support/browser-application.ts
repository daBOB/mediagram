import { browserEnvironment, Node, Video, settle } from "./player-environment";

/** DOM operations used by page rendering, layered on the media IO fixture. */
export class PageNode extends Node {
  href = "";
  async decode() {}
  get childElementCount() { return this.children.length; }
  focus() {}
  override append(...nodes: Array<Node | string>) {
    super.append(...nodes.map((node) => {
      if (typeof node !== "string") return node;
      const text = new PageNode("#TEXT");
      text.textContent = node;
      return text;
    }));
  }
  querySelector(selector: string): Node | null {
    return descendants(this).find((node) => selector.split(",").some((part) => {
      const name = part.trim();
      return name.startsWith(".") ? node.className.split(" ").includes(name.slice(1)) : node.tagName === name.toUpperCase();
    })) ?? null;
  }
}

export function descendants(node: Node): Node[] {
  return node.children.flatMap((child) => [child, ...descendants(child)]);
}

export const textOf = (node: Node): string => node.textContent + node.children.map(textOf).join("");

export function applicationEnvironment() {
  const env = browserEnvironment();
  const streams: LibraryStream[] = [];
  class LibraryStream extends EventTarget {
    closed = false;
    constructor(readonly url: string) { super(); streams.push(this); }
    close() { this.closed = true; }
    fire(type: string) { this.dispatchEvent(new Event(type)); }
  }
  const document = Object.assign(new EventTarget(), {
    getElementById: env.node,
    querySelector: env.node,
    querySelectorAll: () => [],
    createElement: (tag: string) => tag === "video" ? new Video() : new PageNode(tag.toUpperCase()),
    createElementNS: (_ns: string, tag: string) => new PageNode(tag.toUpperCase()),
    body: new PageNode(), head: new PageNode(), documentElement: new PageNode(),
    activeElement: null, fullscreenElement: null, visibilityState: "visible",
  });
  const location = { hash: "#/movies" };
  const storage = new Map([["mediagram.profile", "viewer"]]);
  const scrolls: Array<[number, number]> = [];
  Object.assign(env.window, { scrollTo: (x: number, y: number) => { scrolls.push([x, y]); }, localStorage: {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => { storage.set(key, value); },
    removeItem: (key: string) => { storage.delete(key); },
  } });
  env.replace("document", document);
  env.replace("location", location);
  env.replace("matchMedia", () => ({ matches: true }));
  env.replace("EventSource", LibraryStream);
  env.replace("requestAnimationFrame", (run: (time: number) => void) => { queueMicrotask(() => run(0)); return 0; });
  // Session history as far as the page uses it: entries pushed without a
  // hash change, a replace that moves the hash without adding an entry —
  // exactly what a real `replaceState` does and a `hashchange` listener never
  // sees — and a back() that reports itself the way a browser does.
  const entries: unknown[] = [null];
  const history = {
    get length() { return entries.length; },
    get state() { return entries.at(-1) ?? null; },
    pushState(state: unknown) { entries.push(state); },
    replaceState(state: unknown, _title: string, url?: string) {
      entries[entries.length - 1] = state;
      const at = typeof url === "string" ? url.indexOf("#") : -1;
      if (at !== -1) location.hash = url!.slice(at);
    },
    back() {
      if (entries.length > 1) entries.pop();
      queueMicrotask(() => env.window.dispatchEvent(new Event("popstate")));
    },
  };
  env.replace("history", history);
  return {
    ...env, document, location, streams, scrolls, history,
    async navigate(hash: string) { location.hash = hash; env.window.dispatchEvent(new Event("hashchange")); await settle(); },
    async visibility(value: string) { document.visibilityState = value; document.dispatchEvent(new Event("visibilitychange")); await settle(); },
  };
}

/** The first node under `root` whose class list has `className`. */
export function byClass(root: Node, className: string): Node {
  const found = descendants(root).find((node) => node.className.split(" ").includes(className));
  if (!found) throw new Error(`Missing .${className}`);
  return found;
}

/** The button under `root` that says `label`. */
export function buttonNamed(root: Node, label: string): Node {
  const found = descendants(root).find((node) => node.tagName === "BUTTON" && node.textContent === label);
  if (!found) throw new Error(`Missing button: ${label}`);
  return found;
}

/** Types `pins` into the open PIN prompt's fields in order (the first again for any left over) and submits. */
export async function answerPin(root: Node, ...pins: string[]) {
  const prompt = byClass(root, "pin-prompt");
  descendants(prompt).filter((node) => node.tagName === "INPUT").forEach((field, at) => { field.value = pins[at] ?? pins[0] ?? ""; });
  descendants(prompt).find((node) => node.tagName === "FORM")!.fire("submit");
  await settle();
}
