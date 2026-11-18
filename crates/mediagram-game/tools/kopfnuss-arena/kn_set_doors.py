"""The category hall (Pick, Resolve): four ivory doors with glittering gold leaves on a marble floor."""
import math

from kn_common import ball, box, circle_points, empty, fx, prism, ring, tube
from kn_lighting import light
from kn_dressing import curtain, fx_quad, moulding
from kn_materials import ANSWERS

WORLD = {"ambient": "#e9e2f6", "strength": .3, "reflections": .8}
SPOTS = [(-5.4, .2), (-3.5, -.2), (-.5, .1), (.6, .5), (3.6, .1), (6.1, .2)]
LEAF_FRONT = .02  # the leaf sits back inside its frame, which is what gives the doorway depth


def _leaf(door, i, geo, fxc, M):
    box("DoorLeaf", (0, LEAF_FRONT + .06, 2.1), (2.16, .12, 4.0), M["goldGlitter"], geo, door, .0)
    double = i in (0, 3)
    for cx, width in (((-.54, .86), (.54, .86)) if double else ((0, 1.84),)):
        for cz, height in ((1.2, 1.55), (3.1, 1.7)):
            moulding("DoorMoulding", (cx, LEAF_FRONT - .025, cz), width, height, M["gold"], geo, door)
    if double:
        box("DoorSplit", (0, LEAF_FRONT - .02, 2.1), (.05, .05, 3.9), M["brass"], geo, door, .01)
    for kx in ((-.14, .14) if double else (.74,)):
        ball("DoorKnob", (kx, LEAF_FRONT - .05, 2.0), (.1, .08, .1), M["gold"], geo, door)
    fx_quad("DoorSparkle", (0, LEAF_FRONT - .09, 2.1), 2.1, 3.9, "sparkle", "#fff3c4", fxc, door, upright=True)


def _crown(door, i, geo, M):
    """What sits on the lintel. Kept low: the category names are written on the wall just above."""
    top = 4.5
    arched = i in (1, 2)
    if arched:
        def arch(radius, lift):
            return [(math.cos(a) * radius, 0, top + math.sin(a) * radius * .5 + (1 - abs(math.cos(a))) * lift)
                    for a in (math.pi * j / 32 for j in range(33))]
        tube("IvoryArch", arch(1.3, .25), .17, M["ivory"], geo, door)
        gold = [(x, -.12, z) for x, _, z in arch(1.0, .2)]
        tube("GoldArch", gold, .05, M["gold"], geo, door)
        if i == 2:
            for radius in (.2, .36, .52):  # a little rainbow of gold hoops standing on the lintel
                ring("GoldHoop", (0, -.06, top), radius, .035, M["gold"], geo, door, start=0, end=math.pi, count=24, vertical=True)
        else:
            for reach in gold[4:-4:4]:  # a sunburst: rays from the sun out to the gold arch
                tube("GoldRay", [(0, -.06, top + .05), (reach[0], -.1, reach[2])], .03, M["gold"], geo, door)
            ball("GoldSun", (0, -.08, top + .05), (.18, .1, .18), M["gold"], geo, door)
        peak = top + 1.3 * .5 + .25
    else:
        peak = 5.3 if i == 0 else 5.15
        prism("IvoryGable", [(-1.45, top), (-1.45, top + .2), (-.8, top + .4), (0, peak), (.8, top + .4),
                             (1.45, top + .2), (1.45, top)], -.29, .29, M["ivory"], geo, door)
        prism("GoldGable", [(-1.12, top + .12), (-.62, top + .33), (0, peak - .25), (.62, top + .33),
                            (1.12, top + .12)], -.34, -.29, M["goldGlitter"], geo, door)
    for lift, size in (((.2, .1),) if arched else ((.08, .11), (.25, .08))):  # each ball sits into what is below it
        ball("GoldFinial", (0, 0, peak + lift), (size,) * 3, M["gold"], geo, door)


def build(root, geo, fxc, lights, M):
    # only as much floor and wall as the camera can see: every metre more is atlas the doors lose
    box("HallFloor", (0, .4, -.04), (30, 15.2, .18), M["marble"], geo, root, .0, tex=.55)
    box("HallWall", (0, 7.35, 5.0), (30, .3, 10.4), M["lilac"], geo, root, .0, tex=.3)
    box("HallWainscot", (0, 7.15, .62), (30, .12, 1.15), M["ivory"], geo, root, .02, tex=.3)
    box("HallDadoRail", (0, 7.07, 1.24), (30, .1, .09), M["gold"], geo, root, .02, tex=.3)
    for j in range(-3, 4):
        x = j * 3.6
        box("HallPilaster", (x, 7.1, 5.0), (.62, .3, 10), M["ivory"], geo, root, .04, tex=.35)
        box("HallPilasterBand", (x, 7.02, 7.6), (.8, .34, .16), M["gold"], geo, root, .03, tex=.35)
        if j < 3:
            moulding("HallPanel", (x + 1.8, 7.17, 4.5), 2.3, 5.4, M["ivory"], geo, root, .07, tex=.3)
    for side in (-1, 1):
        curtain("HallDrape", (side * 12.5, 5.2), (side * 9.0, 6.6), .05, 10.5, M["velvetNight"], geo, root, 5, .3, .35)
    for i, (x, y) in enumerate(SPOTS):
        empty(f"CategorySpot_{i}", geo, root, at=(x, y, .05))
    for i in range(4):
        door = empty(f"CategoryDoor_{i}", geo, root, at=((i - 1.5) * 3.6, 1.4, 0))
        box("DoorPlinth", (0, -.1, .11), (3.3, 1.15, .14), M["marble"], geo, door, .03)
        for side in (-1, 1):
            box("DoorJamb", (side * 1.26, 0, 2.04), (.38, .55, 4.08), M["ivory"], geo, door, .06)
        box("DoorLintel", (0, 0, 4.29), (3.0, .6, .42), M["ivory"], geo, door, .06)
        _leaf(door, i, geo, fxc, M)
        _crown(door, i, geo, M)

    resolve = empty("Resolve_Stage", fxc)
    for i in range(4):
        points, _ = circle_points(((i - 1.5) * 3.6, .5, 1.1), 1.3, 0, math.pi, 48, vertical=True)
        fx(tube(f"TrickArc_{i}", points, .07, None, fxc, resolve), "energy", ANSWERS[i])

    light("LGT_Hall_Key", lights, (-5, -13, 13), "#fff0dc", 3200, 12, (0, 1, 2.5))
    light("LGT_Hall_Fill", lights, (9, -11, 6), "#d6ccff", 800, 10, (0, 1, 2.5))
    for j in range(-2, 3):
        light("LGT_Hall_Uplight", lights, (j * 3.6, 6.3, .3), "#ffb98a", 420, .5, (j * 3.6, 7.2, 6), "SPOT", 75)
    for i in range(4):
        x = (i - 1.5) * 3.6
        light("LGT_Hall_DoorSpot", lights, (x, -3.5, 9.5), "#ffe6bd", 380, .4, (x, 1.2, 2.6), "SPOT", 32)
