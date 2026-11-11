"""Renders a generated city (JSON) as the TV would show it: writes a standalone HTML page."""
import json, math, sys

city = json.load(open(sys.argv[1]))
out = sys.argv[2]
spots = city["spots"]
W, H = city["size"]
P = lambda f: spots[f - 1]
COL = {"taxi": "#8b95a1", "bus": "#0f766e", "ubahn": "#6d28d9", "ferry": "#1f2937"}

def smooth(pts):
    d = f"M{pts[0][0]} {pts[0][1]}"
    for i in range(1, len(pts)):
        a, b = pts[i - 1], pts[i]
        p0, p3 = pts[max(i - 2, 0)], pts[min(i + 1, len(pts) - 1)]
        d += f"C{a[0] + (b[0] - p0[0]) / 6:.1f} {a[1] + (b[1] - p0[1]) / 6:.1f},{b[0] - (p3[0] - a[0]) / 6:.1f} {b[1] - (p3[1] - a[1]) / 6:.1f},{b[0]} {b[1]}"
    return d

river = city["river"]
def river_y(x):
    for a, b in zip(river, river[1:]):
        if a[0] <= x <= b[0]:
            return a[1] + (b[1] - a[1]) * (x - a[0]) / ((b[0] - a[0]) or 1)
    return river[-1][1]

def link(a, b, bend):
    (ax, ay), (bx, by) = P(a), P(b)
    if not bend:
        return f"M{ax} {ay}L{bx} {by}"
    dx, dy = bx - ax, by - ay
    n = math.hypot(dx, dy)
    return f"M{ax} {ay}Q{(ax + bx) / 2 - dy / n * bend:.1f} {(ay + by) / 2 + dx / n * bend:.1f} {bx} {by}"

ring = {}
for kind in ("ferry", "bus", "ubahn"):
    for a, b in city[kind]:
        ring[a] = ring[b] = kind
svg = [f'<path d="{smooth(river)}" fill="none" stroke="#c9dfeb" stroke-width="30" stroke-linecap="round"/>']
for a, b in city["ferry"]:
    xs = sorted((P(a)[0], P(b)[0]))
    lane = [(x, river_y(x)) for x in range(xs[0], xs[1] + 1, 12)] + [(xs[1], river_y(xs[1]))]
    svg.append(f'<path d="M{"L".join(f"{x} {y:.1f}" for x, y in lane)}" fill="none" stroke="{COL["ferry"]}" stroke-width="3" stroke-dasharray="9 6" stroke-linecap="round" opacity=".8"/>')
    for f in (a, b):
        svg.append(f'<line x1="{P(f)[0]}" y1="{P(f)[1]}" x2="{P(f)[0]}" y2="{river_y(P(f)[0]):.1f}" stroke="{COL["ferry"]}" stroke-width="3" opacity=".8"/>')
for a, b in city["ubahn"]:
    svg.append(f'<path d="{link(a, b, 0)}" fill="none" stroke="{COL["ubahn"]}" stroke-width="4.5" stroke-dasharray="11 6" opacity=".75"/>')
for a, b in city["bus"]:
    svg.append(f'<path d="{link(a, b, 9)}" fill="none" stroke="{COL["bus"]}" stroke-width="3.5" opacity=".75"/>')
for a, b in city["taxi"]:
    svg.append(f'<path d="{link(a, b, 0)}" fill="none" stroke="{COL["taxi"]}" stroke-width="1.6"/>')
for i, (x, y) in enumerate(spots, 1):
    kind = ring.get(i, "taxi")
    width = {"taxi": 1.4, "ferry": 2.6, "bus": 2.6, "ubahn": 3.4}[kind]
    svg.append(f'<circle cx="{x}" cy="{y}" r="11.5" fill="#fff" stroke="{COL[kind]}" stroke-width="{width}"/>')
    svg.append(f'<text x="{x}" y="{y + 3.7}" text-anchor="middle" font-size="{10.5 if i > 99 else 11.5}" font-weight="800" fill="#17231d">{i}</text>')
pawns = [(17, "#f43f5e"), (63, "#10b981"), (88, "#f59e0b"), (131, "#0ea5e9"), (176, "#a855f7")]
for n, (f, c) in enumerate(pawns, 1):
    x, y = P(f)
    svg.append(f'<circle cx="{x + 11}" cy="{y - 11}" r="9" fill="{c}"/><text x="{x + 11}" y="{y - 7.4}" text-anchor="middle" font-size="10.5" font-weight="800" fill="#fff">{n}</text>')
page = f"""<!doctype html><meta charset="utf-8"><title>Flüchtig, big city</title>
<style>
html,body{{margin:0;background:#fbf7ee;font-family:"Inter","Helvetica Neue",Arial,sans-serif}}
.tv{{position:relative;width:960px;height:540px;overflow:hidden;zoom:var(--zoom,1)}}
svg{{display:block;width:100%;height:100%}}
.pill{{position:absolute;background:#0f5c45;color:#fbf7ee;border-radius:12px;box-shadow:0 3px 0 #0a4433;font-weight:800;display:flex;align-items:center;gap:8px;padding:0 14px;box-sizing:border-box}}
small{{font-weight:700;opacity:.75;font-size:11px}}
.dot{{width:22px;height:22px;border-radius:50%;display:inline-grid;place-items:center;font-size:11px}}
</style>
<div class="tv" style="--zoom:{sys.argv[3] if len(sys.argv) > 3 else 1}">
<svg viewBox="-10 -64 {W + 20} 668">{''.join(svg)}</svg>
<div class="pill" style="left:14px;top:12px;height:42px;font-size:20px">Flüchtig <small>Runde 1 / 24</small></div>
<div class="pill" style="left:356px;top:12px;height:38px;width:248px;background:#0e86b4;justify-content:center;font-size:18px">Cem fährt geheim …</div>
<div class="pill" style="right:14px;top:12px;height:48px;font-size:26px;color:#f7c531">2 <small style="color:#fbf7ee">Runden, dann<br>taucht Cem auf</small></div>
<div class="pill" style="left:14px;bottom:12px;height:56px;width:572px;font-size:12px">Cem flieht. Die Jäger suchen.</div>
<div class="pill" style="right:14px;bottom:12px;height:42px;font-size:13px">{''.join(f'<span class="dot" style="background:{c}">{n}</span>{f}' for n, (f, c) in enumerate(pawns, 1))}</div>
</div>"""
open(out, "w").write(page)
