"""Renders the big city as a designed map in one of three looks: writes a standalone HTML page.

usage: render-city-designs.py city.json out.html day|night|plan [zoom]
City blocks are the faces of the street net, drawn inset so the streets show between them.
"""
import json, math, random, sys

city = json.load(open(sys.argv[1]))
out, look = sys.argv[2], sys.argv[3]
zoom = sys.argv[4] if len(sys.argv) > 4 else 1
spots, (W, H) = city["spots"], city["size"]
P = lambda f: spots[f - 1]
rnd = random.Random(3)

LOOKS = {
    "day": dict(
        name="A · Stadtplan", land="#f3ecdc", blocks=["#e6dcc6", "#eadfcf", "#e2d9c8", "#ece3d0"], court="#efe7d6", park="#bfd9a8", tree="#8fbb7a",
        water="#a9cfe4", bank="#8dbbd6", road="#fffdf8", casing="#cbbf a5".replace(" ", ""), road_w=5.2, casing_w=7.6,
        taxi_ring="#8b95a1", bus="#0f766e", ubahn="#6d28d9", ferry="#1f2937", disc="#ffffff", num="#17231d",
        label="#6f6552", label_op=0.5, mark="#b3a688", lights=None, grid=None, stamp=None, bus_op=0.85, ubahn_op=0.8),
    "night": dict(
        name="B · Nachtjagd", land="#0c1422", blocks=["#152238", "#18263f", "#131f33", "#1a2a45"], court="#101a2c", park="#123329", tree="#1f5a45",
        water="#0d3a57", bank="#1b5c80", road="#33466a", casing="#0a101c", road_w=4.6, casing_w=7.0,
        taxi_ring="#9aa7bb", bus="#2dd4bf", ubahn="#a78bfa", ferry="#e2e8f0", disc="#f6f2e8", num="#0c1422",
        label="#8ea2c4", label_op=0.45, mark="#3b4f77", lights="#f7d774", grid=None, stamp=None, bus_op=0.9, ubahn_op=0.9),
    "plan": dict(
        name="C · Einsatzplan", land="#123a66", blocks=["#174675", "#174675", "#174675", "#174675"], court=None, park="#1a5a6a", tree="#3f8f8f",
        water="#0d2d52", bank="#7fb2e0", road="#dbe9f7", casing=None, road_w=1.8, casing_w=0,
        taxi_ring="#dbe9f7", bus="#5eead4", ubahn="#fde047", ferry="#ffffff", disc="#f4f8fc", num="#0d2d52",
        label="#cfe2f5", label_op=0.5, mark="#7fb2e0", lights=None, grid="#ffffff", stamp="#ff6b5e", bus_op=0.9, ubahn_op=0.95),
}
L = LOOKS[look]

# ---- geometry
river = city["river"]
def river_y(x):
    for a, b in zip(river, river[1:]):
        if a[0] <= x <= b[0]:
            return a[1] + (b[1] - a[1]) * (x - a[0]) / ((b[0] - a[0]) or 1)
    return river[-1][1] if x > river[-1][0] else river[0][1]

def smooth(pts):
    d = f"M{pts[0][0]} {pts[0][1]}"
    for i in range(1, len(pts)):
        a, b = pts[i - 1], pts[i]
        p0, p3 = pts[max(i - 2, 0)], pts[min(i + 1, len(pts) - 1)]
        d += f"C{a[0] + (b[0] - p0[0]) / 6:.1f} {a[1] + (b[1] - p0[1]) / 6:.1f},{b[0] - (p3[0] - a[0]) / 6:.1f} {b[1] - (p3[1] - a[1]) / 6:.1f},{b[0]} {b[1]}"
    return d

def area(poly):
    return sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(poly, poly[1:] + poly[:1])) / 2

def inside(pt, poly):
    x, y, hit = pt[0], pt[1], False
    for (ax, ay), (bx, by) in zip(poly, poly[1:] + poly[:1]):
        if (ay > y) != (by > y) and x < ax + (bx - ax) * (y - ay) / (by - ay):
            hit = not hit
    return hit

