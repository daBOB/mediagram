"""Mock: the big city with raised buildings and standing avatar figures, in two tilted views.

usage: render-city-raised.py city.json out.html oblique|iso [zoom]
  oblique: the plan stays as it is and heights go straight up (an illustrated city map)
  iso:     true isometric, the plan turned 45 degrees
"""
import json, math, random, sys

city = json.load(open(sys.argv[1]))
out, view = sys.argv[2], sys.argv[3]
zoom = sys.argv[4] if len(sys.argv) > 4 else 1
spots, (W, H) = city["spots"], city["size"]
rnd = random.Random(3)
ISO = view == "iso"
proj = (lambda x, y, z=0: ((x - y) * 0.866, (x + y) * 0.5 - z)) if ISO else (lambda x, y, z=0: (x, y - z))
depth = (lambda x, y: x + y) if ISO else (lambda x, y: y)
faces_us = (lambda nx, ny: nx + ny > 0.05) if ISO else (lambda nx, ny: ny > 0.05)
P = lambda f: spots[f - 1]
G = lambda f: proj(*P(f))                      # a field on the screen
pt = lambda p: f"{p[0]:.1f} {p[1]:.1f}"
poly = lambda pts: "M" + "L".join(pt(p) for p in pts) + "Z"

river = city["river"]
def river_y(x):
    for a, b in zip(river, river[1:]):
        if a[0] <= x <= b[0]:
            return a[1] + (b[1] - a[1]) * (x - a[0]) / ((b[0] - a[0]) or 1)
    return river[-1][1] if x > river[-1][0] else river[0][1]
def smooth(pts):
    d = f"M{pt(pts[0])}"
    for i in range(1, len(pts)):
        a, b, p0, p3 = pts[i - 1], pts[i], pts[max(i - 2, 0)], pts[min(i + 1, len(pts) - 1)]
        d += f"C{a[0] + (b[0] - p0[0]) / 6:.1f} {a[1] + (b[1] - p0[1]) / 6:.1f},{b[0] - (p3[0] - a[0]) / 6:.1f} {b[1] - (p3[1] - a[1]) / 6:.1f},{pt(b)}"
    return d
area = lambda p: sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(p, p[1:] + p[:1])) / 2
centroid = lambda p: (sum(x for x, _ in p) / len(p), sum(y for _, y in p) / len(p))
def inside(q, p):
    hit = False
    for (ax, ay), (bx, by) in zip(p, p[1:] + p[:1]):
        if (ay > q[1]) != (by > q[1]) and q[0] < ax + (bx - ax) * (q[1] - ay) / (by - ay):
            hit = not hit
    return hit
def normals(p):
    """Each side's unit normal pointing out of the polygon."""
    raw = []
    for a, b in zip(p, p[1:] + p[:1]):
        m = math.hypot(b[0] - a[0], b[1] - a[1]) or 1
        raw.append((-(b[1] - a[1]) / m, (b[0] - a[0]) / m))
    a, b = p[0], p[1]
    sign = -1 if inside(((a[0] + b[0]) / 2 + raw[0][0] * 1.5, (a[1] + b[1]) / 2 + raw[0][1] * 1.5), p) else 1
    return [(nx * sign, ny * sign) for nx, ny in raw]
def inset(p, d):
    ns = normals(p)
    res = []
    for i, v in enumerate(p):
        n1, n2 = ns[i - 1], ns[i]
        den = 1 + n1[0] * n2[0] + n1[1] * n2[1]
        ox, oy = (-(n1[0] + n2[0]) / den * d, -(n1[1] + n2[1]) / den * d) if den > 0.25 else (-n1[0] * d, -n1[1] * d)
        m = math.hypot(ox, oy)
        if m > 2.6 * d:
            ox, oy = ox / m * 2.6 * d, oy / m * 2.6 * d
        res.append((v[0] + ox, v[1] + oy))
    turned = any((res[(i + 1) % len(p)][0] - res[i][0]) * (p[(i + 1) % len(p)][0] - p[i][0]) + (res[(i + 1) % len(p)][1] - res[i][1]) * (p[(i + 1) % len(p)][1] - p[i][1]) <= 0 for i in range(len(p)))
    return None if turned or abs(area(res)) < 220 else res

