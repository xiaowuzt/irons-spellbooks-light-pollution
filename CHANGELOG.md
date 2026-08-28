# Changelog

Newest first. The top section is what gets uploaded to CurseForge and Modrinth,
so keep it about this release and keep it readable — it is release notes, not a
commit log.

## 1.2.0

### Nine new spells

- **Gargantua** — a black hole built from the Kerr geometry rather than drawn as a
  dark ball with a ring around it. Light is bent by integrating the photon
  geodesic, so the disk's far side is genuinely lensed into arcs over and under
  the shadow while the near side crosses as a knife-thin bar, and terrain, sky and
  your other spells' light bend around it too. The proportions are the published
  *Interstellar* figures: the shadow is 2.6 times the horizon, the disk stops at
  the innermost stable circular orbit, and at roughly 4500 K there is no blue in
  it anywhere. It arrives at full size, hangs flat above the ground, swallows
  other spell lights one at a time, and detonates at criticality.
- **Cosmic Horseshoe** — the gravitational lens SDSS J1148+1930, whose arc the
  discovery paper measures at about 300 degrees; the missing sixty are what give
  it its name. The arc is not drawn but solved for, by inverting the lens map at
  every pixel, so the ring lands where the map is singular, the arc is thin
  because lensing stretches tangentially and not radially, each star-forming knot
  appears more than once, and a faint counter-image sits opposite the gap — none
  of it painted in. Unlike the other lenses here it does not swallow: magnification
  diverges at the Einstein radius, so the ring is the dangerous part and the
  interior is somewhere to stand.
- **Microquasar** — SS 433, whose disk precesses, so the ejecta are drawn out into a
  corkscrew: every blob keeps the direction it left with while the disk turns on
  under it. The helix has been imaged in radio, and the standard kinematic model
  reproduces it from a 160-day precession, a 20 degree cone and a jet speed of
  0.26c. The two jets do not match — relativistic beaming makes the one coming
  toward you brighter and bluer and the one going away dimmer and redder, from the
  Doppler factor cubed. It is the only spell here dodged by timing rather than
  distance: the jets sweep, so a safe radius does not stay safe.
- **Helix Nebula** — NGC 7293, the Eye of God. An expanding shell of cometary knots,
  each a globule whose star-facing side is being boiled off, leaving a bright head
  and a tail streaming radially away; about 40,000 have been counted in the real
  object. Two nested rings seen at an inclination make the eye, and they are
  different colours because they are different shells — blue-green in doubly
  ionised oxygen inside, red in hydrogen and nitrogen outside. The hazard travels:
  distance buys time, not safety, and what matters is when the shell arrives.
- **Magnetar** — SGR 1806-20, whose 2004 giant flare is the most powerful on record
  and ionised Earth's upper atmosphere from fifty thousand light years away. Closed
  dipole loops thread out from the poles, built from r = r0 sin²θ so they are the
  curves a dipole actually makes, and they wind tighter and shift from cold violet
  toward white-hot as the field loads. Only the lines discharge — the gaps between
  them are safe, which makes it the one spell where where you stand matters more
  than how far away you are. Then the magnetosphere rearranges itself all at once.
- **Tidal Disruption** — a star pulled apart, and the star is the subject rather than
  the hole: the tidal field stretches it into one enormously long thin stream that
  wraps back around and lashes. The flare when the bound debris returns follows the
  t^(-5/3) fallback law that identifies these events, so the light curve is computed
  from the physics rather than eased by hand.
- **Quasar Jet** — one needle out of the nucleus, because at these speeds beaming makes
  the counter-jet invisible. Straight, not precessing, and the knots racing along it
  move at the apparent speed relativistic geometry gives: about six times light speed
  at a Lorentz factor of 10 and 17 degrees, the figure actually tracked for M87. Only
  the knots hit, so the beam is a matter of timing rather than distance, and it ends in
  a terminal hotspot inside a diffuse lobe.
- **Wolf-Rayet Pinwheel** — two hot stars whose colliding winds condense dust into an
  Archimedean spiral, imaged for real with aperture masking on Keck. Constant pitch,
  empty space between the arms and dust colours rather than a hot disk, so it cannot be
  mistaken for a third accretion disk. It rotates rigidly, which means the safe gaps
  travel: a gap is only safe until the next arm reaches it.
- **Crab Nebula** — a hollow cage of filaments over a void, with a synchrotron wind
  nebula inside it driven by the pulsar. The two components are shaded as the different
  materials they are: sharp knotted line emission in red and green, and a smooth
  structureless blue-white glow. Unlike the World Tree it is hollow, so it is a trap
  rather than a radius — outside is safe, the filaments sting, the interior pulses, and
  leaving means crossing the cage again.

### Balance

- Every spell's damage is now a distinct figure. Five spells previously shared the
  same 30–34% finisher and eight sat between 4% and 6% per tick, which made most
  of the set feel interchangeable on paper. Totals are unchanged in aggregate —
  this spreads them out, it does not buff or nerf the mod — and the ladder now
  follows cooldown, from Silhouette's 3% a second up to Stargrave Singularity's
  93% execution. Existing tooltips are updated to match in all three languages.
  Twenty-four spells, forty-nine damage figures, no two the same.

### Fixes

- Fixed a startup crash on AMD graphics cards. Six shaders declared a function named
  `noise3` or `noise2`, and those are reserved built-in names in the GLSL
  specification — the built-in returns a different type, so declaring one is an
  illegal overload. AMD's compiler declares those built-ins and NVIDIA's does not,
  which is why this only ever affected some machines. Constellation, Second Sun,
  Singularity, Sky Collapse and Eclipse Severance were all affected; the crash only
  ever named the first, because loading stopped there.
- A shader that fails to compile no longer takes the game down with it. One bad
  shader used to be an unrecoverable startup crash; now it is logged, that one effect
  is skipped, and everything else runs. Driver-specific GLSL differences cannot all
  be known in advance, so the cost of one has to be a missing effect rather than an
  unplayable game.

### Presentation

- Spell icons for all nine new spells, drawn from the same geometry the effects use.
- Every new spell has a `_show` command that stands one up for inspection with a
  matching `_clear`: `/gargantua_show`, `/horseshoe_show`, `/quasar_show`,
  `/helix_show`, `/magnetar_show`, `/tde_show`, `/jet_show`, `/pinwheel_show` and
  `/crab_show`. Operator-only, and they do no damage.
- The Simplified Chinese text for the Cosmic Horseshoe was written in the classical
  register, with the 分/釐/毫 fractions that belong to the Literary Chinese file.
  It now reads as modern Chinese with percentages, like the rest of `zh_cn`.

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
