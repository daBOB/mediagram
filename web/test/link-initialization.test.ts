import { afterEach, expect, test } from "bun:test";
import { decodesParam, loadLink } from "../public/lib/link.js";

const originals = new Map<string, PropertyDescriptor | undefined>();

function replace(name: string, value: unknown) {
  if (!originals.has(name)) originals.set(name, Object.getOwnPropertyDescriptor(globalThis, name));
  Object.defineProperty(globalThis, name, { configurable: true, writable: true, value });
}

afterEach(() => {
  for (const [name, descriptor] of originals) {
    if (descriptor) Object.defineProperty(globalThis, name, descriptor);
    else Reflect.deleteProperty(globalThis, name);
  }
  originals.clear();
});

function installBrowser(supported: boolean) {
  replace("document", {
    createElement: () => ({ canPlayType: () => supported ? "probably" : "" }),
  });
  replace("MediaSource", {
    isTypeSupported: () => supported,
  });
}

test("explicit startup probes a browser installed after module import even while offline", async () => {
  installBrowser(true);
  replace("fetch", async () => { throw new Error("offline"); });

  await loadLink();

  expect(decodesParam()).toBe("&vcodecs=hevc");
});

test("another startup refreshes capabilities when the browser no longer supports a codec", async () => {
  replace("fetch", async () => Response.json({ remote: false }));
  installBrowser(true);
  await loadLink();
  expect(decodesParam()).toBe("&vcodecs=hevc");

  installBrowser(false);
  await loadLink();

  expect(decodesParam()).toBe("");
});
