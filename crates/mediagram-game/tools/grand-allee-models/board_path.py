"""Grand Allee's board as plain geometry (no Blender): the winding boulevard's
centre line, its 40 fields and the ten hotel plots, in metres (x east, y north,
the TV looks north). The rules know only field ids (`grand_allee/board.rs`);
this is the one map from them to the board, for the Blender board and the TV.

The road is a closed curve through CONTROL, clockwise from the Bank; its bends
make room for the hotels: outer hotels sit in the bays outside it, park hotels
in the lobes inside. All hotels stand at HOTEL_SCALE and face south, the TV.

python3 board_path.py <out-dir>  writes layout.json and lists every plot that
touches the road, the water, another plot or the board's edge (none, or fix CONTROL)."""

import bisect
import json
import math
import os
import sys

import numpy as np

import board_land as bl

FIELD = 6.0  # every field's length along the road
ROAD = 4.0
CLEAR = 1.0  # plot edge to road edge, at least
HOTEL_SCALE = 0.7
BOARD = (128.0, 96.0)  # 4:3, wide enough for the TV and deep enough for the bends
CONTROL = [(-45, -8), (-46, 8), (-38, 19), (-22, 21), (-5, 19), (-2, 11), (6, 11), (9, 19), (22, 21),
           (38, 19), (46, 8), (45, -8), (38, -19), (22, -21), (8, -19), (5, -13), (-5, -13), (-8, -19),
           (-22, -21), (-38, -19)]
# id, module, first and last frontage field, side, plot (width along x, depth along y) at full scale,
# and a shift along x off the frontage's centre: the park hotels step away from the bends in the
# middle, so they stand beside their own fields instead of being pushed deep into the park
PLOTS = [(0, "lagune", 4, 8, "outer", (20, 16), 0), (1, "kirschbluete", 9, 13, "outer", (20, 16), 0),
         (2, "fjordhus", 14, 19, "outer", (24, 16), 0), (3, "hacienda_sol", 24, 28, "outer", (20, 16), 0),
         (4, "riad_atlas", 29, 33, "outer", (20, 16), 0), (5, "alpenrose", 34, 39, "outer", (24, 16), 0),
         (6, "maharadscha", 4, 11, "park", (32, 14), -3.5), (7, "skyline", 12, 19, "park", (32, 14), 1.5),
         (8, "savanne", 24, 31, "park", (32, 14), 3.0), (9, "belle_epoque", 32, 39, "park", (32, 14), 0)]
LENGTHS = [FIELD] * 40
SEA_Y = 37.0  # the sea along the north edge, beyond the outer hotels on that side
# The river: in from the west, under the road by the Bank, through both park lobes and the
# Kurpark pond in the waist, under the road by the Rathaus, then north to the sea.
RIVER = [(-64, 1), (-50, 1.5), (-36, 1), (-20, 0.5), (-8, 0), (1.5, -0.5), (12, 0.5), (30, 0.5), (44, 0.7),
         (54, 6), (59, 22), (60, 37)]  # crossing the road under fields 1 and 21
RIVER_WIDTH = 3.0
POND = (1.5, -0.5, 4.0)  # x, y, radius


def nearest(points, line):
    """Smallest distance from any of `points` to any point of `line` (both dense lists of (x, y))."""
    a, b = np.asarray(points)[:, None, :], np.asarray(line)[None, :, :]
    return float(np.sqrt(((a - b) ** 2).sum(-1)).min())


def dense(line, step=0.5):
    """An open polyline with a point at least every `step` metres."""
    out = []
    for a, b in zip(line, line[1:]):
        n = max(1, int(math.dist(a, b) / step))
        out += [(a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n) for i in range(n)]
    return out + [line[-1]]


def catmull_rom(points, steps=80):
    """Dense closed centripetal Catmull-Rom curve through `points` (no cusps or loops)."""
    n, out = len(points), []
    for i in range(n):
        p = [points[(i + k) % n] for k in (-1, 0, 1, 2)]
        t = [0.0]
        for a, b in zip(p, p[1:]):
            t.append(t[-1] + math.dist(a, b) ** 0.5)
        for s in range(steps):
            u = t[1] + (t[2] - t[1]) * s / steps
            lerp = lambda a, b, ta, tb: tuple((tb - u) / (tb - ta) * x + (u - ta) / (tb - ta) * y for x, y in zip(a, b))
            a1, a2, a3 = (lerp(p[k], p[k + 1], t[k], t[k + 1]) for k in range(3))
            b1, b2 = lerp(a1, a2, t[0], t[2]), lerp(a2, a3, t[1], t[3])
            out.append(lerp(b1, b2, t[1], t[2]))
    return out


class Path:
    """The centre line, scaled so its length is exactly the 40 fields'."""

    def __init__(self, control=CONTROL):
        pts = catmull_rom(control)
        length = sum(math.dist(a, b) for a, b in zip(pts, pts[1:] + pts[:1]))
        k = sum(LENGTHS) / length  # the curve is affine-invariant: scaling the points scales its length
        self.pts = [(x * k, y * k) for x, y in pts]
        self.s = [0.0]
        for a, b in zip(self.pts, self.pts[1:] + self.pts[:1]):
            self.s.append(self.s[-1] + math.dist(a, b))

    def at(self, s):
        """(x, y, tx, ty) at arc length s: the point and the unit direction of travel."""
        s %= self.s[-1]
        i = bisect.bisect_right(self.s, s) - 1
        a, b = self.pts[i], self.pts[(i + 1) % len(self.pts)]
        f = (s - self.s[i]) / (self.s[i + 1] - self.s[i])
        d = math.dist(a, b)
        return a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, (b[0] - a[0]) / d, (b[1] - a[1]) / d

    def distance(self, points):
        return nearest(points, self.pts)