# ---- blocks: the faces of the street net
nbrs = {}
for a, b in city["taxi"]:
    nbrs.setdefault(a, []).append(b); nbrs.setdefault(b, []).append(a)
for v in nbrs:
    nbrs[v].sort(key=lambda w: math.atan2(P(w)[1] - P(v)[1], P(w)[0] - P(v)[0]))
seen, faces = set(), []
for u in nbrs:
    for v in nbrs[u]:
        face, a, b = [], u, v
        while (a, b) not in seen:
            seen.add((a, b)); face.append(tuple(P(a)))
            ring = nbrs[b]
            a, b = b, ring[(ring.index(a) - 1) % len(ring)]
        if len(face) >= 3:
            faces.append(face)
way = 1 if sum(1 for f in faces if area(f) > 0) * 2 > len(faces) else -1
blocks = [(f, inset(f, 4.6)) for f in faces if area(f) * way > 0]
blocks = [(f, s) for f, s in blocks if s]
wet = lambda f: any(abs(y - river_y(x)) < 26 for x, y in f) or abs(centroid(f)[1] - river_y(centroid(f)[0])) < 30
dry = sorted((b for b in blocks if not wet(b[0])), key=lambda b: -abs(area(b[0])))
parks = set(id(b) for b in rnd.sample(dry[:40], 11))
middle = lambda c: 1 - min(1, math.hypot((c[0] - W / 2) / (W / 2), (c[1] - H / 2) / (H / 2)))   # 1 in the centre, 0 at the edge

svg = []
add = svg.append
corners = [proj(0, 0), proj(W, 0), proj(W, H), proj(0, H)]
x0, x1 = min(c[0] for c in corners), max(c[0] for c in corners)
y0, y1 = min(c[1] for c in corners), max(c[1] for c in corners)
LAND, TABLE = "#f3ecdc", "#0f5a43"
if ISO:   # the city is a slab on the table
    edge = [proj(-14, -14), proj(W + 14, -14), proj(W + 14, H + 14), proj(-14, H + 14)]
    add(f'<path d="{poly([(x, y + 12) for x, y in edge])}" fill="#b9ab8c"/><path d="{poly(edge)}" fill="{LAND}"/>')
    add(f'<clipPath id="slab"><path d="{poly(edge)}"/></clipPath>')
ground = ' clip-path="url(#slab)"' if ISO else ""

# ---- the ground: parks, water, streets
park_shapes = [b[1] for b in blocks if id(b) in parks]
for s in park_shapes:
    add(f'<path d="{poly([proj(*p) for p in s])}" fill="#bfd9a8" stroke="#bfd9a8" stroke-width="3" stroke-linejoin="round"/>')
rd = smooth([proj(*p) for p in river])
add(f'<g{ground}><path d="{rd}" fill="none" stroke="#8dbbd6" stroke-width="{30 if ISO else 34}" stroke-linecap="round"/><path d="{rd}" fill="none" stroke="#a9cfe4" stroke-width="{26 if ISO else 30}" stroke-linecap="round"/></g>')
for a, b in city["ferry"]:
    xs = sorted((P(a)[0], P(b)[0]))
    lane = [proj(x, river_y(x)) for x in range(xs[0], xs[1] + 1, 12)] + [proj(xs[1], river_y(xs[1]))]
    add(f'<path d="M{"L".join(pt(p) for p in lane)}" fill="none" stroke="#1f2937" stroke-width="3" stroke-dasharray="9 6" stroke-linecap="round" opacity=".85"/>')
    for f in (a, b):
        add(f'<path d="M{pt(G(f))}L{pt(proj(P(f)[0], river_y(P(f)[0])))}" stroke="#1f2937" stroke-width="3" opacity=".85"/>')
