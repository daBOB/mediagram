"""Grand Allee board: shared constants and 2D helpers in board space (metres;
x east, y north, z up; the TV looks north). The layout itself (road, fields,
plots, river, pond, sea) is `board_path.py`'s; nothing here copies it."""

import math
import os

import numpy as np

import board_path as bp

HERE = os.path.dirname(os.path.abspath(__file__))
# B Bank, R Rathaus, P Grundstück, S Baustelle, E Freier Eingang, G Gratis bauen, . Allee (checked against board.rs)
FIELD_KINDS = "B.GEP.S..P.SP.S.P.S.R.GEP.S..P.SP.S.P.S."
# Field tints of Bauherr's road (tools/bauherr-bake/page/ground.mjs); Gratis bauen is new and keeps board_layout's blue
KINDS = {"B": "#d9ad55", "R": "#8d9fbf", "P": "#8bb174", "S": "#d9824a", "E": "#b48ac0", "G": "#4ea8de"}
ICONS = {"B": "bank", "R": "rathaus", "P": "plot", "S": "crane", "E": "door", "G": "gift"}
TEXT = {"B": "Bank", "R": "Rathaus"}
INK = "#f6efe0"
KERB_W, KERB_H = 0.25, 0.12
EDGING_W, EDGING_H, EDGING_GAP = 0.2, 0.08, 0.02  # the gap keeps a hotel's own ground off the edging's inner wall
SEAM = 0.1  # half the dark band between two fields' setts
INLAY = 0.4  # the coloured inlay's inset from the setts' edge
ICON = 2.3  # icon size: still fits the inlay of a field that runs diagonally
WATER_Z, BANK, BEACH = -0.3, 0.7, 2.8  # water level, width of the river's sloping banks, depth of the beach
PATH_W = 1.4
BRIDGES = (1, 21)  # the two fields over the river
SCALE = bp.HOTEL_SCALE  # Bank and Rathaus are built at hotel size and shrunk like the hotels
# beside fields 0 and 20, outside the loop, clear of the river and the plots; local footprints at full size
BANK_AT, BANK_BOX = (-51.0, -6.5), (-6.0, -8.2, 6.0, 4.5)
RATHAUS_AT, RATHAUS_BOX = (48.5, 13.5), (-5.0, -4.25, 5.0, 4.25)
SUN = (0.866, 0.5)  # shadows fall this way (east-north-east), from build.light()'s sun
SUN_UP, TV_PITCH = math.radians(19), math.radians(55)


def area(poly):
    return 0.5 * sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(poly, poly[1:] + poly[:1]))


def offset(line, d, closed=False):
    """The polyline moved `d` to its left (negative: right), corners mitred."""
    n, out = len(line), []
    for i in range(n):
        ns = []
        for j in (i - 1, i):
            if not closed and not 0 <= j < n - 1:
                continue
            (ax, ay), (bx, by) = line[j % n], line[(j + 1) % n]
            L = math.hypot(bx - ax, by - ay)
            ns.append(((ay - by) / L, (bx - ax) / L))
        nx, ny = sum(v[0] for v in ns), sum(v[1] for v in ns)
        L = math.hypot(nx, ny)
        nx, ny = nx / L, ny / L
        k = d / (nx * ns[0][0] + ny * ns[0][1])
        out.append((line[i][0] + nx * k, line[i][1] + ny * k))
    return out


def inset(poly, d):
    """A closed polygon shrunk by `d` on every side."""
    return offset(poly, d if area(poly) > 0 else -d, closed=True)


def to_line(P, line, closed=False):
    """Distance from each point to a polyline."""
    P = np.asarray(P, float).reshape(-1, 2)
    pts = np.asarray(line, float)
    if closed:
        pts = np.vstack([pts, pts[:1]])
    d = np.full(len(P), np.inf)
    for a, b in zip(pts[:-1], pts[1:]):
        ab = b - a
        t = np.clip(((P - a) @ ab) / max(ab @ ab, 1e-12), 0, 1)
        d = np.minimum(d, np.linalg.norm(P - a - t[:, None] * ab, axis=1))
    return d