def field_starts():
    out = [0.0]
    for n in LENGTHS[:-1]:
        out.append(out[-1] + n)
    return out


def fields(path):
    """Each field: id, centre, heading and its four road corners (a quad along the curve)."""
    out = []
    for i, s0 in enumerate(field_starts()):
        x, y, tx, ty = path.at(s0 + LENGTHS[i] / 2)
        corners = []
        for s, side in ((s0, -1), (s0, 1), (s0 + LENGTHS[i], 1), (s0 + LENGTHS[i], -1)):
            px, py, qx, qy = path.at(s)
            corners.append((round(px + side * qy * ROAD / 2, 3), round(py - side * qx * ROAD / 2, 3)))
        outline = [(round(px, 3), round(py, 3)) for px, py in bl.field_outline(path, s0, LENGTHS[i], ROAD / 2)]
        out.append({"id": i, "x": round(x, 3), "y": round(y, 3), "heading": round(math.atan2(ty, tx), 4), "corners": corners,
                    "outline": outline})
    return out


def footprint(x, y, w, d, step=0.5):
    """Points along the edge of an axis-aligned w x d plot centred at (x, y)."""
    nx, ny = int(w / step) + 1, int(d / step) + 1
    xs = [x - w / 2 + w * i / (nx - 1) for i in range(nx)]
    ys = [y - d / 2 + d * j / (ny - 1) for j in range(ny)]
    return [(px, y - d / 2) for px in xs] + [(px, y + d / 2) for px in xs] + [(x - w / 2, py) for py in ys] + [(x + w / 2, py) for py in ys]


def plots(path, field_list, scale=HOTEL_SCALE):
    """Each plot centred in x over its frontage and moved north or south, away from the
    road, until it clears it; hotels face the TV, so they stay square to the board."""
    out = []
    for hid, mod, first, last, side, (w, d), shift in PLOTS:
        front = field_list[first:last + 1]
        x = sum(f["x"] for f in front) / len(front) + shift
        y = sum(f["y"] for f in front) / len(front)
        north = first < 20
        step = 0.25 if north == (side == "outer") else -0.25  # outer hotels away from the park, park hotels into it
        w, d, moved = w * scale, d * scale, 0.0
        while path.distance(footprint(x, y, w, d)) < ROAD / 2 + CLEAR and moved < d + ROAD:
            y, moved = y + step, moved + abs(step)
        reach = path.distance(footprint(x, y, w, d)) - ROAD / 2 - bl.KERB + 0.5  # the land reaches just past the hotel's nearest edge
        out.append({"hotel": hid, "name": mod, "x": round(x, 3), "y": round(y, 3), "first": first, "last": last, "side": side, "reach": round(reach, 3),
                    "w": w, "d": d, "rotation": 0.0, "scale": scale, "clear": moved < d + ROAD})
    return out


def problems(field_list, plot_list):
    """Fields pinched by too sharp a bend, plots on the road, in the water or off the board,
    and pairs of plots that overlap."""
    bad, river = [], dense(RIVER)
    for f in field_list:
        c = f["corners"]
        if min(math.dist(c[0], c[3]), math.dist(c[1], c[2])) < FIELD / 2:  # the inner edge of a bend
            bad.append((f"field {f['id']}", "sharp bend"))
    for i, a in enumerate(plot_list):
        edge = footprint(a["x"], a["y"], a["w"], a["d"])
        if not a["clear"]:
            bad.append((a["name"], "road"))
        if nearest(edge, river) < RIVER_WIDTH / 2 + CLEAR or nearest(edge, [POND[:2]]) < POND[2] + CLEAR:
            bad.append((a["name"], "river"))
        if a["y"] + a["d"] / 2 > SEA_Y - CLEAR:
            bad.append((a["name"], "sea"))
        if abs(a["x"]) + a["w"] / 2 > BOARD[0] / 2 or abs(a["y"]) + a["d"] / 2 > BOARD[1] / 2:
            bad.append((a["name"], "board edge"))
        for b in plot_list[i + 1:]:
            if abs(a["x"] - b["x"]) < (a["w"] + b["w"]) / 2 and abs(a["y"] - b["y"]) < (a["d"] + b["d"]) / 2:
                bad.append((a["name"], b["name"]))
    return bad


def layout():
    path = Path()
    field_list = fields(path)
    plot_list = plots(path, field_list)
    bl.attach_land(path, plot_list, field_starts(), LENGTHS, ROAD / 2)
    return {"board": BOARD, "road": ROAD, "fields": field_list, "plots": plot_list, "sea_y": SEA_Y,
            "river": RIVER, "river_width": RIVER_WIDTH, "pond": POND,
            "centre_line": [(round(x, 3), round(y, 3)) for x, y in path.pts[::8]]}, problems(field_list, plot_list) + land_checks(path, plot_list)


def land_checks(path, plot_list):
    river = dense(RIVER)
    water = lambda pts: min(nearest(pts, river) - RIVER_WIDTH / 2, nearest(pts, [POND[:2]]) - POND[2])
    return bl.land_problems(plot_list, path.distance, water, ROAD / 2)


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    data, bad = layout()
    json.dump(data, open(os.path.join(out, "layout.json"), "w"), indent=1)
    print("length", sum(LENGTHS), "problems", bad)
