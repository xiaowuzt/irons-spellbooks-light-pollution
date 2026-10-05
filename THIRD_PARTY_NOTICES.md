# Third-party notices

This project contains original code and assets together with the adapted or
bundled material listed below. The project `LICENSE` applies only to original
Iron's Spells: Light Pollution code and assets. Each component keeps its own
license and attribution requirements.

## Gemini

The `Funeral Nova` and `Eclipse Severance` renderers and their GLSL shaders
port or adapt `KillEffect` and `SweepingAttackVFX` by AirZone-Team:

- Repository: <https://github.com/AirZone-Team/Gemini>
- Referenced post-processing revision: `25db876842c10c9d98dc4089e4f9873bade6d01a`
- License: GNU Lesser General Public License, version 2.1, only
  (`LGPL-2.1-only`)

The affected shader files retain source comments identifying the Gemini port.
The LGPL text is included in `LICENSE.txt`; this notice must remain with
redistributed source and binaries.

## CeliaClaire ShaderTest

The `Stargrave Singularity` black-hole renderer and shaders are adapted from
CeliaClaire's `ShaderTest` project under its MIT terms. The attribution in the
renderer and this notice must be preserved.

## Some of FX

Singularity's screen-space refraction, lightning, noise/core textures, and
sound effects derive from Some of FX by Pizuka:

- Repository: <https://github.com/YangMao-Minister/some_of_fx>
- License: MIT

The full notice is included in
`src/main/resources/assets/irons_spellbooks_light_pollution/textures/effect/SOME_OF_FX_LICENSE.txt`
and in the built jar.

## TextAnimator and text effect code

The base animated text effect mathematics is ported from Snownee's
`TextAnimator` under Apache License 2.0. The full applicable notice is in
`TEXTANIMATOR_LICENSE.txt`.

Additional small text-effect portions follow the MIT terms of TheSalts'
`Text_Effects` and Robert Penner's easing implementation as distributed by
Tween.js. Their attributions are retained in `CREDITS.txt` and in the source
comments.

## ArcaneVortex and Star Nest

Several tooltip, world-text, and Gargantua rendering portions are adapted from
ArcaneVortex by ErChien. ArcaneVortex remains All Rights Reserved. The original
author has expressly permitted this mod to use those portions; that permission
does not relicense the ArcaneVortex project or grant permission to redistribute
ArcaneVortex itself.

The volumetric star field is Kali's `Star Nest` shader from Shadertoy, used via
ArcaneVortex with permission and credited in `CREDITS.txt`.

## Gargantua With HDR Bloom (Event Horizon)

Event Horizon (`event_horizon` / 无光视界) adapts the shader source provided by
the project owner for **Gargantua With HDR Bloom**, by **sonicether**:

- Original: <https://www.shadertoy.com/view/lstSRS>
- Original publication date shown on the reference page: 2016-04-07
- Adapted material: bent-ray integration, accretion-disc and haze functions,
  noise helpers, HDR bloom/compositing ideas and the Image pass's colour curve.
- Affected files: `event_horizon_trace.fsh`, `event_horizon_bloom.fsh`,
  `event_horizon_composite.fsh`, their companion render pipeline, and the
  spell icon derived from a render of the adapted shaders.
- The supplied source credits its noise function to **iq (Inigo Quilez)**;
  that credit is retained in the adapted shader.

Changes for Minecraft include a world-space camera and fixed disc orientation,
scene-depth occlusion, background refraction, a separate HDR bloom pyramid,
bounded adaptive sampling and spell-only colour grading. The original external
Shadertoy channel textures are not bundled: local procedural noise and disc
modulation are used instead. No screenshot from the reference site is bundled.

**Permission status: unverified.** The supplied text has no clear licence grant,
and a licence for this specific shader has not been confirmed. Attribution is
not permission. This adaptation is not covered by the project's original-code
licence. Confirm the applicable upstream terms or obtain the author's permission
before publicly distributing the adapted code, icon or a binary containing them;
record the grant here once obtained.

## GR BH with volume accretion disk (Redshift Abyss)

Redshift Abyss (`redshift_abyss` / 赤移天渊) adapts the project-owner-supplied
source for **GR BH with volume accretion disk**, by **baopinsui**:

