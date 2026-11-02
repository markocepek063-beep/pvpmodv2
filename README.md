# PvP Mod (Fabric, Minecraft 1.21.11)

Client-side only. Open settings with /pvpmod in chat. Config: config/pvpmod.json

- Items: click any item for its own screen: colour picker, preview, size. "Item in my hand..." edits whatever you hold.
- End crystal: 3D on/off (off = core cube only), bedrock base on/off.
- Totem pop: hide the on-screen totem, hide/resize/thin out/recolour the particles.
- Particles: master switch, global size, show/hide and size per particle type.

Build: push to GitHub; the Actions tab builds the jar (artifact "pvpmod-jar", use the one without -sources).
The item colour tint lives in its own mixin config (pvpmod.tint.mixins.json). If it ever fails to apply,
the rest of the mod still loads.
