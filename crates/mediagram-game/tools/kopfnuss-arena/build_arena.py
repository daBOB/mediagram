"""Build the Kopfnuss TV stages: four lit Cycles sets, baked to one atlas each and exported for the TV.

Sources first:  python3 fetch_textures.py
Whole build:    blender -b --python build_arena.py -- --bake [--size 2048] [--samples 160] [--out <folder>]
One set again:  ... -- --bake --rebake Doors      (the other sets keep the atlas already in the cache)
Export again:   ... -- --bake --rebake none       (every set from the cache, a few seconds)
Live lookdev:   import build_arena; build_arena.build()   (Blender MCP)

Each set gets its own scene (KN_Set_<Name>) with its lights and world, so `look` shows it as it will bake.
All show text stays HTML; the 3D carries no game state.
"""
import importlib
import os
import sys

import bpy

HERE = os.path.dirname(os.path.abspath(__file__))
PUBLIC = os.path.abspath(os.path.join(HERE, "..", "..", "client", "public", "kopfnuss"))
if HERE not in sys.path:
    sys.path.insert(0, HERE)

import kn_bake
import kn_common
import kn_dressing
import kn_export
import kn_library
import kn_materials
import kn_host
import kn_lighting
import kn_set_doors
import kn_set_pyramid
import kn_set_quiz
import kn_set_winner

SETS = {"Doors": kn_set_doors, "Quiz": kn_set_quiz, "Pyramid": kn_set_pyramid, "Winner": kn_set_winner}
# stage -> (its set, camera position, camera target, lens). The HTML layout is tuned to these framings.
STAGES = {
    "Pick": ("Doors", (.35, -18.5, 5.2), (0, 1.3, 2.9), 48),
    "Resolve": ("Doors", (.35, -18.5, 5.2), (0, 1.3, 2.9), 48),
    "Question": ("Quiz", (0, -23, 10), (0, 0, 2.0), 43),
    "Reveal": ("Quiz", (0, -21, 8.5), (0, .4, 2.2), 43),
    "Climb": ("Pyramid", (12, -29, 18), (0, 1.5, 3.2), 43),
    "Fact": ("Winner", (7, -19, 8), (0, .3, 2.2), 43),
}


def _clear(scene):
    """Empty one of our scenes. Only KN_ scenes are ever touched; the user's other scenes stay as they are."""
    data = {obj.data for obj in scene.objects if obj.data}
    for obj in list(scene.objects):
        bpy.data.objects.remove(obj, do_unlink=True)
    for coll in list(scene.collection.children):
        bpy.data.collections.remove(coll)
    for block in data:
        if block.users == 0:
            bpy.data.batch_remove([block])


def _world(name, ambient, strength, reflections):
    """Soft ambient colour for diffuse light; the studio HDRI only where something shiny looks for a reflection."""
    world = bpy.data.worlds.get("KN_World_" + name) or bpy.data.worlds.new("KN_World_" + name)
    nodes, links = world.node_tree.nodes, world.node_tree.links
    nodes.clear()
    out = nodes.new("ShaderNodeOutputWorld")
    soft, shiny, mix = nodes.new("ShaderNodeBackground"), nodes.new("ShaderNodeBackground"), nodes.new("ShaderNodeMixShader")
    soft.inputs["Color"].default_value = kn_common.rgba(ambient)
    soft.inputs["Strength"].default_value = strength
    env = nodes.new("ShaderNodeTexEnvironment")
    env.image = bpy.data.images.load(os.path.join(kn_materials.CACHE, "wooden_studio_05_hdri.hdr"), check_existing=True)
    shiny.inputs["Strength"].default_value = reflections
    links.new(env.outputs["Color"], shiny.inputs["Color"])
    links.new(nodes.new("ShaderNodeLightPath").outputs["Is Glossy Ray"], mix.inputs["Factor"])
    links.new(soft.outputs["Background"], mix.inputs[1])
    links.new(shiny.outputs["Background"], mix.inputs[2])
    links.new(mix.outputs["Shader"], out.inputs["Surface"])
    return world


