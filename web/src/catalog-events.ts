/**
 * Telling open pages that the catalog changed, as it happens.
 *
 * The server hears a new index from Telegram in milliseconds; a page that
 * found out only when someone reloaded it threw that away. Server-sent events
 * rather than a socket: the traffic only ever goes one way, and an
 * `EventSource` reconnects by itself after a server restart or a laptop
 * waking up — which the page treats as a reason to read the catalog again,
 * since it may have missed an event while it was gone.
 *
 * The page never talks to Telegram. This is the whole of what it learns from
 * the server about the library changing — and about watch state another
 * device sent, which the server pulls but a visible page would otherwise
 * never ask for again.
 */

/**
 * How often an idle stream says something. Proxies and some browsers close a
 * connection that has been silent for a minute; a comment line costs nothing
 * and is ignored by `EventSource`.
 */
const HEARTBEAT_MS = 25_000;

const encoder = new TextEncoder();

export class CatalogEvents {
  private readonly listeners = new Set<ReadableStreamDefaultController<Uint8Array>>();
  private heartbeat: ReturnType<typeof setInterval> | null = null;

  /** A new page's stream. It ends when the page goes away. */
  subscribe(): ReadableStream<Uint8Array> {
    let mine: ReadableStreamDefaultController<Uint8Array> | null = null;
    return new ReadableStream<Uint8Array>({
      start: (controller) => {
        mine = controller;
        this.listeners.add(controller);
        // Sent at once so the page's `open` fires now, not at the first event.
        controller.enqueue(encoder.encode(": connected\n\n"));
        this.beat();
      },
      cancel: () => {
        if (mine) this.listeners.delete(mine);
        this.beat();
      },
    });
  }

  /** Tells every open page that the catalog it holds is out of date. */
  catalogChanged(publishedAt: number | null): void {
    this.send(`event: catalog\ndata: ${JSON.stringify({ publishedAt })}\n\n`);
  }

  /** Tells every open page that another device's positions or marks arrived. */
  stateChanged(): void {
    this.send("event: state\ndata: {}\n\n");
  }

  /** Open pages, for the status route and the tests. */
  get count(): number {
    return this.listeners.size;
  }

  /** Ends every stream, so a stopping server is not held open by them. */
  close(): void {
    for (const controller of this.listeners) {
      try {
        controller.close();
      } catch {
        // Already gone.
      }
    }
    this.listeners.clear();
    this.beat();
  }

  private send(text: string): void {
    const bytes = encoder.encode(text);
    for (const controller of this.listeners) {
      try {
        controller.enqueue(bytes);
      } catch {
        // A stream whose reader has gone; `cancel` normally removes it first.
        this.listeners.delete(controller);
      }
    }
    this.beat();
  }

  /** Runs the heartbeat only while someone is listening. */
  private beat(): void {
    if (this.listeners.size > 0 && this.heartbeat === null) {
      this.heartbeat = setInterval(() => this.send(": still here\n\n"), HEARTBEAT_MS);
    } else if (this.listeners.size === 0 && this.heartbeat !== null) {
      clearInterval(this.heartbeat);
      this.heartbeat = null;
    }
  }
}
