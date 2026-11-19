"""Stage cameras and lamps for the Kopfnuss sets. Both exist only in Blender: the TV gets the cameras' framing
and the light as the baked picture."""
import math

import bpy
from mathutils import Vector

from kn_common import rgba


def _aim(obj, at, target):
    obj.location = at
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat("-Z", "Y").to_euler()


def camera(name, coll, at, target, lens=43):
    data = bpy.data.cameras.new(name)
    data.lens = lens
    data.clip_end = 150
    obj = bpy.data.objects.new(name, data)
    coll.objects.link(obj)
    _aim(obj, at, target)
    return obj


def light(name, coll, at, color, energy, size=8, target=(0, 0, 0), kind="AREA", spot=45):
    data = bpy.data.lights.new(name, kind)
    data.energy = energy
    data.color = rgba(color)[:3]
    if kind == "AREA":
        data.shape = "DISK"
        data.size = size
    else:
        data.shadow_soft_size = size
        data.spot_size = math.radians(spot)
        data.spot_blend = .35
    obj = bpy.data.objects.new(name, data)
    coll.objects.link(obj)
    _aim(obj, at, target)
    # a bare lamp would mirror in the gloss floors as a white disc; its light stays, its reflection goes.
    # What the shiny things reflect instead is the studio HDRI and the set's own lit pieces.
    obj.visible_glossy = False
    return obj
