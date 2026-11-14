#!/usr/bin/env python3
"""Builds Grenzfall's country outlines from Natural Earth's 1:50m countries.

Usage: build_country_shapes.py ne_50m_admin_0_countries.geojson out.json [sheet.svg]

Natural Earth is public domain (https://www.naturalearthdata.com). The input is
the v5.1.2 GeoJSON from github.com/nvkelso/natural-earth-vector. Each country is
drawn in its own Mercator view, the projection school maps use, so outlines look
the way people know them; overseas parts far from the home territory are dropped.
The optional sheet is every outline with its name, for checking them by eye.
"""

import json
import math
import sys

# The 193 UN members and the Vatican, by ISO code. Left out on purpose: places
# whose statehood is disputed (Taiwan, Kosovo, Palestine, Western Sahara), so the
# game night stays clear of politics.
DISPUTED_PARTS = {"Somaliland": "SO", "Northern Cyprus": "CY", "Aland": "FI"}
SOVEREIGN_TYPES = {"Sovereign country", "Country", "Sovereignty", "Disputed"}
NOT_COUNTRIES = {"TW", "XK", "IO", "FK", "JE", "GG", "IM", "AW", "CW", "GL", "MO", "HK", "SX"}

# Shorter or more usual German names than Natural Earth's NAME_DE.
NAMES = {
    "US": "USA",
    "GB": "Großbritannien",
    "CN": "China",
    "CY": "Zypern",
    "MD": "Moldau",
    "CD": "DR Kongo",
    "FM": "Mikronesien",
}

# Outlines nobody could tell apart: a few dots of atoll across the ocean, or a
# small state that is a blob of a dozen points in 1:50m data.
DUST = set(
    "KI MH TV NR MV PW FM MC VA SM AD LI SG BH MT MU SC KM ST TO WS "
    "BB DM GD KN AG LC VC".split()
)

# Shapes most players know at a glance, then those a geography-minded player
# knows; everything else is the hardest tier.
TIER1 = "IT CL JP IN AU BR US FR ES DE GB GR NO EG MX AR CA RU CN TR NZ ZA MG IS IE CU AT CH PT PL".split()
TIER2 = (
    "SE FI DK NL BE CZ SK HU HR RO BG UA BY LT LV EE RS SI BA AL MK ME MD LU CY "
    "VN TH KR KP ID PH SA IR IQ IL PK AF MN KZ LK MA DZ LY TN NG ET KE SO NA TZ CD "
    "PE CO VE BO PY UY EC JM PA MY NP BD MM SY JO YE OM"
).split()

CORE_SHARE = 0.80  # the biggest polygons holding this share of land are home territory
NEAR_SHARE = 0.25  # other land counts as near within this share of home's size ...
NEAR_MIN_DEG = 2.0  # ... or within this many degrees, whichever is more
BOX = 1000  # an outline's longer side, in SVG units
TOLERANCE = 1.2  # simplification error allowed, in SVG units
MIN_PART = 4.0  # land smaller than this many SVG units is dropped


def code_of(p):
    code = p["ISO_A2_EH"]
    return DISPUTED_PARTS.get(p["ADMIN"], code)


def polygons(geometry):
    if geometry["type"] == "Polygon":
        return [geometry["coordinates"]]
    return geometry["coordinates"]


def unwrap(polys):
    """Shifts western longitudes east when a country straddles the date line."""
    lons = [x for poly in polys for ring in poly for x, _ in ring]
    if max(lons) - min(lons) <= 180:
        return polys
    return [[[(x + 360 if x < 0 else x, y) for x, y in ring] for ring in poly] for poly in polys]


def ring_area(ring):
    return abs(sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(ring, ring[1:] + ring[:1]))) / 2


def bbox(points):
    xs = [x for x, _ in points]
    ys = [y for _, y in points]
    return min(xs), min(ys), max(xs), max(ys)


def home_parts(polys):
    """The polygons of the home territory and the land near it."""
    def mean_lat(poly):
        return math.radians(sum(y for _, y in poly[0]) / len(poly[0]))

    # Sizes are measured at each part's own latitude, distances at the largest part's.
    areas = [ring_area([(x * math.cos(mean_lat(p)), y) for x, y in p[0]]) for p in polys]
    order = sorted(range(len(polys)), key=lambda i: -areas[i])
    lat0 = mean_lat(polys[order[0]])
    flat = [[(x * math.cos(lat0), y) for x, y in p[0]] for p in polys]
    total, held, core = sum(areas), 0.0, []
    for i in order:
        core.append(i)
        held += areas[i]
        if held >= CORE_SHARE * total:
            break
    x0, y0, x1, y1 = bbox([pt for i in core for pt in flat[i]])
    reach = max(NEAR_SHARE * max(x1 - x0, y1 - y0), NEAR_MIN_DEG)
    kept = []
    for i, ring in enumerate(flat):
        cx = sum(x for x, _ in ring) / len(ring)
        cy = sum(y for _, y in ring) / len(ring)
        if x0 - reach <= cx <= x1 + reach and y0 - reach <= cy <= y1 + reach:
            kept.append(polys[i])
    return kept


