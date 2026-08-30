# Changelog

Newest first. The top section is what gets uploaded to CurseForge and Modrinth,
so keep it about this release and keep it readable — it is release notes, not a
commit log.

## 1.4.0

Questions, bug reports and suggestions: **https://discord.gg/adKfbRDn6V**

### Nineteen of the effects are now open to other mods

1.3.0 opened three. This opens sixteen more, plus the animated text and the tooltip frames.

Visuals only, as before: no damage, no entity, no spell. Client-side, and nothing is synced — a mod
that wants other players to see an effect sends its own packet and calls this in the client handler.

- **Structures that hang there.** `magnetar`, `microquasar`, `pinwheel`, `quasarJet`, `crabNebula`,
  `constellation`, `stellarConvergence`, `worldTree`
- **Events that run and finish.** `tidalDisruption`, `helixNebula`, `secondSun`, `singularity`,
  `skyCollapse`, `starfall`, `leviathan`, `eclipseSeverance`, `funeralNova`

Each takes a params record — an azimuth or bearing, a seed, a scale, a lifetime — and returns a handle
to move it or take it away. `/lightpollution fx <name> [scale]` goes through the same public entry
points a caller would, and `/lightpollution fx clear` removes what it made.

### Animated text and tooltip frames

`LightPollutionText.styled(text, style)` returns a Component that animates wherever Minecraft draws
it — a tooltip, a chat line, a book. Ten styles, as an enum rather than the format codes they were
before. `LightPollutionText.inWorld(text, pos)` floats text at a world position with eighteen entrance
and exit animations.

`LightPollutionFx.registerTooltipAccent(item, colour)` gives another mod's item this pack's tooltip
frame. A tag overload covers a whole set at once. The colour is the caller's; the *style* stays the
player's, set in the config or with `/lightpollution frame` — a caller who could force a style would be
overriding a preference, and two mods each forcing their own would leave the player with no way to get
a consistent inventory.

### Not opened

Cosmic Horseshoe, Silhouette and Starless are full-screen passes with no geometry and no position, so
a handle with `setPosition` on it would be a lie. Gargantua's lens copies the framebuffer twice per
instance. Celestial Judgment, Chromatic Accretion and Stargrave Singularity draw through Minecraft's
own entity renderers and key their look off entity UUIDs, so there is nothing to hand a position to.
These need a different interface than the other nineteen, not a worse version of this one.

### Things found while extracting

The point of this work was to get the shape maths out from behind the spells, and moving it turned up
several things that were invisible while each renderer owned its own copy.

- **A public API that had been silently doing nothing.** `DynamicTextApi` reflectively looked up
  `cn.blockforge.dynamictext.client.DynamicTextRuntime` — the package that code lived in *before* it
  was flattened into this mod. The lookup failed every time and the exception was swallowed, so all
  four runtime controls were no-ops. That path never existed in any released build of this mod.
- **The Crab Nebula's anchor syncs and saves an azimuth that nothing reads.** Every filament's tilt
  and spin is hashed out of the seed, so the cage has no single axis to turn.
- **Eight copies of two hash functions.** These are the reason a spell's knots, filaments and meteors
  land in the same places on the server and on every client with none of it synced. Copies that were
  identical today could diverge under an innocent edit, and the symptom would be each player seeing a
  subtly different arrangement rather than an error. One copy each now.
- **The Helix Nebula's knot placement was written inline in its renderer**, so that renderer could not
  have drawn anything but the spell's own entity even in principle.
- Roughly 700 lines of duplicated timeline, fade and bookkeeping code came out of the anchor entities.

Every extraction was checked numerically rather than by eye: knot, filament, meteor, branch and spine
positions across several seeds, ages and indices are bit-identical to what they were, the Crab's
filament loops still close, and the magnetar's field lines still terminate two star-radii apart. The
four scripts that do this are in `scripts/`.

## 1.3.0

Questions, bug reports and suggestions: **https://discord.gg/adKfbRDn6V**

### Other mods can now draw these effects

The renderings in this pack were locked to the spells that caused them. Each renderer walked a list
of that spell's anchor entities, so there was no way to put a tidal disruption anywhere without also
casting the spell, its damage and its cooldown. Three of them are now available on their own.

- **`LightPollutionFx.tidalDisruption(pos, params)`** — a star stretched into a stream that wraps
  back around and flares when the bound debris returns.
- **`LightPollutionFx.helixNebula(pos, params)`** — an expanding shell of cometary knots in two
  nested rings, each knot's tail streaming away from the white dwarf.
- **`LightPollutionFx.crabNebula(pos, params)`** — a cage of tangled filament loops around a pulsar,
  with the wind nebula pulsing inside it.

Each returns a handle to move the effect or take it away, and each takes a params record for the
azimuth, the seed, a scale and a lifetime. Visuals only: no damage, no entity, no spell. They are
drawn rather than simulated and nothing is synced, so a mod that wants other players to see one
sends its own packet and calls this in the client handler — the alternative would be a wrapper that
worked in single player and quietly failed in multiplayer.