def _render_settings(scene):
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = 128
    scene.cycles.use_denoising = True
    scene.cycles.sample_clamp_indirect = 8.0
    scene.render.resolution_x, scene.render.resolution_y = 960, 540
    scene.view_settings.view_transform = "AgX"
    try:
        scene.view_settings.look = "AgX - Medium High Contrast"
    except TypeError:
        pass


def build():
    """Build the four set scenes with materials, lights, world and stage cameras, and the host. Always all of
    them: the material library is rebuilt each time, and a set left out would keep slots pointing at nothing."""
    for module in (kn_common, kn_materials, kn_library, kn_lighting, kn_dressing, kn_host, kn_bake, kn_export,
                   *SETS.values()):
        importlib.reload(module)
    M = kn_library.build()
    for old in [s for s in bpy.data.scenes if s.name.startswith("KN_") and not s.name.startswith("KN_Set_")]:
        if old.name.startswith("KN_Export"):  # only links the sets' collections: drop the links, not the sets
            for coll in list(old.collection.children):
                old.collection.children.unlink(coll)
        _clear(old)
        bpy.data.scenes.remove(old)
    for name, module in SETS.items():
        scene = bpy.data.scenes.get("KN_Set_" + name) or bpy.data.scenes.new("KN_Set_" + name)
        _clear(scene)
        geo, fxc, lights, cams, rig = (kn_common.collection(f"COL_{name}_{part}", scene)
                                       for part in ("Geo", "FX", "Lights", "Cameras", "Rig"))
        fxc.hide_render = True  # runtime effects: drawn by the TV, never rendered or baked
        root = kn_common.empty("Set_" + name, geo)
        module.build(root, geo, fxc, lights, M)
        for obj in [o for o in geo.objects if o.type == "EMPTY"]:  # the export takes the rig, not the pieces
            geo.objects.unlink(obj)
            rig.objects.link(obj)
        for stage, (set_name, at, target, lens) in STAGES.items():
            if set_name == name:
                cam = kn_lighting.camera("Camera_" + stage, cams, at, target, lens)
                scene.camera = scene.camera or cam
        scene.world = _world(name, **module.WORLD)
        _render_settings(scene)
        print("KN_SET", name, len(geo.all_objects), "geo", len(fxc.all_objects), "fx", len(lights.all_objects), "lights")
    kn_host.build(kn_common.collection("COL_Host", bpy.data.scenes.new("KN_Host")))


def bake(size=2048, samples=160, rebake=None, out=PUBLIC):
    """Build afresh, bake every set and write the two GLBs the TV loads into `out`. With `rebake`, only the
    named sets are rendered again and the rest reuse their cached atlas. Always a whole arena: the TV needs
    all four sets in the one file."""
    unknown = [name for name in rebake or () if name not in SETS and name != "none"]
    if unknown:
        raise SystemExit(f"--rebake: no set named {unknown}. The sets are {list(SETS)}; 'none' only exports again.")
    build()
    for name in SETS:
        kn_bake.bake_set(bpy.data.scenes["KN_Set_" + name], name, size, samples,
                         reuse=rebake is not None and name not in rebake)
    os.makedirs(out, exist_ok=True)
    kn_export.export_arena(os.path.join(out, "kopfnuss-arena.glb"), list(SETS))
    kn_export.export_host(os.path.join(out, "showmaster.glb"))


if __name__ == "__main__":
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    option = lambda flag, default: args[args.index(flag) + 1] if flag in args else default
    if "--bake" in args:
        bake(int(option("--size", 2048)), int(option("--samples", 160)),
             option("--rebake", "").split(",") if "--rebake" in args else None, option("--out", PUBLIC))
    else:
        build()
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(kn_materials.CACHE, "kopfnuss-stages.blend"), copy=True)