streets = "".join(f"M{pt(G(a))}L{pt(G(b))}" for a, b in city["taxi"])
add(f'<path d="{streets}" fill="none" stroke="#cbbfa5" stroke-width="7.6" stroke-linecap="round"/><path d="{streets}" fill="none" stroke="#fffdf8" stroke-width="5.2" stroke-linecap="round"/>')

# ---- buildings, far to near: the walls that face us, then the roof
ROOFS = ["#e9dfca", "#ece3d0", "#e4dac6", "#e6dcc8", "#ddb9a0", "#d8b29a", "#e9dfca", "#e2d8c4"]
quays = {id(b) for b in blocks if wet(b[0])}   # blocks the river runs through stay open ground: no house stands in the water
things = []
for b in blocks:
    f, s = b
    if id(b) in parks:
        for _ in range(max(3, int(abs(area(s)) / 240))):
            for _try in range(12):
                x, y = rnd.uniform(min(a for a, _ in s), max(a for a, _ in s)), rnd.uniform(min(c for _, c in s), max(c for _, c in s))
                if inside((x, y), s) and all(math.hypot(x - a, y - c) > 5 for a, c in s):
                    gx, gy = proj(x, y)
                    tx, ty = proj(x, y, 6)
                    r = rnd.uniform(3.0, 4.4)
                    things.append((depth(x, y), f'<ellipse cx="{gx + 1.5:.1f}" cy="{gy + 0.5:.1f}" rx="{r:.1f}" ry="{r * 0.5:.1f}" fill="#9cbf88"/><circle cx="{tx:.1f}" cy="{ty:.1f}" r="{r:.1f}" fill="#7fae6c"/><circle cx="{tx - 1:.1f}" cy="{ty - 1:.1f}" r="{r * 0.62:.1f}" fill="#95c082"/>'))
                    break
        continue
    if id(b) in quays:
        continue
    h = (5 + 9 * middle(centroid(s)) + rnd.uniform(0, 5)) * (2.2 if rnd.random() < 0.05 else 1)
    roof = rnd.choice(ROOFS)
    parts = []
    # a block is a ring of houses round a courtyard, where it is big enough for one
    yard = inset(s, 11) if abs(area(s)) > 1700 else None
    for (a, c), (nx, ny) in zip(zip(s, s[1:] + s[:1]), normals(s)):
        if faces_us(nx, ny):
            shade = "#c4b89c" if (nx > 0.35 if not ISO else nx > ny) else "#d6cbb1"
            parts.append(f'<path d="{poly([proj(*a), proj(*c), proj(*c, h), proj(*a, h)])}" fill="{shade}" stroke="{shade}" stroke-width=".6" stroke-linejoin="round"/>')
    top = poly([proj(*p, h) for p in s])
    if yard:
        # through the hole in the roof: the yard's floor, and the inner walls that look at us
        parts.append(f'<path d="{poly([proj(*p) for p in yard])}" fill="{"#cfe0b8" if rnd.random() < 0.45 else "#e8e0cf"}"/>')
        for (a, c), (nx, ny) in zip(zip(yard, yard[1:] + yard[:1]), normals(yard)):
            if not faces_us(nx, ny):
                parts.append(f'<path d="{poly([proj(*a), proj(*c), proj(*c, h), proj(*a, h)])}" fill="#cabea3" stroke="#cabea3" stroke-width=".5"/>')
        top += poly([proj(*p, h) for p in yard])
    parts.append(f'<path d="{top}" fill="{roof}" fill-rule="evenodd" stroke="#b9ad92" stroke-width=".7" stroke-linejoin="round"/>')
    things.append((max(depth(*p) for p in s), "".join(parts)))
for _, markup in sorted(things, key=lambda t: t[0]):
    add(markup)

# ---- the lines ride above the roofs, as on a map
def link(a, b, bow):
    (ax, ay), (bx, by) = G(a), G(b)
    return f"M{ax:.1f} {ay:.1f}Q{(ax + bx) / 2 - (by - ay) * bow:.1f} {(ay + by) / 2 + (bx - ax) * bow:.1f} {bx:.1f} {by:.1f}"
