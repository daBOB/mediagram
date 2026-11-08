"""Hacienda Sol's own shapes, materials and layout constants
(the stages are built in `hacienda_sol.py`)."""

import math
import bmesh
from mathutils import Matrix, Vector
import materials as m
import parts as p


STOREY = 3.4

PLOT = (20, 16)

MID = -2.25  # the casa's axis: entrance arch, bell gable and courtyard gate line up on it

def wall_axis(axis):
    """(along the wall, z, depth) to plot xyz, for a wall running along x or y."""
    return (lambda s, z, t: (s, t, z)) if axis == "x" else (lambda s, z, t: (t, s, z))

def arch_outline(s0, s1, bays, spring, top, base=0.0, pier=0.5, segs=6):
    """A wall face from s0 to s1 cut by round-arched openings down to `base`:
    one simple polygon, as the openings all reach the bottom edge."""
    span = (s1 - s0 - (bays + 1) * pier) / bays
    r = span / 2
    pts = [(s0, top), (s0, base)]
    for i in range(bays):
        o0 = s0 + pier + i * (span + pier)
        pts += [(o0, base), (o0, spring)]
        pts += [(o0 + r + r * math.cos(math.pi * (1 - k / segs)), spring + r * math.sin(math.pi * (1 - k / segs))) for k in range(1, segs)]
        pts += [(o0 + span, spring), (o0 + span, base)]
    return pts + [(s1, base), (s1, top)]

def arcade(name, s0, s1, bays, t0, t1, spring, top, material, parent, axis="x"):
    pts = arch_outline(s0, s1, bays, spring, top)
    return p.mesh_object(name, lambda bm: p.prism(bm, pts, t0, t1, wall_axis(axis)), material, parent)

def casa(name, x0, y0, x1, y1, eaves, rise, wall, roof, parent, ridge="x", overhang=0.5, thick=0.18):
    """Walls with gable ends in `wall` (render all the way up) and two
    terracotta slabs; returns the body and the roof."""
    if ridge == "x":
        a0, a1, b0, b1, P = x0, x1, y0, y1, (lambda b, z, a: (a, b, z))
    else:
        a0, a1, b0, b1, P = y0, y1, x0, x1, (lambda b, z, a: (b, a, z))
    bc, top = (b0 + b1) / 2, eaves + rise
    ze = eaves - overhang * rise / (bc - b0)
    body = p.mesh_object(name, lambda bm: p.prism(bm, [(b0, 0), (b1, 0), (b1, eaves), (bc, top), (b0, eaves)], a0, a1, P), wall, parent)

    def slabs(bm):
        for be in (b0 - overhang, b1 + overhang):
            p.prism(bm, [(be, ze), (bc, top), (bc, top + thick), (be, ze + thick)], a0 - overhang, a1 + overhang, P)

    return body, p.mesh_object(name + "_roof", slabs, roof, parent)

def lean_to(name, a0, a1, b_out, b_wall, z_out, z_wall, roof, parent, axis="x", thick=0.15):
    """A single-pitch tiled roof over an arcade, from its outer edge up to the wall."""
    P = (lambda b, z, a: (a, b, z)) if axis == "x" else (lambda b, z, a: (b, a, z))
    return p.mesh_object(name, lambda bm: p.prism(bm, [(b_out, z_out), (b_wall, z_wall), (b_wall, z_wall + thick), (b_out, z_out + thick)],
                                                a0, a1, P), roof, parent)

def tube(bm, a, b, r, segs=8):
    """A round stem from point a to point b."""
    a, b = Vector(a), Vector(b)
    d = b - a
    rot = d.to_track_quat("Z", "Y").to_matrix().to_4x4()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=segs, radius1=r, radius2=r * 0.9, depth=d.length,
                          matrix=Matrix.Translation((a + b) / 2) @ rot)

def saguaro(name, x, y, height, material, parent, turn=0.0):
    """A saguaro: a ribbed column with two upturned arms and rounded tips."""
    c, s = math.cos(turn), math.sin(turn)

    def build(bm):
        tube(bm, (x, y, 0), (x, y, height), 0.22)
        for side, z0, reach, up in ((1, height * 0.35, 0.7, height * 0.35), (-1, height * 0.5, 0.55, height * 0.28)):
            ex, ey = x + side * reach * c, y + side * reach * s
            tube(bm, (x, y, z0), (ex, ey, z0 + 0.15), 0.16)
            tube(bm, (ex, ey, z0), (ex, ey, z0 + up), 0.16)
            bmesh.ops.create_icosphere(bm, subdivisions=1, radius=0.16, matrix=Matrix.Translation((ex, ey, z0 + up)))
        bmesh.ops.create_icosphere(bm, subdivisions=1, radius=0.22, matrix=Matrix.Translation((x, y, height)))

    return p.mesh_object(name, build, material, parent)

def nopal(name, x, y, material, parent):
    """A prickly pear: flat oval pads stacked on each other."""

    def build(bm):
        for cx, cy, cz, tilt in ((0, 0, 0.35, 0.0), (0.3, 0.05, 0.85, -0.5), (-0.28, -0.05, 0.8, 0.45), (0.05, 0.0, 1.25, 0.1)):
            bmesh.ops.create_icosphere(bm, subdivisions=1, radius=1.0, matrix=Matrix.Translation((x + cx, y + cy, cz))
                                       @ Matrix.Rotation(tilt, 4, "Y") @ Matrix.Diagonal((0.28, 0.07, 0.36, 1.0)))

    return p.mesh_object(name, build, material, parent)

def saltillo(name):
    """Terracotta floor tiles in a light grout grid."""
    g = m.Graph(name)
    grout = g.op("MAXIMUM", g.op("LESS_THAN", g.op("FRACT", g.op("DIVIDE", g.xyz.outputs["X"], 0.6)), 0.05),
                 g.op("LESS_THAN", g.op("FRACT", g.op("DIVIDE", g.xyz.outputs["Y"], 0.6)), 0.05))
    return g.color(g.mix(grout, g.image("plastered_wall_04", 2.0, "#c47a52", "OVERLAY", 0.6), "#e2d3bd"))

def talavera(name):
    """Blue-and-white glazed tiles, matte like everything else."""
    g = m.Graph(name)
    check = g.nt.nodes.new("ShaderNodeTexChecker")
    check.inputs["Scale"].default_value = 5.0
    g.nt.links.new(g.obj, check.inputs["Vector"])
    return g.color(g.mix(check.outputs["Fac"], "#f0ebe0", "#2c5aa0"))
