# Iron's Spells: Light Pollution

An addon for *Iron's Spells 'n Spellbooks* that adds many more legendary spells.

Most are built from a real astronomical object, and each has its own rendering and its own
means of avoidance: some by distance, some by timing, some by where you stand.

**Discord:** https://discord.gg/adKfbRDn6V

---

## Lighting and shadows

Real-time coloured point lights and ray-traced shadows, without a shader pack.

- **Dynamic light sources.** Spell effects illuminate their surroundings, with brightness and
  colour following each spell's phases.
- **Ray-traced shadows.** A 64×64×64-block occupancy volume is maintained around the camera,
  each block subdivided 8×8×8, giving intersection tests 1/8-block precision. The volume is
  anchored in world coordinates and rolls by whole blocks, so subvoxel boundaries do not slide
  across terrain as the camera moves — which would otherwise make thin occluders shimmer.
- **Light through detailed geometry.** Fence gaps, trapdoor openings and iron-bar spacing all
  transmit light and cast the corresponding patterns. Occlusion masks are baked from each
  block's model and texture alpha, so shapes are consistent from any angle.
- **Soft shadows.** Several shadow rays are sampled across each light's spherical surface per
  frame and converged by temporal accumulation.
- **Spatiotemporal denoising.** Temporal reprojection with edge-aware spatial filtering.

## Effect rendering

- **Real geometry.** Strand structures are closed tubes shaded per fragment from a true surface
  normal; central bodies are sphere meshes with limb darkening and surface granulation that
  rotates with the body.
- **Screen-space gravitational lensing.** The black hole integrates light bending along
  geodesics, deflecting terrain, sky and other spells' light with it; the gravitational lens
  resolves its arc by inverting a lens map per pixel.
- **Physical derivation.** Relativistic beaming, dipole field geometry, apparent superluminal
  motion and the t^(-5/3) fallback law, used to produce the correct forms.

## Getting them

- **The Scroll Forge.** Every spell appears under its school's focus, and needs legendary ink
  like any other legendary.
- **Crafting.** Each scroll has its own recipe: four arcane essence, two legendary ink, a sheet
  of paper, the rune of its school, and one item that says which spell it is — crying obsidian
  for Gargantua, a lodestone for the Magnetar, a sculk catalyst for the Crab Nebula. Ordinary
  shaped recipes, so JEI and EMI both list them.
- **Loot.** End city treasure, ancient cities, stronghold libraries, bastion treasure and
  woodland mansions can each turn up one, at a low chance.
- **Inscription.** Copy a scroll into a spell book at an Inscription Table as usual.

## Presentation

- **Tooltip styles.** Seven, off by default: a rounded panel, radiating rays, the spell's icon
  drifting behind the box, a particle field, a star-field ring passing through the tooltip,
  counter-rotating sigils, and a panel-less pinwheel text layout. Each takes its spell's own
  colour. Command `/lightpollution frame <style>`, or the `tooltipStyle` config option.
- **Floating damage numbers.** Optional, off by default. Shown above the target in the spell's
  colour. Since these spells resolve damage continuously, damage on one target is accumulated
  briefly and shown merged. Command `/lightpollution damage on`, or the `floatingDamage`
  config option.
- **Spell captions.** Several spells display a prompt at their turning point.
- **Languages.** English, Simplified Chinese, Literary Chinese.

## Compatibility

- Works with Oculus / Iris shader packs. World geometry is drawn after a pack has composited,
  so it is not overwritten.
- Requires *Iron's Spells 'n Spellbooks*, on both client and server: the spells register
  entities and damage types server-side, and the visuals are client-side.
- The lighting pipeline is independent of vanilla block light levels, so it does not affect
  mob spawning or anything else that reads them.
- A shader failing to compile on a particular driver is logged and skipped, costing only that
  effect.