def inset(poly, d):
    """The polygon moved inward by `d` on every side; None if it is too small to survive."""
    n = len(poly)
    def normal(a, b, sign):
        ex, ey = b[0] - a[0], b[1] - a[1]
        m = math.hypot(ex, ey) or 1
        return (-ey / m * sign, ex / m * sign)
    a, b = poly[0], poly[1]
    nx, ny = normal(a, b, 1)
    sign = 1 if inside(((a[0] + b[0]) / 2 + nx * 1.5, (a[1] + b[1]) / 2 + ny * 1.5), poly) else -1
    res = []
    for i in range(n):
        p, v, q = poly[i - 1], poly[i], poly[(i + 1) % n]
        n1, n2 = normal(p, v, sign), normal(v, q, sign)
        den = 1 + n1[0] * n2[0] + n1[1] * n2[1]
        ox, oy = ((n1[0] + n2[0]) / den * d, (n1[1] + n2[1]) / den * d) if den > 0.25 else (n1[0] * d, n1[1] * d)
        m = math.hypot(ox, oy)
        if m > 2.6 * d:
            ox, oy = ox / m * 2.6 * d, oy / m * 2.6 * d
        res.append((v[0] + ox, v[1] + oy))
    return res if area(res) * area(poly) > 0 and abs(area(res)) > 220 else None

# ---- city blocks: the faces of the street net
nbrs = {}
for a, b in city["taxi"]:
    nbrs.setdefault(a, []).append(b); nbrs.setdefault(b, []).append(a)
for v in nbrs:
    nbrs[v].sort(key=lambda w: math.atan2(P(w)[1] - P(v)[1], P(w)[0] - P(v)[0]))
seen, faces = set(), []
for u in nbrs:
    for v in nbrs[u]:
        if (u, v) in seen:
            continue
        face, a, b = [], u, v
        while (a, b) not in seen:
            seen.add((a, b)); face.append(a)
            ring = nbrs[b]
            a, b = b, ring[(ring.index(a) - 1) % len(ring)]
        faces.append(face)
polys = [[tuple(P(f)) for f in face] for face in faces]
majority = 1 if sum(1 for p in polys if area(p) > 0) * 2 > len(polys) else -1
blocks = [p for p in polys if area(p) * majority > 0 and len(p) >= 3]
centroid = lambda p: (sum(x for x, _ in p) / len(p), sum(y for _, y in p) / len(p))

DISTRICTS = [(170, 105, "NORDEND"), (450, 95, "ALTSTADT"), (740, 110, "DOMVIERTEL"), (1010, 120, "VILLENBERG"),
             (150, 420, "HAFEN"), (430, 440, "MÜHLENVIERTEL"), (720, 430, "SÜDSTADT"), (1010, 430, "MESSE")]
def district(pt):
    north = pt[1] < river_y(pt[0])
    return min((i for i, d in enumerate(DISTRICTS) if (d[1] < river_y(d[0])) == north), key=lambda i: math.hypot(DISTRICTS[i][0] - pt[0], DISTRICTS[i][1] - pt[1]))

svg = []
add = svg.append
poly_d = lambda p: "M" + "L".join(f"{x:.1f} {y:.1f}" for x, y in p) + "Z"
add(f'<rect x="-300" y="-200" width="{W + 600}" height="{H + 400}" fill="{L["land"]}"/>')
if L["grid"]:
    add("".join(f'<path d="M{x} -100V{H + 100}" stroke="{L["grid"]}" stroke-width=".6" opacity=".09"/>' for x in range(-280, W + 300, 28)))
    add("".join(f'<path d="M-300 {y}H{W + 300}" stroke="{L["grid"]}" stroke-width=".6" opacity=".09"/>' for y in range(-84, H + 100, 28)))

