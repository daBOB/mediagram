"""Belle Epoque's own shapes, materials and layout constants
(the stages are built in `belle_epoque.py`)."""

import math
import materials as m
import parts as p


STOREY = 3.0

PLOT = (32, 14)

BAY = 2.0

ROTUNDA = (-12.75, 1.8, 2.6)  # centre x, y and radius of the corner pavilion

def _normal(g):
    sep = g.nt.nodes.new("ShaderNodeSeparateXYZ")
    g.nt.links.new(g.obj.node.outputs["Normal"], sep.inputs[0])
    return sep.outputs

def _angle(g, cx, cy, n):
    """Bays counted round a vertical axis through (cx, cy): n per turn."""
    x, y = g.xyz.outputs["X"], g.xyz.outputs["Y"]
    return g.op("MULTIPLY", g.op("ARCTAN2", g.op("SUBTRACT", y, cy), g.op("SUBTRACT", x, cx)), n / (2 * math.pi))

def stone_facade(name, ring=None, stone="#f1e6d0", trim="#dccbad", glass="#3b4650", frame="#f4efe6",
                 lit="#6e3c1a", glow="#ff9a3c", roof="#8f989e"):
    """Ashlar limestone: tall French windows (centre mullion, transom) in a
    stone surround with a small cornice over each, some rooms lit; a
    rusticated ground floor of lit round-arched openings; a string course at
    every floor. Mirrors at x = 0, or with `ring` = (cx, cy, bays per turn)
    the bays run round a round tower."""
    g = m.Graph(name)
    x, y, z = (g.xyz.outputs[k] for k in "XYZ")
    if ring:
        s, bay = _angle(g, *ring), 2 * math.pi * ROTUNDA[2] / ring[2]
    else:
        s, bay = g.op("DIVIDE", g.op("ADD", g.op("ABSOLUTE", x), y), BAY), BAY
    xc = g.op("MULTIPLY", g.op("SUBTRACT", g.op("FRACT", s), 0.5), bay)
    ax = g.op("ABSOLUTE", xc)
    h = g.op("MULTIPLY", g.op("FRACT", g.op("DIVIDE", z, STOREY)), STOREY)
    ground = g.op("LESS_THAN", z, STOREY)
    upper = g.op("SUBTRACT", 1.0, ground)
    # upper floors
    pane = g.op("MULTIPLY", g.op("LESS_THAN", ax, 0.4), g.within(h, 0.35, 2.55))
    bars = g.op("MAXIMUM", g.op("LESS_THAN", ax, 0.035), g.within(h, 1.95, 2.02))
    surround = g.op("MAXIMUM", g.op("MULTIPLY", g.op("LESS_THAN", ax, 0.55), g.within(h, 0.25, 2.75)),
                    g.op("MULTIPLY", g.op("LESS_THAN", ax, 0.66), g.within(h, 2.75, 2.92)))
    # ground floor: round arches over the rustication
    arch = g.op("MULTIPLY", g.op("LESS_THAN", ax, 0.62), g.op("GREATER_THAN", h, 0.3))
    arch = g.op("MULTIPLY", arch, g.op("MAXIMUM", g.op("LESS_THAN", h, 1.9),
                                       g.op("LESS_THAN", g.op("ADD", g.op("MULTIPLY", xc, xc), g.op("MULTIPLY", g.op("SUBTRACT", h, 1.9), g.op("SUBTRACT", h, 1.9))), 0.62 ** 2)))
    groove = g.op("MULTIPLY", ground, g.op("LESS_THAN", g.op("FRACT", g.op("DIVIDE", z, 0.5)), 0.06))
    course = g.op("LESS_THAN", h, 0.15)
    cell = g.nt.nodes.new("ShaderNodeCombineXYZ")
    for k, src in zip("XYZ", (g.op("FLOOR", s), g.op("FLOOR", g.op("DIVIDE", z, STOREY)), g.op("SIGN", x))):
        g.nt.links.new(src, cell.inputs[k])
    noise = g.nt.nodes.new("ShaderNodeTexWhiteNoise")
    noise.noise_dimensions = "3D"
    g.nt.links.new(cell.outputs[0], noise.inputs["Vector"])
    on = g.op("MAXIMUM", g.op("MULTIPLY", g.op("MULTIPLY", pane, upper), g.op("GREATER_THAN", noise.outputs["Value"], 0.7)),
              g.op("MULTIPLY", arch, ground))
    open_ = g.op("MAXIMUM", g.op("MULTIPLY", pane, upper), g.op("MULTIPLY", arch, ground))
    body = g.mix(g.op("MAXIMUM", course, g.op("MULTIPLY", surround, upper)),
                 g.image("sandstone_blocks_05", 3.0, stone, "OVERLAY", 0.45), trim)
    body = g.mix(g.op("MULTIPLY", groove, g.op("SUBTRACT", 1.0, arch)), body, "#b9a98e")
    glazing = g.mix(on, glass, lit)
    wall = g.mix(open_, body, g.mix(g.op("MULTIPLY", bars, upper), glazing, frame))
    up = g.op("GREATER_THAN", _normal(g)["Z"], 0.5)
    g.color(g.mix(up, wall, roof))
    g.nt.links.new(g.mix(g.op("MULTIPLY", on, g.op("SUBTRACT", 1.0, up)), "#000000", glow), g.bsdf.inputs["Emission Color"])
    g.bsdf.inputs["Emission Strength"].default_value = 0.6
    return g.mat