def world_box(at, box, margin=0.0):
    x0, y0, x1, y1 = box
    return (at[0] + x0 * SCALE - margin, at[1] + y0 * SCALE - margin, at[0] + x1 * SCALE + margin, at[1] + y1 * SCALE + margin)


class Layout:
    """board_path's layout plus the distance queries the board's builders share."""

    def __init__(self):
        self.data, bad = bp.layout()
        assert not bad, bad
        self.fields, self.plots = self.data["fields"], self.data["plots"]
        self.centre = bp.Path().pts[::4]
        # the road's curved edges, 6 points a field (a field's 7th is the next one's 1st): outside the loop (left of travel), inside
        self.outer = [q for f in self.fields for q in f["outline"][:6]]
        self.inner = [q for f in self.fields for q in f["outline"][:6:-1][:6]]
        self.buildings = [world_box(BANK_AT, BANK_BOX), world_box(RATHAUS_AT, RATHAUS_BOX)]
        rs = open(os.path.join(HERE, "..", "..", "src", "grand_allee", "board.rs")).read()
        assert f'b"{FIELD_KINDS}"' in rs, "field kinds differ from grand_allee/board.rs"

    def edge(self, line, a, b):
        """A road edge from the start of field a to the start of field b, round past field 39 if b <= a."""
        k, n = len(line) // len(self.fields), len(self.fields)
        return [line[i % len(line)] for i in range(a * k, (b if b > a else b + n) * k + 1)]

    def in_land(self, P, margin=0.0):
        """Points on a plot's land (board_path's `land`: from the road's edge back past the hotel), or within `margin` of it."""
        P = np.asarray(P, float).reshape(-1, 2)
        hit = np.zeros(len(P), bool)
        for p in self.plots:
            hit |= inside_poly(P, p["land"]) | (to_line(P, p["land"], closed=True) < margin)
        return hit

    def road(self, P):
        return to_line(P, self.centre, closed=True)

    def water(self, P):
        """Signed distance to the river's and pond's water edge (negative in the water)."""
        P = np.asarray(P, float).reshape(-1, 2)
        river = to_line(P, bp.RIVER) - bp.RIVER_WIDTH / 2
        return np.minimum(river, np.hypot(P[:, 0] - bp.POND[0], P[:, 1] - bp.POND[1]) - bp.POND[2])

    def plot_boxes(self, margin=0.0):
        """The hotels' footprints (what a hotel covers; its land reaches further, to the road)."""
        return [(p["x"] - p["w"] / 2 - margin, p["y"] - p["d"] / 2 - margin, p["x"] + p["w"] / 2 + margin,
                 p["y"] + p["d"] / 2 + margin) for p in self.plots]

    @staticmethod
    def in_boxes(P, boxes):
        P = np.asarray(P, float).reshape(-1, 2)
        hit = np.zeros(len(P), bool)
        for x0, y0, x1, y1 in boxes:
            hit |= (P[:, 0] > x0) & (P[:, 0] < x1) & (P[:, 1] > y0) & (P[:, 1] < y1)
        return hit

    def inside(self, P):
        """Inside the road's loop (the Kurpark)."""
        return inside_poly(P, self.centre)


def inside_poly(P, poly):
    """Points inside a polygon, by the even-odd rule."""
    P = np.asarray(P, float).reshape(-1, 2)
    pts = np.asarray(poly)
    a, b = pts, np.roll(pts, -1, axis=0)
    x, y = P[:, 0:1], P[:, 1:2]
    cross = ((a[:, 1] > y) != (b[:, 1] > y)) & (x < (b[:, 0] - a[:, 0]) * (y - a[:, 1]) / (b[:, 1] - a[:, 1] + 1e-12) + a[:, 0])
    return cross.sum(1) % 2 == 1