`/lightpollution fx tidal_disruption|helix_nebula|crab_nebula [scale]` goes through the same public
entry points a caller would use, and `/lightpollution fx clear` removes what it made.

### Tooltip frames work alongside ModernUI

One frame style drew correctly with ModernUI installed and the other seven drew a doubled border —
ours over theirs. Forge checks whether an event has been cancelled once per listener, at the moment
it invokes that listener, rather than breaking out of the loop when one cancels; so cancelling the
tooltip event only suppresses the listeners that run *after* ours, and ModernUI's was running first.
Raising our priority puts us ahead of it. The one style that is a background rather than a takeover
now stands down entirely when ModernUI is present, since both were drawing a panel and neither was
wrong to.

### Under it

Getting the effects out from behind the spells meant the shape maths had to stop living on the
entities and inside the renderers, and pulling it out turned up a few things worth naming.

- The Helix Nebula's knot placement was written **inline in its renderer** — the frame, the hash, the
  scatter through the shell's thickness. That renderer could not have drawn anything but the spell's
  own entity even in principle.
- The Crab Nebula's anchor syncs and saves an azimuth that **nothing reads**. The cage has no single
  axis to turn, because every filament's tilt and spin is hashed out of the seed, so the field never
  had anything to mean. It is not in the public params.
- The hash that places knots and filaments existed as two byte-identical copies. It is the reason the
  server and every client agree on an arrangement without any of it being synced, so a silent
  divergence between the copies would have shown each player a subtly different cage rather than
  raising an error. One copy now.
- The three effects' fade-in, ageing and removal bookkeeping was the same sixty lines three times.

Every extraction was checked numerically rather than by eye: knot and filament positions across
several seeds, ages and indices are bit-identical to what they were, and the Crab's loops still
close.

## 1.2.1

Questions, bug reports and suggestions: **https://discord.gg/adKfbRDn6V**

### The spells are obtainable

Every spell in this pack was creative-only, which was not intentional: all twenty-four carried
`allowCrafting = false`, and that one flag is what Iron's Spells' Scroll Forge checks before
offering a spell. So there are now three ways to get them.

- **The Scroll Forge.** All twenty-four now appear under their school's focus, and need legendary
  ink like any other legendary. Nothing else was needed for this — the forge scans the spell
  registry rather than reading a recipe list, so the flag was the only thing in the way. Iron's
  own JEI plugin covers that screen, so they show up there too.
- **Crafting.** Each scroll has its own recipe: four arcane essence, two legendary ink, a sheet of
  paper, the rune of its school, and one item that says which spell it is — crying obsidian for
  Gargantua, a lodestone for the Magnetar, a sculk catalyst for the Crab Nebula. Ordinary shaped
  recipes, so JEI and EMI both list them with no extra work.
- **Loot.** End city treasure, ancient cities, stronghold libraries, bastion treasure and woodland
  mansions can each turn up one, at a low chance.

The four Eldritch spells — Funeral Nova, Magnetar, Silhouette, Starless — needed the crafting
recipe more than the others. Iron's constructs the Eldritch school with `allowLooting = false`,
and that flag is on the school rather than in any config, so those four are excluded from every
loot table and every wandering trade in the game. A recipe is the only route to them that does not
involve editing another mod's data.

Copying a scroll into a spell book already worked and needed no change; it is mentioned here only
because it was not obvious.

### Fixes

- The sigil tooltip style is no longer two flat triangles. Both of its problems were mine: the two
  halves were counter-rotating, where the original turns them the same way half a turn apart, so it
  never resolved into a six-pointed star at all; and the shells were being blended when the original
  writes them opaque, which mixed all three into one muddy shape instead of layering into a bright
  band with a rim either side. The band was also a fixed seventy pixels wide against a much smaller
  star, which at these proportions came out at seventy percent of the radius — very nearly solid.
- The ring tooltip style was about thirteen hundred pixels across, most of it off screen. It was
  sized from the panel's width, and these tooltips run fifteen times wider than they are tall.

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

### Rendering

- The new effects' strands are real tube geometry rather than camera-facing ribbons.
  A ribbon has no cross-section, never hides itself, and collapses to nothing wherever
  the curve happens to point at you — which is why they read as pictures pasted over
  the world instead of as objects. A tube is round from every angle, its near side
  occludes its far side, and the highlight moves when you do.
- Strands are shaded per fragment from a real geometric normal, and brighten toward
  the rim rather than falling off like a lit surface, because they are hot emitting
  material and not plastic pipe. An eight-sided tube no longer creases at every quad
  edge either.
- Every effect with something at its middle now draws it. Six of the seven had only
  their outer structure and nothing inside: jets emerging from empty air, field lines
  looping around a gap, a nebula with no star in it.
- Those central bodies are real spheres, not billboards — actual geometry with a true
  normal at every vertex, limb-darkened by the Eddington grey-atmosphere law so the edge
  sits at 0.4 of the middle, with a granulated surface that turns with the body. A flat
  quad has no limb, no rotation and no silhouette of its own, so it read as a white
  circle painted on the sky however carefully it was shaded.
