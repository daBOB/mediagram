"""Export the baked stages and the showmaster as the two GLBs the TV loads."""
import os

import bpy

PARTS = ("Rig", "Baked", "FX", "Cameras")


def export_arena(path, sets):
    """Rig empties (spots, stage groups), baked meshes, effect meshes and stage cameras of every set: no lights,
    no source pieces."""
    scene = bpy.data.scenes.new("KN_Export")
    for name in sets:
        for part in PARTS:
            scene.collection.children.link(bpy.data.collections[f"COL_{name}_{part}"])
    _write(scene, path, stage=True)


def export_host(path):
    _write(bpy.data.scenes["KN_Host"], path, stage=False)


def _write(scene, path, stage):
    """The stage goes out with its cameras and without normals: its sets are unlit pictures, and the few effect
    meshes that shade by their normal get one computed on the TV. The host is lit, so it keeps its normals."""
    bpy.context.window.scene = scene
    result = bpy.ops.export_scene.gltf(
        filepath=path, export_format="GLB", use_active_scene=True, export_apply=True, export_cameras=stage,
        export_normals=not stage, export_lights=False, export_extras=True, export_image_format="WEBP",
        export_image_quality=85)
    if result != {"FINISHED"} or not os.path.getsize(path):
        raise RuntimeError(f"export of {path} ended with {result}")
    print("KN_EXPORTED", path, os.path.getsize(path), "bytes")