def slate_roof(name, slate="#8a96a3", zinc="#a7b0b5", ring=None):
    """Blue-grey slate on the steep slopes, zinc on the shallow top; with
    `ring` = (cx, cy, ribs) zinc ribs run up a dome."""
    g = m.Graph(name)
    zinc_mask = g.op("GREATER_THAN", _normal(g)["Z"], 0.8)
    if ring:
        zinc_mask = g.op("MAXIMUM", zinc_mask, g.op("LESS_THAN", g.op("FRACT", _angle(g, *ring)), 0.1))
    return g.color(g.mix(zinc_mask, g.image("roof_slates_02", 2.0, slate), zinc))

def glasshouse(name, glass="#b9c9d1", iron="#2f3c37", leaves="#3e5c34", plinth="#d9cdb8", pane=0.9, row=1.1):
    """Matte blue-grey glass in a dark iron grid (ribs and purlins follow the
    face: across x on the end walls, across y on the long walls and vault),
    a stone plinth, and the plants inside showing as dark green behind the
    lower panes."""
    g = m.Graph(name)
    x, y, z = (g.xyz.outputs[k] for k in "XYZ")
    end = g.op("GREATER_THAN", g.op("ABSOLUTE", _normal(g)["Y"]), 0.7)
    a = g.op("ADD", y, g.op("MULTIPLY", end, g.op("SUBTRACT", x, y)))
    grid = g.op("MAXIMUM", g.op("LESS_THAN", g.op("FRACT", g.op("DIVIDE", a, pane)), 0.09),
                g.op("LESS_THAN", g.op("FRACT", g.op("DIVIDE", z, row)), 0.07))
    noise = g.nt.nodes.new("ShaderNodeTexNoise")
    noise.inputs["Scale"].default_value = 1.8
    noise.inputs["Detail"].default_value = 4.0
    g.nt.links.new(g.obj, noise.inputs["Vector"])
    plants = g.op("MULTIPLY", g.op("GREATER_THAN", noise.outputs["Fac"], 0.5),
                  g.op("LESS_THAN", g.op("ADD", z, g.op("MULTIPLY", noise.outputs["Fac"], 2.0)), 4.4))
    panes = g.mix(g.op("MULTIPLY", plants, 0.8), glass, leaves)
    return g.color(g.mix(g.op("LESS_THAN", z, 0.5), g.mix(grid, panes, iron), plinth))

def revolve(name, x, y, z, profile, material, parent=None, segments=16, caps=True, smooth=True):
    """A lathe round the vertical axis through (x, y): `profile` lists
    (radius, height above z) from the bottom up. Smooth parts go without caps
    (a cap would bend the side normals, and the shaders read normals)."""

    def build(bm):
        rings = [[bm.verts.new((x + r * math.cos(2 * math.pi * i / segments), y + r * math.sin(2 * math.pi * i / segments), z + h))
                  for i in range(segments)] for r, h in profile]
        for lo, hi in zip(rings, rings[1:]):
            for i in range(segments):
                bm.faces.new((lo[i], lo[(i + 1) % segments], hi[(i + 1) % segments], hi[i]))
        if caps:
            bm.faces.new(list(reversed(rings[0])))
            bm.faces.new(rings[-1])

    obj = p.mesh_object(name, build, material, parent)
    if smooth:
        obj.data.shade_smooth()
    return obj