- Gargantua's strongest lensing is finally visible. Rays bent far enough to leave the
  screen had no scene left to read and quietly faded back to whatever colour was already
  there, which meant the region just outside the photon ring — where light turns through
  the largest angles — bent nothing you could see. Those rays now look out at a
  volumetric star field along the direction gravity actually sent them. It is sampled by
  world direction, so it behaves as a fixed sky rather than sliding about with the
  camera, which is the only way lensed starlight reads as lensed. The `ring` tooltip style
  shows the same field, so the two agree by construction.
- Crab Nebula filaments are closed loops, so none of them has loose ends hanging in
  the middle of the shell. Closing them is not the same as making them circles —
  twenty-two great circles is a wireframe globe, which is the one thing the real
  remnant does not look like — so each loop weaves out of its own plane instead. The
  join is genuinely invisible rather than merely short: carrying a reference frame once
  around one of these loops turns it by as much as fifty degrees, and that turn is
  spread across every ring instead of being dumped into the closing segment.
- The visible rectangles and hard edges in the new effects are gone. They were not
  tiling artefacts. Raising the exposure left the falloffs ending well above zero at
  the quads' own boundaries, so what you could see was the edge of the quad itself.

### Gargantua

- It pulls. Creatures are dragged toward it rather than only damaged at range, and
  projectiles are pulled too; inside the capture radius their motion is taken over
  outright rather than nudged, so they go in instead of sailing past.

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
- Fixed effects leaking a shader colour into whatever drew next. Fourteen world renderers
  each carried their own copy of the same GL state snapshot, and the copies had drifted:
  seven put the shader colour back and seven did not. Since the central bodies set that
  colour's alpha as high as 2.0 to pick which layer to draw, anything rendering after one
  of the seven inherited it. There is now one snapshot, and it restores the colour that was
  actually there beforehand rather than assuming white.

### Presentation

- Spell icons for all nine new spells, drawn from the same geometry the effects use.
- Every new spell has a `_show` command that stands one up for inspection with a
  matching `_clear`: `/gargantua_show`, `/horseshoe_show`, `/quasar_show`,
  `/helix_show`, `/magnetar_show`, `/tde_show`, `/jet_show`, `/pinwheel_show` and
  `/crab_show`. Operator-only, and they do no damage.
- The Simplified Chinese text for the Cosmic Horseshoe was written in the classical
  register, with the 分/釐/毫 fractions that belong to the Literary Chinese file.
  It now reads as modern Chinese with percentages, like the rest of `zh_cn`.
- Animated text effects are built in rather than depended on, ported from the
  author's own dynamic-text mod, so the spell names move without adding a second
  download. Its toggles live in their own config file.
- Tooltips for this mod's items are styled per spell: the name animates, and every
  line under it takes that spell's accent colour. The lines below the name are not
  ours — level, rarity, cast time, mana, cooldown and school all come from Iron's
  Spells in its own orange, blue, grey and red, which no language file here can
  reach — so half the box used to clash with the other half.
- Seven optional tooltip styles, all off by default and all switchable in game with
  `/lightpollution frame <style>`, or set `tooltipStyle` in the client config to keep one.
  Every style is tinted to the spell's own accent, so a tooltip looks like the spell it
  describes.
  - `panel` — a rounded panel behind the normal layout, which vanilla still fills in.
  - `arcane` — takes rendering over: glow rays radiating from behind the box, a centred
    title, a rule under it.
  - `orbit` — the spell's own icon drifting behind the panel with ghost copies trailing it.
  - `astral` — a drifting field of thirty-two points, turning marks at the corners, and a
    band of light travelling under the title.
  - `ring` — a tilted ring that passes *through* the tooltip, with a star field inside the
    band. The far half is drawn, then the panel, then the near half.
  - `sigil` — two counter-rotating triangular bands behind the panel, forming a
    six-pointed figure that never settles.
  - `pinwheel` — no panel at all: each line becomes a spoke of a slowly turning pinwheel,
    every glyph placed individually with its own tilt and a wave running along the line.
  Every style carries a highlight that travels around the outline rather than pulsing in
  place. Adapted from ArcaneVortex with its author's permission, credited in
  `CREDITS.txt` — nine separate renderers there, which shared a vocabulary and mostly
  differed in which pieces they used.
- Damage from these spells can float above what it hits, in that spell's own colour. Off by
  default — turn it on with `/lightpollution damage on` or set `floatingDamage` in the client
  config. Damage on one target is accumulated for half a second and shown once as a total:
  these spells tick rather than landing single hits, and the Crab Nebula alone would otherwise
  put around forty separate numbers on one target in a single cast.
- Four spells now announce their own turning point, which were all completely silent
  before: Gargantua's horizon closing, the magnetar's magnetosphere letting go, a tidal
  disruption's fallback peaking, and the Crab remnant coming apart.

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