- Original: <https://www.shadertoy.com/view/4XcfR2>
- Publication date shown on the reference page: 2025-01-20.
- The page identifies it as a fork of **Black Hole test**. The supplied source
  credits its bloom/Image passes to **sonicether / Gargantua With HDR Bloom**
  (<https://www.shadertoy.com/view/lstSRS>).
- Adapted portions: Schwarzschild-style ray bending, Keplerian disk motion,
  volume density/noise, temperature/Kelvin colour, gravitational and Doppler
  shifts, and HDR bloom/filmic colour concepts. The periodic correlated value
  noise implementation follows the **iq (Inigo Quilez)** helper already credited
  for Event Horizon.
- Affected files: `redshift_abyss_trace.fsh`, `redshift_abyss_bloom.fsh`,
  `redshift_abyss_composite.fsh`, the companion rendering pipeline and the
  icon generated from a synthetic render of these adapted shaders.

Minecraft-specific changes use normalized Schwarzschild-radius coordinates,
world-camera/depth integration, slab-clipped bounded ray steps, finite optical
transport, periodic angular noise, world-space orientation, independent runtime
appearance settings and spell-only grading. Density detail is adjusted to work
without the original temporal feedback buffers. External Shadertoy textures,
reference screenshots and the supplied text file are not bundled in the runtime
JAR. A verbatim owner-supplied source copy was used for the local integration
audit; that audit copy is not bundled in the release and is not a claim of
redistribution permission.

**Permission status: unverified.** No clear licence grant appears in the supplied
text; a licence for this particular shader and all its upstream portions has not
been confirmed. Attribution is not permission. This adaptation is not covered by
the project's original-code licence. Confirm the applicable upstream terms or
obtain permission before distributing the adapted source, icon or a containing
binary; record the grant here once obtained.

## Forge/FML and dependencies

Forge/FML and MCP-related template material retain the notices and LGPL text in
`LICENSE.txt`. Iron's Spells 'n Spellbooks, Iron's Lib, Minecraft, and optional
JEI are runtime/build dependencies; they are not relicensed by this project.
Their own licenses and notices apply.


## Black-hole family: isolated R2, R4 and R5 adaptations (R6 code is not in the build)

The local source audit and acquisition evidence are kept outside the release
package. Shared Minecraft camera, depth,
parameter and entity plumbing is local integration code; the authors' rendering
cores remain separate. This does not relicense any upstream shader.

### R2 / R7 — Eric Bruneton

- Source: https://github.com/ebruneton/black_hole_shader
- Paper (principles): Real-time High-Quality Rendering of Non-Rotating Black Holes,
  arXiv:2010.08735, Eric Bruneton, 2020.
- The fetched Git tree SHA / raw-source revision token is
  `e72b3f293409893a6fa25528b29572c96fc57f57`; this audit does not label it a commit SHA.
- R2 is BSD-3-Clause. The complete upstream notice is bundled as
  `META-INF/licenses/black-hole-R2-BSD-3-Clause.txt`
  and copied to the local source audit only.
- Adapted files: `shaders/include/bh_r2_geodesic.glsl`,
  `shaders/core/schwarzschild_lens.fsh`, and `scripts/BlackHoleLutGenerator.java`.
  `textures/effect/black_hole/deflection.lut` and `inverse-radius.lut` are generated
  local data, not the upstream demo's images or star catalogues.
- The integration keeps Schwarzschild deflection/disc intersection lookup, but
  uses a finite-resolution LUT, a thin emissive calibration ring and screen-space
  Minecraft background reprojection. It does NOT reproduce the paper's full beam
  filtering/star catalogue, exact off-screen scene or relativistic gameplay.

### R4 — Xor / Blackhole

- Reference: https://fragcoord.xyz/s/bdf4dchf
- Public metadata attributes the shader to Xor and links
  https://x.com/XorDev/status/1897669357934608590.
- Retrieved Common and Main source passes are retained separately in the audit.
  Only this analytic ring core is adapted in `radiant_collapse.fsh`; explicit
  loop initialization and finite arithmetic replace undefined/singular cases.
- Permission UNVERIFIED. Public visibility and `source_hidden=false` establish
  source availability, not a licence grant. Confirm terms before distribution.

### R5 — owner-supplied Shadertoy section A (R6 code is not in the build)

- R5: https://www.shadertoy.com/view/WltSDM — A section of `shadertoy代码.txt`,
  adapted independently in `stasis_singularity.fsh` (Newtonian optical bending).
- R6: https://www.shadertoy.com/view/wltSD7 — B section of that same supplied file.
  Photon Corridor adapted this section, but that spell was removed from the build
  on 2026-10-04. No R6 shader, path class or command is compiled or packaged.
  Photon Corridor is only a pre-deletion record and is not shipped.
- Original author identities and licences were NOT verified from the supplied
  text. No author name or licence is invented. The marked source and each
  extracted section have separate hashes in the audit manifest.
- Permission UNVERIFIED for the R5 adaptation. R6 is no longer shipped;
  attribution is not permission.

The three remaining new spell icons are local procedural art, not copied from
reference screenshots.
The R1 icon retains the separately documented shader-derived permission caveat.
R3's DEV page and public article API returned 404; no R3 source or implementation
has been included. R7 is a principles reference, not an extra spell or a claimed
implementation of every feature in the paper. Do not publicly redistribute the
unverified adaptations (source or containing binaries) before resolving terms.