def mansard(name, x0, y0, x1, y1, z, material, parent, lower=2.6, inset=0.8, upper=0.6, top_inset=1.6):
    """A mansard: a steep slate slope (the brisis) up from the cornice, then a
    shallow zinc one (the terrasson) to a flat top."""

    def build(bm):
        def ring(d, h):
            return [bm.verts.new(q) for q in ((x0 + d, y0 + d, z + h), (x1 - d, y0 + d, z + h),
                                              (x1 - d, y1 - d, z + h), (x0 + d, y1 - d, z + h))]

        a, b, c = ring(0, 0), ring(inset, lower), ring(inset + top_inset, lower + upper)
        for lo, hi in ((a, b), (b, c)):
            for i in range(4):
                bm.faces.new((lo[i], lo[(i + 1) % 4], hi[(i + 1) % 4], hi[i]))
        bm.faces.new(c)
        bm.faces.new(list(reversed(a)))

    return p.mesh_object(name, build, material, parent)

def dormers(name, xs, y_face, z, stone, roof, glass, parent):
    """Stone dormers with a pediment, standing in the front slope of a mansard."""

    def bodies(bm):
        for x in xs:
            p.add_box(bm, x - 0.55, y_face + 0.05, z + 0.1, x + 0.55, y_face + 1.1, z + 1.9)

    def windows(bm):
        for x in xs:
            p.add_box(bm, x - 0.32, y_face, z + 0.45, x + 0.32, y_face + 0.06, z + 1.65)

    p.mesh_object(name, bodies, stone, parent)
    p.mesh_object(name + "_windows", windows, glass, parent)
    for i, x in enumerate(xs):
        p.gable_roof(f"{name}_roof_{i}", x - 0.55, y_face + 0.05, x + 0.55, y_face + 1.1, z + 1.9, 0.5, roof, parent,
                     overhang=0.1, ridge_along="y")

def railing(name, points, z, material, parent, height=1.0, step=0.5):
    """Iron railing along a polyline: posts every `step` and a top rail (geometry)."""

    def build(bm):
        # posts stop under the rail, a corner post is not doubled, and each run's
        # rail sits 2 mm lower than the last, so no two faces share a plane
        for k, ((xa, ya), (xb, yb)) in enumerate(zip(points, points[1:])):
            n = max(1, round(math.hypot(xb - xa, yb - ya) / step))
            for i in range(0 if k == 0 else 1, n + 1):
                px, py = xa + (xb - xa) * i / n, ya + (yb - ya) * i / n
                p.add_box(bm, px - 0.03, py - 0.03, z, px + 0.03, py + 0.03, z + height - 0.03)
            top, pad = z + height - 0.002 * k, 0.04 - 0.005 * k  # each run's rail a little slimmer, so corners share no face
            p.add_box(bm, min(xa, xb) - pad, min(ya, yb) - pad, top - 0.06, max(xa, xb) + pad, max(ya, yb) + pad, top)

    return p.mesh_object(name, build, material, parent)

def vault_hall(name, x0, y0, x1, y1, wall, rise, material, parent, n=8):
    """A glasshouse hall: upright walls and a half-elliptic barrel vault,
    running along y, closed at both ends."""

    def build(bm):
        cx, hw = (x0 + x1) / 2, (x1 - x0) / 2
        loop = [(x0, 0.0)] + [(cx - hw * math.cos(math.pi * i / n), wall + rise * math.sin(math.pi * i / n)) for i in range(n + 1)] + [(x1, 0.0)]
        front = [bm.verts.new((px, y0, pz)) for px, pz in loop]
        back = [bm.verts.new((px, y1, pz)) for px, pz in loop]
        k = len(loop)
        for i in range(k):
            j = (i + 1) % k
            bm.faces.new((front[i], front[j], back[j], back[i]))
        bm.faces.new(front)
        bm.faces.new(list(reversed(back)))

    return p.mesh_object(name, build, material, parent)