add(f'<path d="{"".join(link(a, b, 0.09) for a, b in city["ubahn"])}" fill="none" stroke="#6d28d9" stroke-width="4.5" stroke-dasharray="11 6" opacity=".8"/>')
add(f'<path d="{"".join(link(a, b, 0.09) for a, b in city["bus"])}" fill="none" stroke="#0f766e" stroke-width="3.4" opacity=".85"/>')

# ---- fields: upright signs, sized by the closest pair on the screen
close = min(math.hypot(G(a)[0] - G(b)[0], G(a)[1] - G(b)[1]) for a in range(1, len(spots) + 1) for b in range(a + 1, len(spots) + 1))
R = min(11.5, max(8.5, close * 0.42))
ring = {}
for kind, color in (("ferry", "#1f2937"), ("bus", "#0f766e"), ("ubahn", "#6d28d9")):
    for a, b in city[kind]:
        ring[a] = ring[b] = (color, 3.4 if kind == "ubahn" else 2.8)
SEEN_AT, SEEN_ROUND = 100, 3
pawns = [(31, "#f43f5e", "fox"), (63, "#10b981", "bear"), (88, "#f59e0b", "panda"), (131, "#10b981", "bear"), (176, "#f59e0b", "panda")]
reach = {SEEN_AT}
for _ in range(2):
    reach |= {n for f in reach for n in nbrs[f]}
for f in sorted(reach - {p[0] for p in pawns} - {SEEN_AT})[:11]:
    add(f'<circle cx="{G(f)[0]:.1f}" cy="{G(f)[1]:.1f}" r="{R * 1.45:.1f}" fill="none" stroke="#f2862e" stroke-width="2.4" stroke-dasharray="4 3"/>')
