"""Curved fields and plot land for Grand Allee's board (plain geometry, no
Blender), used by `board_path.layout()`.

A field is drawn along the road's curve, not as a straight quad, so the road
reads as one smooth bend. A hotel's land reaches from the road's edge, along
its frontage fields, back to the far side of the hotel: its road side follows
every bend and touches the road, like a plot on a printed board (user,
2026-11-08: "Each hotel has a plot of land that adapts to the course of the
route", "make the players road curved")."""

import math

KERB = 0.3  # between the road's edge and a plot's land


def edge_point(path, s, side, offset):
    """The point `offset` metres to the right (side +1) or left (-1) of the direction of travel at arc length s."""
    x, y, tx, ty = path.at(s)
    return x + side * ty * offset, y - side * tx * offset


def field_outline(path, s0, length, half, steps=6):
    """A field as a polygon along the curve: its left edge forward, then its right edge back."""
    ss = [s0 + length * k / steps for k in range(steps + 1)]
    return [edge_point(path, s, -1, half) for s in ss] + [edge_point(path, s, 1, half) for s in reversed(ss)]


def plot_land(path, s0, s1, side, box, half, reach0, reach1, margin=1.0, step=1.0):
    """A plot's land: the road's edge on the plot's side from arc length s0 to s1, straight back
    from the road at both ends (`reach0`, `reach1` metres: a forecourt the width of the frontage),
    then the far corners of the hotel's footprint `box` = (x0, y0, x1, y1), `margin` metres round it."""
    n = max(2, round((s1 - s0) / step))
    edge = [edge_point(path, s0 + (s1 - s0) * k / n, side, half + KERB) for k in range(n + 1)]
    start, end = edge_point(path, s0, side, half + KERB + reach0), edge_point(path, s1, side, half + KERB + reach1)
    x0, y0, x1, y1 = box[0] - margin, box[1] - margin, box[2] + margin, box[3] + margin
    mid = sum(p[1] for p in edge) / len(edge)
    far = y1 if abs(y1 - mid) > abs(y0 - mid) else y0
    a, b = (x0, far), (x1, far)
    return edge + [end] + ([a, b] if math.dist(end, a) < math.dist(end, b) else [b, a]) + [start]


def attach_land(path, plot_list, starts, lengths, half):
    """Gives each plot its land. Neighbours on the same side share the straight line between
    them (the deeper of their two forecourts), so their lands meet without overlapping."""
    for p in plot_list:
        same = [q for q in plot_list if q is not p and q["side"] == p["side"]]
        r0 = max([p["reach"]] + [q["reach"] for q in same if q["last"] + 1 == p["first"]])
        r1 = max([p["reach"]] + [q["reach"] for q in same if q["first"] == p["last"] + 1])
        s0 = starts[p["first"]]
        box = (p["x"] - p["w"] / 2, p["y"] - p["d"] / 2, p["x"] + p["w"] / 2, p["y"] + p["d"] / 2)
        land = plot_land(path, s0, s0 + sum(lengths[p["first"]:p["last"] + 1]), 1 if p["side"] == "park" else -1, box, half, r0, r1)
        p["land"] = [(round(x, 3), round(y, 3)) for x, y in land]


def inside(pt, poly):
    """Whether `pt` lies inside the polygon (even-odd rule)."""
    x, y, hit = pt[0], pt[1], False
    for (ax, ay), (bx, by) in zip(poly, poly[1:] + poly[:1]):
        if (ay > y) != (by > y) and x < ax + (y - ay) * (bx - ax) / (by - ay):
            hit = not hit
    return hit


def _crosses(p, q, r, s):
    """Whether segments pq and rs cross at a point inside both (touching ends do not count)."""
    d = (q[0] - p[0]) * (s[1] - r[1]) - (q[1] - p[1]) * (s[0] - r[0])
    if abs(d) < 1e-9:
        return False
    t = ((r[0] - p[0]) * (s[1] - r[1]) - (r[1] - p[1]) * (s[0] - r[0])) / d
    u = ((r[0] - p[0]) * (q[1] - p[1]) - (r[1] - p[1]) * (q[0] - p[0])) / d
    return 1e-6 < t < 1 - 1e-6 and 1e-6 < u < 1 - 1e-6


def overlap(a, b):
    """Whether two polygons overlap; sharing a corner or an edge's end is allowed."""
    edges = lambda poly: list(zip(poly, poly[1:] + poly[:1]))
    if any(_crosses(p, q, r, s) for p, q in edges(a) for r, s in edges(b)):
        return True
    mid = lambda poly: (sum(x for x, _ in poly) / len(poly), sum(y for _, y in poly) / len(poly))
    return inside(mid(a), b) or inside(mid(b), a)


def outline_points(poly, step=0.5):
    """Points every `step` metres along a closed polygon's edges."""
    out = []
    for a, b in zip(poly, poly[1:] + poly[:1]):
        n = max(1, int(math.dist(a, b) / step))
        out += [(a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n) for k in range(n)]
    return out


def land_problems(plot_list, road_distance, water_distance, half):
    """Hotels standing off their land, land on the road or in the water, and lands that overlap."""
    bad = []
    for i, a in enumerate(plot_list):
        land, w, d = a["land"], a["w"] / 2 - 0.05, a["d"] / 2 - 0.05
        if not all(inside((a["x"] + sx * w, a["y"] + sy * d), land) for sx in (-1, 1) for sy in (-1, 1)):
            bad.append((a["name"], "hotel off its land"))
        pts = outline_points(land)
        if road_distance(pts) < half - 1e-3:
            bad.append((a["name"], "land on the road"))
        if water_distance(pts) < 0:
            bad.append((a["name"], "land in the water"))
        bad += [(a["name"], b["name"] + "'s land") for b in plot_list[i + 1:] if overlap(land, b["land"])]
    return bad
