"""The showmaster: a floating walnut whose lower shell is the talking jaw.

Exported on its own and lit by the TV at runtime, because it moves between the stages. Its furrows are real
geometry and its colour is painted per vertex (dark in the creases), so it needs no texture.
"""
import math

from mathutils import Vector, noise

from kn_common import ball, box, cylinder, empty, place, pydata, rgba
from kn_materials import Shader, flat

DARK, LIGHT = rgba("#4f2a12")[:3], rgba("#c28748")[:3]


def _shell(name, upper, mat, coll, parent):
    lat, lon = 44, 112
    theta0, theta1 = (0, math.pi / 2) if upper else (math.pi / 2, math.pi)
    verts, faces, colors = [], [], []
    for j in range(lat + 1):
        theta = theta0 + (theta1 - theta0) * j / lat
        for i in range(lon):
            phi = math.tau * i / lon
            unit = Vector((math.sin(theta) * math.cos(phi), math.sin(theta) * math.sin(phi), math.cos(theta)))
            # hard turbulence folds at its zero crossings: sharp creases between rounded lobes, like a walnut
            crease = noise.turbulence(unit * 2.3 + Vector((3.1, 0, 7.7)), 3, True)
            lip = .05 * math.exp(-((theta - math.pi / 2) / .09) ** 2)
            seam = .035 * math.exp(-(unit.x / .07) ** 2)
            radius = 1 + .16 * (crease - .45) + lip + seam
            verts.append((1.05 * unit.x * radius, .9 * unit.y * radius, (1.18 if upper else 1.0) * unit.z * radius))
            shade = min(1.0, max(0.0, (crease - .12) * 2.2))
            colors.append(tuple(d + (l - d) * shade for d, l in zip(DARK, LIGHT)) + (1.0,))
    for j in range(lat):
        for i in range(lon):
            a, b = j * lon + i, j * lon + (i + 1) % lon
            faces.append((a, b, b + lon, a + lon))
    mesh = pydata(name, verts, faces)
    paint = mesh.color_attributes.new("Color", "FLOAT_COLOR", "POINT")
    paint.data.foreach_set("color", [c for color in colors for c in color])
    return place(name, mesh, mat, coll, parent, smooth=True)


def build(coll):
    noise.seed_set(7)  # the same walnut on every build
    shell = Shader("WalnutShell").set(Roughness=.48)
    shell.link(shell.node("ShaderNodeVertexColor", layer_name="Color").outputs["Color"], "Base Color")
    white, black = flat("HostEye", "#ffffff", Roughness=.12), flat("HostPupil", "#13131c", Roughness=.15)
    mouth, tie = flat("HostMouth", "#321124", Roughness=.6), flat("HostTie", "#e63d85", Roughness=.75)
    brow = flat("HostBrow", "#7a4420", Roughness=.6)

    root = empty("Showmaster", coll)
    root["role"] = "walnut-host"
    _shell("WalnutUpperShell", True, shell.mat, coll, root)
    jaw = empty("WalnutJaw", coll, root, at=(0, .78, 0))
    _shell("WalnutLowerShell", False, shell.mat, coll, jaw).location.y = -.78
    ball("MouthCavity", (0, -.6, -.1), (.8, .3, .34), mouth, coll, root)
    for side in (-1, 1):
        ball("EyeWhite", (side * .36, -.84, .46), (.25, .14, .31), white, coll, root)
        ball("Pupil", (side * .34, -.965, .44), (.11, .06, .14), black, coll, root)
        ball("EyeGlint", (side * .34 - .04, -1.02, .5), (.035, .02, .04), white, coll, root, segments=12)
        lid = box("ExpressiveBrow", (side * .37, -.82, .9), (.4, .1, .08), brow, coll, root, .03)
        lid.rotation_euler.y = side * .2
    knot = cylinder("BowTieKnot", (0, -.25, -1.28), .13, .22, tie, coll, root, 24)
    knot.rotation_euler.x = math.pi / 2
    for side in (-1, 1):
        wing = cylinder("BowTieWing", (side * .27, -.25, -1.28), .04, .34, tie, coll, root, 4, .27)
        wing.rotation_euler.y = side * math.pi / 2
    return root