for i in sorted(range(1, len(spots) + 1), key=lambda i: depth(*P(i))):
    x, y = G(i)
    color, width = ring.get(i, ("#8b95a1", 1.4))
    add(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{R:.1f}" fill="#fff" stroke="{color}" stroke-width="{width}"/><text x="{x:.1f}" y="{y + R * 0.33:.1f}" text-anchor="middle" font-size="{(R if i < 100 else R * 0.9):.1f}" font-weight="800" fill="#17231d">{i}</text>')

# ---- the pieces: avatar figures standing beside their field
AV = "file:///home/andre/Workspace/spieltisch/mediagram-fluechtig/crates/mediagram-game/client/public/avatars"
def figure(f, color, who, badge, opacity=1, badge_fill="#17231d"):
    x, y = G(f)
    h = R * 4.3
    fx, fy = x + R * 2.0, y + R * 0.55           # where the feet stand: beside the field, so its number stays readable
    wide = len(badge) > 1
    return (f'<g opacity="{opacity}"><ellipse cx="{fx:.1f}" cy="{fy:.1f}" rx="{R * 1.05:.1f}" ry="{R * 0.5:.1f}" fill="{color}" stroke="#fff" stroke-width="1.4"/>'
            f'<image href="{AV}/{who}-figure.webp" x="{fx - h / 3:.1f}" y="{fy - h + R * 0.25:.1f}" width="{h * 2 / 3:.1f}" height="{h:.1f}"/>'
            f'<circle cx="{fx + R:.1f}" cy="{fy + R * 0.1:.1f}" r="{R * (0.66 if wide else 0.56):.1f}" fill="{badge_fill}" stroke="#fff" stroke-width="1.2"/>'
            f'<text x="{fx + R:.1f}" y="{fy + R * 0.1 + R * 0.27:.1f}" text-anchor="middle" font-size="{R * (0.66 if wide else 0.78):.1f}" font-weight="800" fill="#fff">{badge}</text></g>')
standing = [(depth(*P(SEEN_AT)), figure(SEEN_AT, "#9aa3ad", "ghost", f"R{SEEN_ROUND}", 0.6, "#b95f17"))]
standing += [(depth(*P(f)), figure(f, c, who, str(n))) for n, (f, c, who) in enumerate(pawns, 1)]
for _, markup in sorted(standing, key=lambda t: t[0]):
    add(markup)

pad = 10
vb = f"{x0 - pad:.0f} {y0 - 64:.0f} {x1 - x0 + 2 * pad:.0f} {y1 - y0 + 128:.0f}"
TICKET = {"taxi": "#8b95a1", "bus": "#0f766e", "ubahn": "#6d28d9", "schatten": "#1f2937", "doppel": "#b95f17"}
spent = ["taxi", "bus", "taxi", "schatten"]
log = "".join(f'<i class="slot{" reveal" if r in (3, 8, 13, 18, 24) else ""}{" now" if r == 5 else ""}" style="background:{TICKET[spent[r - 1]] if r <= len(spent) else "#0a4433"}"></i>' for r in range(1, 25))
chips = "".join(f'<b class="tk" style="background:{TICKET[k]}">{n}</b>' for k, n in (("taxi", 6), ("bus", 4), ("ubahn", 3), ("schatten", 4), ("doppel", 2)))
page = f"""<!doctype html><meta charset="utf-8"><title>Flüchtig, raised city ({view})</title>
<style>
:root{{--display:"Bricolage Grotesque","Arial Rounded MT Bold","Nunito","Helvetica Neue",Arial,sans-serif}}
html,body{{margin:0;background:{TABLE if ISO else LAND};font-family:"Atkinson Hyperlegible","Inter","Helvetica Neue",Arial,sans-serif}}
.tv{{position:relative;width:960px;height:540px;overflow:hidden;zoom:{zoom};background:{TABLE if ISO else LAND}}}
svg{{display:block;width:100%;height:100%}}
.pill{{position:absolute;background:#0f5a43;color:#fbf8f1;border-radius:12px;box-shadow:0 3px 0 #0a4433;font-weight:800;display:flex;align-items:center;gap:8px;padding:0 14px;box-sizing:border-box;font-family:var(--display);{"outline:1.5px solid #1c7258;" if ISO else ""}}}
small{{font-weight:700;opacity:.75;font-size:11px}}
.slot{{width:13px;height:13px;border-radius:50%;display:inline-block;box-sizing:border-box}}
.slot.reveal{{outline:1.6px dashed #f6c731;outline-offset:1px}}
.slot.now{{box-shadow:0 0 0 2px #fbf8f1}}
.tk{{min-width:18px;height:16px;border-radius:4px;display:inline-grid;place-items:center;font-size:10px;color:#fff}}
</style>
<div class="tv">
<svg viewBox="{vb}">{''.join(svg)}</svg>
<div class="pill" style="left:14px;top:12px;height:42px;font-size:20px">Flüchtig <small>Runde 5 / 24</small></div>
<div class="pill" style="left:356px;top:12px;height:38px;width:248px;background:#10b981;box-shadow:0 3px 0 #0b8a60;color:#0c2a22;justify-content:center;font-size:18px"><img src="{AV}/bear-head.webp" style="width:26px;height:26px;border-radius:50%">Ben zieht Jäger 2</div>
<div class="pill" style="right:14px;top:12px;height:48px;font-size:26px;color:#f6c731">3 <small style="color:#fbf8f1">Runden, dann<br>taucht Cem auf</small></div>
<div class="pill" style="left:14px;bottom:12px;height:56px;flex-direction:column;align-items:flex-start;justify-content:center;gap:5px;font-size:12px">
<span>Jäger 1 fährt mit dem Bus nach 31: Dort ist der Flüchtige nicht.</span>
<span style="display:flex;gap:4px;align-items:center"><img src="{AV}/ghost-head.webp" style="width:18px;height:18px;border-radius:50%;background:#9aa3ad">{log}<span style="width:10px"></span>{chips}</span>
</div>
</div>"""
open(out, "w").write(page)
print(view, "blocks", len(blocks), "field radius %.1f" % R, "closest fields on screen %.0f" % close, "view box", vb)