def mercator(x, y):
    y = max(min(y, 85.0), -85.0)
    return math.radians(x), -math.log(math.tan(math.pi / 4 + math.radians(y) / 2))


def simplify(points, tol):
    """Douglas-Peucker on a closed ring, keeping its first point."""
    if len(points) < 4:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        (ax, ay), (bx, by) = points[a], points[b]
        dx, dy = bx - ax, by - ay
        norm = math.hypot(dx, dy)
        best, far = -1.0, -1
        for i in range(a + 1, b):
            px, py = points[i]
            d = abs(dy * (px - ax) - dx * (py - ay)) / norm if norm else math.hypot(px - ax, py - ay)
            if d > best:
                best, far = d, i
        if best > tol:
            keep[far] = True
            stack += [(a, far), (far, b)]
    return [p for p, k in zip(points, keep) if k]


def outline(polys):
    """The SVG path, width and height of a country's outer rings."""
    rings = [[mercator(x, y) for x, y in poly[0]] for poly in polys]
    x0, y0, x1, y1 = bbox([pt for r in rings for pt in r])
    scale = BOX / max(x1 - x0, y1 - y0)
    parts = []
    for ring in rings:
        pts = [((x - x0) * scale, (y - y0) * scale) for x, y in ring]
        rx0, ry0, rx1, ry1 = bbox(pts)
        if max(rx1 - rx0, ry1 - ry0) < MIN_PART:
            continue
        pts = simplify(pts, TOLERANCE)
        ints = []
        for x, y in pts:
            p = (round(x), round(y))
            if not ints or ints[-1] != p:
                ints.append(p)
        if len(ints) > 1 and ints[0] == ints[-1]:
            ints.pop()
        if len(ints) >= 3:
            parts.append("M" + " ".join(f"{x},{y}" for x, y in ints) + "Z")
    return "".join(parts), round((x1 - x0) * scale), round((y1 - y0) * scale)


def build(features):
    merged = {}
    for f in features:
        p = f["properties"]
        code = code_of(p)
        if p["TYPE"] not in SOVEREIGN_TYPES and p["ADMIN"] not in DISPUTED_PARTS:
            continue
        if code in NOT_COUNTRIES or code in DUST or code == "-99":
            continue
        entry = merged.setdefault(code, {"p": None, "polys": []})
        if p["ADMIN"] not in DISPUTED_PARTS:
            entry["p"] = p
        entry["polys"] += polygons(f["geometry"])
    countries = []
    for code, entry in sorted(merged.items()):
        p = entry["p"]
        if p is None:
            continue
        d, w, h = outline(home_parts(unwrap(entry["polys"])))
        tier = 1 if code in TIER1 else 2 if code in TIER2 else 3
        countries.append({
            "code": code,
            "name": NAMES.get(code, p["NAME_DE"]),
            "continent": p["CONTINENT"],
            "subregion": p["SUBREGION"],
            "tier": tier,
            "w": w,
            "h": h,
            "d": d,
        })
    return countries


def sheet(countries, path):
    cell, cols = 180, 12
    rows = math.ceil(len(countries) / cols)
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{cols * cell}" height="{rows * cell}">',
           '<rect width="100%" height="100%" fill="#fff"/>']
    for i, c in enumerate(countries):
        x, y = (i % cols) * cell, (i // cols) * cell
        s = (cell - 40) / BOX
        out.append(f'<g transform="translate({x + 20 + (BOX - c["w"]) * s / 2},{y + 8}) scale({s})">'
                   f'<path d="{c["d"]}" fill="#c0392b" fill-rule="evenodd"/></g>')
        out.append(f'<text x="{x + cell / 2}" y="{y + cell - 8}" font-size="13" font-family="sans-serif" '
                   f'text-anchor="middle">{c["name"]} ({c["tier"]})</text>')
    out.append("</svg>")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out))


def main():
    with open(sys.argv[1], encoding="utf-8") as fh:
        features = json.load(fh)["features"]
    countries = build(features)
    missing = sorted(set(TIER1 + TIER2) - {c["code"] for c in countries})
    assert not missing, f"tier codes without an outline: {missing}"
    with open(sys.argv[2], "w", encoding="utf-8") as fh:
        lines = (json.dumps(c, ensure_ascii=False) for c in countries)
        fh.write("[\n" + ",\n".join(lines) + "\n]\n")
    if len(sys.argv) > 3:
        sheet(countries, sys.argv[3])
    size = sum(len(c["d"]) for c in countries)
    print(f"{len(countries)} countries, {size // 1024} KB of path data")


if __name__ == "__main__":
    main()
