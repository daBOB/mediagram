import { useEffect, useMemo, useRef, useState } from "react";
import type { ClientMsg } from "./protocol/ClientMsg";
import type { ServerMsg } from "./protocol/ServerMsg";

export type Status = "connecting" | "open" | "closed";

type Sample = { rtt: number; offset: number };

const SYNC_BURST = 6;
const SYNC_EVERY_MS = 5000;
const SAMPLES_KEPT = 12;

/**
 * WebSocket to the host that reconnects on its own and keeps an estimate of
 * the host's clock. `serverNow()` is what makes taps comparable across phones:
 * of recent ping samples, the one with the lowest round trip wins, because
 * its midpoint assumption is the least wrong.
 */
export class Connection {
  private ws: WebSocket | null = null;
  private samples: Sample[] = [];
  private offset = 0;
  private retryMs = 500;
  private timers: number[] = [];
  private closedByUs = false;

  constructor(
    private hello: () => ClientMsg | null,
    private onMessage: (msg: ServerMsg) => void,
    private onStatus: (s: Status) => void,
  ) {}

  // a phone that slept re-syncs the moment it is looked at again
  private onVisible = () => {
    if (document.visibilityState !== "visible" || this.ws?.readyState !== WebSocket.OPEN) return;
    this.samples = [];
    for (let i = 0; i < SYNC_BURST; i++) this.later(() => this.ping(), i * 120);
  };

  open() {
    this.closedByUs = false;
    document.addEventListener("visibilitychange", this.onVisible);
    const proto = location.protocol === "https:" ? "wss" : "ws";
    const ws = new WebSocket(`${proto}://${location.host}/ws`);
    this.ws = ws;
    this.onStatus("connecting");

    ws.onopen = () => {
      this.retryMs = 500;
      this.samples = []; // the old estimate may predate a sleep
      this.onStatus("open");
      this.rehello();
      for (let i = 0; i < SYNC_BURST; i++) this.later(() => this.ping(), i * 120);
      this.timers.push(window.setInterval(() => this.ping(), SYNC_EVERY_MS));
    };
    ws.onmessage = (ev) => {
      let msg: ServerMsg;
      try {
        msg = JSON.parse(ev.data as string);
      } catch {
        return;
      }
      if (msg.t === "pong") this.addSample(msg.t0, msg.now);
      else if (msg.t === "welcome") this.seed(msg.now);
      this.onMessage(msg);
    };
    ws.onclose = () => {
      this.clearTimers();
      this.ws = null;
      this.onStatus("closed");
      if (!this.closedByUs) {
        this.later(() => this.open(), this.retryMs);
        this.retryMs = Math.min(this.retryMs * 2, 5000);
      }
    };
  }

  close() {
    this.closedByUs = true;
    document.removeEventListener("visibilitychange", this.onVisible);
    this.clearTimers();
    this.ws?.close();
  }

  /** Re-sends hello, e.g. after the player typed a name; false when there was none to say. */
  rehello(): boolean {
    const hello = this.hello();
    if (hello) this.send(hello);
    return hello !== null;
  }

  send(msg: ClientMsg) {
    if (this.ws?.readyState === WebSocket.OPEN) this.ws.send(JSON.stringify(msg));
  }

  serverNow(): number {
    return performance.now() + this.offset;
  }

  private ping() {
    this.send({ t: "ping", t0: performance.now() });
  }

  private seed(serverNow: number) {
    if (this.samples.length === 0) this.offset = serverNow - performance.now();
  }

  private addSample(t0: number, serverNow: number) {
    const t1 = performance.now();
    const rtt = t1 - t0;
    this.samples.push({ rtt, offset: serverNow + rtt / 2 - t1 });
    if (this.samples.length > SAMPLES_KEPT) this.samples.shift();
    this.offset = this.samples.reduce((best, s) => (s.rtt < best.rtt ? s : best)).offset;
  }

  private later(fn: () => void, ms: number) {
    this.timers.push(window.setTimeout(fn, ms));
  }

  private clearTimers() {
    this.timers.forEach((t) => {
      clearTimeout(t);
      clearInterval(t);
    });
    this.timers = [];
  }
}

export function useConnection(hello: () => ClientMsg | null, onMessage: (msg: ServerMsg) => void) {
  const [status, setStatus] = useState<Status>("connecting");
  const helloRef = useRef(hello);
  const msgRef = useRef(onMessage);
  helloRef.current = hello;
  msgRef.current = onMessage;

  const conn = useMemo(
    () =>
      new Connection(
        () => helloRef.current(),
        (m) => msgRef.current(m),
        setStatus,
      ),
    [],
  );

  useEffect(() => {
    conn.open();
    return () => conn.close();
  }, [conn]);

  return { conn, status };
}

/** Server time, refreshed every animation frame while mounted. */
export function useServerNow(conn: Connection): number {
  const [now, setNow] = useState(() => conn.serverNow());
  useEffect(() => {
    let raf = 0;
    const loop = () => {
      setNow(conn.serverNow());
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, [conn]);
  return now;
}
