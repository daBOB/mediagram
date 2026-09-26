import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

const css = (name: string) => readFileSync(join(import.meta.dir, "../public/styles", name), "utf8");
const luminance = (hex: string) => {
  const [r, g, b] = [1, 3, 5].map((at) => parseInt(hex.slice(at, at + 2), 16) / 255)
    .map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4)) as [number, number, number];
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};
const contrast = (a: string, b: string) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x) as [number, number];
  return (hi + 0.05) / (lo + 0.05);
};

test("every accent reads at 4.5:1 or better on its theme's paper", () => {
  const theme = css("theme.css");
  const dark = theme.match(/--paper: (#[0-9a-f]{6})/)![1];
  const light = theme.match(/data-theme="light"\] \{[^}]*--paper: (#[0-9a-f]{6})/)![1];
  const rules = [...css("appearance.css").matchAll(/^(:root\[data-theme="light"\])?\[?[^{]*data-accent="(\w+)"\] \{ --accent: (#[0-9a-f]{6}); \}/gm)];
  expect(rules.length).toBe(12);
  for (const [, isLight, name, value] of rules) {
    const ratio = contrast(value!, (isLight ? light : dark)!);
    expect({ name, theme: isLight ? "light" : "dark", ok: ratio >= 4.5 }).toEqual({ name, theme: isLight ? "light" : "dark", ok: true });
  }
});