big = sorted(blocks, key=lambda p: -abs(area(p)))
wet = lambda p: any(abs(y - river_y(x)) < 26 for x, y in p) or abs(centroid(p)[1] - river_y(centroid(p)[0])) < 30
dry_big = [p for p in big if not wet(p)]
parks = set(map(id, rnd.sample(dry_big[:40], 11)))
marks = [p for p in dry_big[:60] if id(p) not in parks]
for p in blocks:
    shape = inset(p, 4.6)
    if not shape:
        continue
    is_park = id(p) in parks
    fill = L["park"] if is_park else L["blocks"][district(centroid(p)) % 4]
    edge = f' stroke="{L["bank"]}" stroke-width=".7" stroke-opacity=".55"' if look == "plan" else f' stroke="{fill}" stroke-width="3" stroke-linejoin="round"'
    add(f'<path d="{poly_d(shape)}" fill="{fill}"{edge}/>')
    if is_park:
        for _ in range(max(3, int(abs(area(shape)) / 260))):
            for _try in range(12):
                x, y = rnd.uniform(min(a for a, _ in shape), max(a for a, _ in shape)), rnd.uniform(min(b for _, b in shape), max(b for _, b in shape))
                if inside((x, y), shape) and all(math.hypot(x - a, y - b) > 5 for a, b in shape):
                    add(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{rnd.uniform(2.2, 3.6):.1f}" fill="{L["tree"]}"/>')
                    break
    elif L["court"] and abs(area(shape)) > 1500:
        yard = inset(shape, 9)
        if yard:
            add(f'<path d="{poly_d(yard)}" fill="{L["court"]}" stroke="{L["court"]}" stroke-width="2" stroke-linejoin="round"/>')
    if L["lights"] and not is_park:
        for _ in range(int(abs(area(shape)) / 330)):
            x, y = rnd.uniform(min(a for a, _ in shape), max(a for a, _ in shape)), rnd.uniform(min(b for _, b in shape), max(b for _, b in shape))
            if inside((x, y), shape):
                add(f'<rect x="{x:.1f}" y="{y:.1f}" width="1.6" height="1.6" fill="{L["lights"]}" opacity="{rnd.uniform(0.25, 0.8):.2f}"/>')

# ---- landmarks on the larger blocks
GLYPHS = [
    "M-6 9V-1L0 -8L6 -1V9Z M0 -8V-14 M-3 -11.5H3",                               # church
    "M-9 9V-2L-4 2V-2L1 2V-2L6 2V9Z M6 9V-10H9V9",                               # works
    "M-8 9V1H8V9Z M-8 1A8 7 0 0 1 8 1 M-3 9V4H3V9",                              # hall
    "M0 9V-12 M-3 -9H3 M-5 9H5 M-2 -4H2",                                        # mast
    "M-9 9V-6H-6V-3H-3V-6H3V-3H6V-6H9V9Z",                                       # castle
    "M0 -9A9 9 0 1 0 0.01 -9Z M0 -9V9 M-9 0H9 M-6.4 -6.4L6.4 6.4 M-6.4 6.4L6.4 -6.4",  # wheel
    "M-10 6A10 6 0 1 0 10 6A10 6 0 1 0 -10 6Z M-6 6A6 3 0 1 0 6 6A6 3 0 1 0 -6 6Z",  # arena
]
rnd.shuffle(marks)
for glyph, p in zip(GLYPHS * 2, marks[:12]):
    x, y = centroid(p)
    if all(math.hypot(x - a, y - b) > 24 for a, b in spots):
        add(f'<path transform="translate({x:.1f} {y:.1f})" d="{glyph}" fill="none" stroke="{L["mark"]}" stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round"/>')

# ---- water
rd = smooth(river)
add(f'<path d="{rd}" fill="none" stroke="{L["bank"]}" stroke-width="34" stroke-linecap="round"/>')
add(f'<path d="{rd}" fill="none" stroke="{L["water"]}" stroke-width="{26 if look == "plan" else 30}" stroke-linecap="round"/>')
if look != "plan":
    for x in range(40, W, 86):
        y = river_y(x) + rnd.uniform(-7, 7)
        add(f'<path d="M{x} {y:.1f}q5 -3 10 0t10 0" fill="none" stroke="{L["bank"]}" stroke-width="1.2" opacity=".8"/>')
for a, b in city["ferry"]:
    xs = sorted((P(a)[0], P(b)[0]))
    lane = [(x, river_y(x)) for x in range(xs[0], xs[1] + 1, 12)] + [(xs[1], river_y(xs[1]))]
    add(f'<path d="M{"L".join(f"{x} {y:.1f}" for x, y in lane)}" fill="none" stroke="{L["ferry"]}" stroke-width="3" stroke-dasharray="9 6" stroke-linecap="round" opacity=".85"/>')
    for f in (a, b):
        add(f'<line x1="{P(f)[0]}" y1="{P(f)[1]}" x2="{P(f)[0]}" y2="{river_y(P(f)[0]):.1f}" stroke="{L["ferry"]}" stroke-width="3" opacity=".85"/>')
def clear(x):
    y = river_y(x)
    near = min(math.hypot(x - a, y - b) for a, b in spots)
    for kind in ("taxi", "bus", "ubahn"):
        for a, b in city[kind]:
            (ax, ay), (bx, by) = P(a), P(b)
            if min(ax, bx) - 4 <= x <= max(ax, bx) + 4 and (ay - river_y(ax)) * (by - river_y(bx)) < 0:
                t = (x - ax) / ((bx - ax) or 1)
                near = min(near, abs(ax + (bx - ax) * 0.5 - x) if abs(bx - ax) < 8 else abs(ay + (by - ay) * t - y) * 0.6 + 0)
    return near
piers = sorted(P(f)[0] for e in city["ferry"] for f in e)
fx = max(range(piers[0] + 30, piers[-1] - 30, 6), key=clear)
add(f'<text x="{fx}" y="{river_y(fx) + 4:.1f}" text-anchor="middle" font-size="11" font-weight="800" letter-spacing=".5" fill="{L["ferry"]}" stroke="{L["water"]}" stroke-width="6" paint-order="stroke">Fähre</text>')

names = []
for i, (_, _, name) in enumerate(DISTRICTS):
    half = len(name) * 6.2   # the name's half width: it goes where fields leave it the most room
    room = lambda c: min(max(abs(x - c[0]) - half, 0) + abs(y - c[1]) * 1.4 for x, y in spots)
    home = [centroid(p) for p in dry_big[:110] if district(centroid(p)) == i]
    if home:
        names.append((max(home, key=room), name))

# ---- streets, lines, fields
def link(a, b, bend):
    (ax, ay), (bx, by) = P(a), P(b)
    if not bend:
        return f"M{ax} {ay}L{bx} {by}"
    dx, dy = bx - ax, by - ay
    n = math.hypot(dx, dy)
    return f"M{ax} {ay}Q{(ax + bx) / 2 - dy / n * bend:.1f} {(ay + by) / 2 + dx / n * bend:.1f} {bx} {by}"
streets = "".join(link(a, b, 0) for a, b in city["taxi"])
if L["casing"]:
    add(f'<path d="{streets}" fill="none" stroke="{L["casing"]}" stroke-width="{L["casing_w"]}" stroke-linecap="round"/>')
add(f'<path d="{streets}" fill="none" stroke="{L["road"]}" stroke-width="{L["road_w"]}" stroke-linecap="round"/>')
for (x, y), name in names:   # district names: over the streets, under the lines and fields
    add(f'<text x="{x:.0f}" y="{y + 5:.0f}" text-anchor="middle" font-family="var(--display)" font-size="14" font-weight="800" letter-spacing="3.5" fill="{L["label"]}" stroke="{L["blocks"][0]}" stroke-width="4" paint-order="stroke" opacity=".85">{name}</text>')
for a, b in city["ubahn"]:
    add(f'<path d="{link(a, b, 0)}" fill="none" stroke="{L["ubahn"]}" stroke-width="4.5" stroke-dasharray="11 6" opacity="{L["ubahn_op"]}"/>')
for a, b in city["bus"]:
    add(f'<path d="{link(a, b, 9)}" fill="none" stroke="{L["bus"]}" stroke-width="3.4" opacity="{L["bus_op"]}"/>')
ring = {}
for kind in ("ferry", "bus", "ubahn"):
    for a, b in city[kind]:
        ring[a] = ring[b] = kind
for i, (x, y) in enumerate(spots, 1):
    kind = ring.get(i)
    color = L[kind] if kind else L["taxi_ring"]
    width = {None: 1.4, "ferry": 2.8, "bus": 2.8, "ubahn": 3.6}[kind]
    add(f'<circle cx="{x}" cy="{y}" r="11.5" fill="{L["disc"]}" stroke="{color}" stroke-width="{width}"/>')
    add(f'<text x="{x}" y="{y + 3.8}" text-anchor="middle" font-size="{10.5 if i > 99 else 11.5}" font-weight="800" fill="{L["num"]}">{i}</text>')
AVATARS = "file:///home/andre/Workspace/spieltisch/mediagram-fluechtig/crates/mediagram-game/client/public/avatars"
head = lambda who: f"{AVATARS}/{who}-head.webp"
# the hunters stand on the board as their players' avatar heads; Ben and Dana each move two pieces
pawns = [(31, "#f43f5e", "fox"), (63, "#10b981", "bear"), (88, "#f59e0b", "panda"), (131, "#10b981", "bear"), (176, "#f59e0b", "panda")]
RUNNER, SEEN_AT, SEEN_ROUND = "ghost", 100, 3
add('<clipPath id="round" clipPathUnits="objectBoundingBox"><circle cx=".5" cy=".5" r=".5"/></clipPath>')
def token(cx, cy, color, who, r=14, badge=None, opacity=1, badge_fill="#17231d"):
    out = f'<g opacity="{opacity}"><circle cx="{cx}" cy="{cy}" r="{r + 1.6}" fill="{L["disc"]}"/><circle cx="{cx}" cy="{cy}" r="{r}" fill="{color}"/>'
    out += f'<image href="{head(who)}" x="{cx - r * 0.84}" y="{cy - r * 0.84}" width="{r * 1.68}" height="{r * 1.68}" clip-path="url(#round)"/>'
    if badge is not None:
        out += f'<circle cx="{cx + r * 0.78}" cy="{cy + r * 0.72}" r="{6.4 if len(str(badge)) < 2 else 7.6}" fill="{badge_fill}" stroke="{L["disc"]}" stroke-width="1.2"/><text x="{cx + r * 0.78}" y="{cy + r * 0.72 + 3.1}" text-anchor="middle" font-size="{8.8 if len(str(badge)) < 2 else 7.6}" font-weight="800" fill="#fff">{badge}</text>'
    return out + "</g>"
# where the fugitive may be now: two streets from where he was seen
reach = {SEEN_AT}
for _ in range(2):
    reach |= {n for f in reach for n in nbrs[f]}
taken = {f for f, _, _ in pawns}
for f in sorted(reach - taken - {SEEN_AT})[:11]:
    add(f'<circle cx="{P(f)[0]}" cy="{P(f)[1]}" r="16.5" fill="none" stroke="#f2862e" stroke-width="2.4" stroke-dasharray="4 3"/>')
sx, sy = P(SEEN_AT)
# where he was last seen: his head, faded, with the round it was
add(token(sx + 13, sy - 17, "#9aa3ad", RUNNER, opacity=0.62, badge=f"R{SEEN_ROUND}", badge_fill="#b95f17"))
for n, (f, c, who) in enumerate(pawns, 1):
    x, y = P(f)
    add(token(x + 13, y - 17, c, who, badge=n))

TICKET = {"taxi": "#8b95a1", "bus": "#0f766e", "ubahn": "#6d28d9", "schatten": "#1f2937", "doppel": "#b95f17"}
spent = ["taxi", "bus", "taxi", "schatten"]
log = "".join(f'<i class="slot{" reveal" if r in (3, 8, 13, 18, 24) else ""}{" now" if r == 5 else ""}" style="background:{TICKET[spent[r - 1]] if r <= len(spent) else "#0a4433"}"></i>' for r in range(1, 25))
chips = "".join(f'<b class="tk" style="background:{TICKET[k]}">{n}</b>' for k, n in (("taxi", 6), ("bus", 4), ("ubahn", 3), ("schatten", 4), ("doppel", 2)))
page = f"""<!doctype html><meta charset="utf-8"><title>Flüchtig, {L["name"]}</title>
<style>
:root{{--display:"Bricolage Grotesque","Arial Rounded MT Bold","Nunito","Helvetica Neue",Arial,sans-serif}}
html,body{{margin:0;background:{L["land"]};font-family:"Atkinson Hyperlegible","Inter","Helvetica Neue",Arial,sans-serif}}
.tv{{position:relative;width:960px;height:540px;overflow:hidden;zoom:{zoom}}}
svg{{display:block;width:100%;height:100%}}
.pill{{position:absolute;background:#0f5a43;color:#fbf8f1;border-radius:12px;box-shadow:0 3px 0 #0a4433;font-weight:800;display:flex;align-items:center;gap:8px;padding:0 14px;box-sizing:border-box;font-family:var(--display)}}
small{{font-weight:700;opacity:.75;font-size:11px}}
.dot{{width:22px;height:22px;border-radius:50%;display:inline-grid;place-items:center;font-size:11px}}
.slot{{width:13px;height:13px;border-radius:50%;display:inline-block;box-sizing:border-box}}
.slot.reveal{{outline:1.6px dashed #f6c731;outline-offset:1px}}
.slot.now{{box-shadow:0 0 0 2px #fbf8f1}}
.tk{{min-width:18px;height:16px;border-radius:4px;display:inline-grid;place-items:center;font-size:10px;color:#fff}}
.pair{{display:inline-flex;align-items:center;gap:3px}}
.chip{{min-width:20px;height:22px;padding:0 4px;border-radius:11px;background:#fff;color:#17231d;box-shadow:inset 0 0 0 1.4px #8b95a1;display:inline-grid;place-items:center;font:800 11px "Atkinson Hyperlegible","Helvetica Neue",Arial,sans-serif}}
.tag{{position:absolute;left:581px;bottom:26px;transform:translateX(-50%);font:800 12px var(--display);color:{L["label"]};white-space:nowrap}}
</style>
<div class="tv">
<svg viewBox="-10 -64 {W + 20} 668">{''.join(svg)}</svg>
<div class="pill" style="left:14px;top:12px;height:42px;font-size:20px">Flüchtig <small>Runde 5 / 24</small></div>
<div class="pill" style="left:356px;top:12px;height:38px;width:248px;background:#10b981;box-shadow:0 3px 0 #0b8a60;color:#0c2a22;justify-content:center;font-size:18px"><img src="{head("bear")}" style="width:26px;height:26px;border-radius:50%">Ben zieht Jäger 2</div>
<div class="pill" style="right:14px;top:12px;height:48px;font-size:26px;color:#f6c731">3 <small style="color:#fbf8f1">Runden, dann<br>taucht Cem auf</small></div>
<div class="pill" style="left:14px;bottom:12px;height:56px;flex-direction:column;align-items:flex-start;justify-content:center;gap:5px;font-size:12px">
<span>Jäger 1 fährt mit dem Bus nach 31: Dort ist der Flüchtige nicht.</span>
<span style="display:flex;gap:4px;align-items:center"><img src="{head("ghost")}" style="width:18px;height:18px;border-radius:50%;background:#9aa3ad">{log}<span style="width:10px"></span>{chips}</span>
</div>
</div>"""
open(out, "w").write(page)
print(look, "blocks", len(blocks), "faces", len(faces))
