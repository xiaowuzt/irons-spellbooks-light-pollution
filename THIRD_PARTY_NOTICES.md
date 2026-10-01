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

## Forge/FML and dependencies

Forge/FML and MCP-related template material retain the notices and LGPL text in
`LICENSE.txt`. Iron's Spells 'n Spellbooks, Iron's Lib, Minecraft, and optional
JEI are runtime/build dependencies; they are not relicensed by this project.
Their own licenses and notices apply.
