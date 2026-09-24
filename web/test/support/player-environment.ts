/** Browser boundaries for the real player: nodes, media events, time and HTTP. */
export class Node extends EventTarget {
  children: Node[] = [];
  parent: Node | null = null;
  dataset: Record<string, string> = {};
  attributes = new Map<string, string>();
  style: Record<string, any> = {
    setProperty(name: string, value: string) {
      this[name] = value;
    },
  };
  classes = new Set<string>();
  classList = {
    add: (name: string) => this.classes.add(name),
    remove: (name: string) => this.classes.delete(name),
    toggle: (name: string, on: boolean) =>
      on ? this.classes.add(name) : this.classes.delete(name),
  };
  hidden = false;
  disabled = false;
  open = false;
  value = "";
  max = "";
  id = "";
  className = "";
  title = "";
  clientWidth = 800;
  private text = "";
  constructor(readonly tagName = "DIV") {
    super();
  }
  get textContent() {
    return this.text;
  }
  set textContent(value: string) {
    this.text = value;
    this.children = [];
  }
  get selectedOptions() {
    return this.children.filter((child) => child.value === this.value);
  }
  append(...nodes: Node[]) {
    for (const node of nodes) {
      node.parent = this;
      this.children.push(node);
    }
  }
  prepend(...nodes: Node[]) {
    for (const node of nodes.reverse()) {
      node.parent = this;
      this.children.unshift(node);
    }
  }
  replaceChildren(...nodes: Node[]) {
    this.children = [];
    this.append(...nodes);
  }
  after(node: Node) {
    this.parent?.append(node);
  }
  remove() {
    if (this.parent) this.parent.children = this.parent.children.filter((node) => node !== this);
  }
  contains(node: Node): boolean {
    return this === node || this.children.some((child) => child.contains(node));
  }
  setAttribute(name: string, value: string) {
    this.attributes.set(name, value);
  }
  getAttribute(name: string) {
    return this.attributes.get(name) ?? null;
  }
  removeAttribute(name: string) {
    this.attributes.delete(name);
  }
  matches(_selector: string) {
    return false;
  }
  querySelectorAll(tag: string) {
    return this.children.filter((node) => node.tagName === tag.toUpperCase());
  }
  getBoundingClientRect() {
    return { width: this.clientWidth, left: 0 };
  }
  showModal() {
    this.open = true;
  }
  close() {
    this.open = false;
    this.fire("close");
  }
  fire(type: string) {
    this.dispatchEvent(new Event(type));
  }
}

export class Video extends Node {
  src = "";
  attachments: string[] = [];
  currentTime = 0;
  duration = Number.NaN;
  paused = true;
  ended = false;
  readyState = 0;
  playbackRate = 1;
  volume = 1;
  muted = false;
  bufferEnd = 0;
  buffered = { length: 1, start: () => 0, end: () => this.bufferEnd };
  textTracks = Object.assign([], { addEventListener() {} });
  constructor() {
    super("VIDEO");
    let source = "";
    Object.defineProperty(this, "src", {
      get: () => source,
      set: (value: string) => {
        source = value;
        if (value) this.attachments.push(value);
      },
    });
  }
  canPlayType() {
    return "";
  }
  pause() {
    if (!this.paused) {
      this.paused = true;
      this.fire("pause");
    }
  }
  async play() {
    this.paused = false;
    this.fire("play");
  }
  load() {
    this.currentTime = 0;
    this.readyState = 0;
    this.duration = Number.NaN;
    this.ended = false;
    this.bufferEnd = 0;
    this.fire("emptied");
  }
  override removeAttribute(name: string) {
    super.removeAttribute(name);
    if (name === "src") this.src = "";
  }
}

export function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}

export async function settle() {
  for (let i = 0; i < 30; i++) await Promise.resolve();
}

export function browserEnvironment() {
  const originals = new Map<string, PropertyDescriptor | undefined>();
  const replace = (name: string, value: unknown) => {
    if (!originals.has(name))
      originals.set(name, Object.getOwnPropertyDescriptor(globalThis, name));
    Object.defineProperty(globalThis, name, {
      configurable: true,
      writable: true,
      value,
    });
  };
  const video = new Video();
  const nodes = new Map<string, Node>([["video", video]]);
  const node = (id: string) => {
    if (!nodes.has(id)) {
      const created = new Node();
      created.id = id;
      nodes.set(id, created);
    }
    return nodes.get(id)!;
  };
  const document = Object.assign(new EventTarget(), {
    getElementById: node,
    querySelector: node,
    createElement: (tag: string) => (tag === "video" ? new Video() : new Node(tag.toUpperCase())),
    createElementNS: (_ns: string, tag: string) => new Node(tag.toUpperCase()),
    createTextNode: (text: string) => {
      const node = new Node("#text");
      node.textContent = text;
      return node;
    },
    head: new Node(),
    documentElement: new Node(),
    activeElement: null as Node | null,
    fullscreenElement: null,
  });
  const window = Object.assign(new EventTarget(), {
    localStorage: { getItem: () => null, setItem() {}, removeItem() {} },
  });
  let now = 0;
  let nextTimer = 0;
  const timers = new Map<number, { at: number; interval: number; run: () => void }>();
  const startTimer = (run: () => void, delay: number, interval: number) => {
    const id = ++nextTimer;
    timers.set(id, { at: now + delay, interval, run });
    return id;
  };
  const priorNow = Date.now;
  Date.now = () => now;
  replace("document", document);
  replace("window", window);
  replace("MediaSource", undefined);
  replace("ManagedMediaSource", undefined);
  replace("setInterval", (run: () => void, ms: number) => startTimer(run, ms, ms));
  replace("setTimeout", (run: () => void, ms: number) => startTimer(run, ms, 0));
  replace("clearInterval", (id: number) => timers.delete(id));
  replace("clearTimeout", (id: number) => timers.delete(id));
  const requests: { url: string; options: RequestInit | undefined }[] = [];
  let respond = async (_url: string, _options?: RequestInit): Promise<Response> =>
    new Response("", { status: 404 });
  replace("fetch", (url: string, options?: RequestInit) => {
    requests.push({ url, options });
    return respond(url, options);
  });
  return {
    document,
    video,
    node,
    window,
    replace,
    requests,
    timers,
    respondWith(fn: typeof respond) {
      respond = fn;
    },
    advance(ms: number) {
      const until = now + ms;
      while (true) {
        const due = [...timers]
          .filter(([, timer]) => timer.at <= until)
          .sort((a, b) => a[1].at - b[1].at)[0];
        if (!due) break;
        const [id, timer] = due;
        now = timer.at;
        if (timer.interval) timer.at += timer.interval;
        else timers.delete(id);
        timer.run();
      }
      now = until;
    },
    restore() {
      Date.now = priorNow;
      for (const [name, descriptor] of originals) {
        if (descriptor) Object.defineProperty(globalThis, name, descriptor);
        else Reflect.deleteProperty(globalThis, name);
      }
    },
  };
}
