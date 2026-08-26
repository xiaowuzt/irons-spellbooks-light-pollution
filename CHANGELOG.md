# Changelog

Newest first. The top section is what gets uploaded to CurseForge and Modrinth,
so keep it about this release and keep it readable — it is release notes, not a
commit log.

## 1.1.0

### Shader pack support

- Works with Oculus / Iris shader packs. Effects used to vanish with a pack
  loaded: a pack composites its own render targets over Minecraft's main target
  at the end of the level render, so anything drawn before that was overwritten.
  World geometry now moves to a later stage when a pack is active, and carries
  the level's view matrix with it — the later stage is dispatched from a place
  that no longer has the camera rotation, which by itself put every effect
  off-screen.
- Effects are skipped during a pack's shadow pass instead of being drawn into
  the shadow map.

### Leviathan and World Tree, rebuilt

- Leviathan is a real snake rather than a flat ribbon: a closed body whose
  cross-section is a rounded trapezoid with a flat ventral plate, a constant-girth
  midsection, a whip tail, a broad flattened skull with a brow ridge and recessed
  eyes, an asymmetric gape, and recurved fangs. Scales are oblique dorsal rows
  with keels over transverse belly scutes.
- World Tree grows six orders of branches — 2548 of them — under a canopy of
  about 13600 leaf clusters carried on the outer two orders, which is what leaves
  the interior structure visible. The trunk is round with seven buttress roots
  that continue into the surface roots, and the trunk now erupts *before* the
  roots spread.
- Both are shaded per fragment from real surface normals, so a sixteen-sided tube
  no longer creases at every quad edge.

### Spells

- The fifteen spells are spread across eight schools instead of concentrating
  nine of them in Ender, so one armour set no longer buffs most of the mod.
  Scroll textures follow their school.
- Leviathan locks onto the creature you aimed at and follows it, and its strike
  brings the jaws down onto the target — the bite previously closed well above it
  and could miss entirely.

### Presentation

- Spell icons for Sky Collapse, Stellar Convergence, Second Sun, Singularity,
  Leviathan and World Tree, which had none.
- Literary Chinese (`lzh`) translation, using period units: 分/釐/毫 for
  fractions, 息 for seconds, 步 for blocks, 元命 for max health.
