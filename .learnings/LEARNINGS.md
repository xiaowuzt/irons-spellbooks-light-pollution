# Learnings

Corrections, insights, and knowledge gaps captured during development.

**Categories**: correction | insight | knowledge_gap | best_practice

---

## [LRN-20260823-004] insight

**Logged**: 2026-08-23T00:20:00+08:00
**Priority**: high
**Status**: resolved
**Area**: tests

### Summary
A sampler used in GLSL but not declared in the shader's `.json` silently binds to texture unit 0 instead of failing.

### Details
Minecraft 1.20.1's `ShaderInstance` builds `samplerNames` exclusively from the JSON (`ShaderInstance.java:395-409`), and `setSampler(name, tex)` is an unvalidated `samplerMap.put` (`:437-440`). A sampler that appears only in GLSL therefore never receives a `glUniform1i`, keeps its link-time default of 0, and reads whatever texture is on unit 0. Symmetrically, `getUniform()` returns null for a uniform absent from the JSON, so the project's null-guarded `set(...)` helpers no-op without a word.

This silently disabled two things at once. `LIGHT_TEMPORAL` read `BloomSampler` (undeclared) while Java bound `PreviousRadianceSampler` (a name the GLSL never declared), so temporal reprojection sampled the *current* frame and `mix(current, current, w)` accumulated nothing. `LIGHT_SPATIAL` read its blur radius from `MiscParams.z` (undeclared), which stayed 0, pinning all four progressively-widening iterations to radius 1.

Both defects are visible in `latest.log` as `could not find sampler/uniform named X` — but only for the *declared-and-unused* direction. The dangerous direction (used-but-undeclared) produces **no diagnostic at all** and must be found by diffing the JSON against the GLSL by hand.

### Suggested Action
After touching any shader JSON or its GLSL, run `runClient` and require `grep "could not find" latest.log` to be empty, then separately verify every `uniform`/`sampler` declaration in the GLSL section has a matching JSON entry. A clean load is necessary but not sufficient.

### Metadata
- Source: error
- Related Files: `kill_effect_post_common.glsl`, `gemini_kill_post_light_temporal.json`, `gemini_kill_post_light_spatial.json`, `SpellLightPostProcessor.java`
- Tags: forge, minecraft, glsl, shaders, uniforms, validation

---

## [LRN-20260823-003] correction

**Logged**: 2026-08-23T00:20:00+08:00
**Priority**: high
**Status**: resolved
**Area**: tests

### Summary
VanillaDI's `offset = fract(markerPosition)` must NOT be carried over into a world-anchored voxel volume.

### Details
Upstream's occupancy volume is *camera-relative*: cell indices are computed from `fragPos` (camera-relative) and `offset` supplies a fixed alignment reference so the lattice does not drift with the camera's fractional position. This port instead anchors the volume in world space (`floor(camera) - 32`) and reprojects the rolling cache by whole-block `originDelta`, which is strictly better — but it also kept `+ fract(cameraPosition)` in `VoxelOrigin`.

The result was that the entire 8x8x8-per-block subvoxel lattice slid a sub-block amount every frame while the mask was only recollected every third frame. Because the shadow test is a single deterministic ray against a binary mask, every pixel near a shadow boundary flipped lit/shadowed per frame. This presented as two separate-looking bugs — "shadows flicker constantly" and "light will not pass through a trapdoor's cutout holes" — that were one root cause: a hole only 1-2 subvoxels wide cannot stay open while its own boundaries slide.

### Suggested Action
Keep `VoxelOrigin` an exact integer world position. If a fractional term is ever reintroduced, the voxelize and lighting passes must agree on it within the same frame, and the recollection cadence must match the slide rate.

### Metadata
- Source: error
- Related Files: `SpellLightVoxelGrid.java`, `SpellLightPostProcessor.java`, `gemini_kill_post_light_voxelize.fsh`
- Tags: vanilladi, voxelization, temporal-stability, shaders

---

## [LRN-20260823-002] correction

**Logged**: 2026-08-23T00:20:00+08:00
**Priority**: high
**Status**: resolved
**Area**: tests

### Summary
Cutout holes in fences and trapdoors must come from the depth buffer; a `getLightBlock`-based override deletes them.

### Details
A trapdoor's four holes are texture alpha, not model geometry: cutout rendering discards those texels so they write no depth, which is precisely why a depth-derived occupancy mask can see them and `BlockState.getShape()` cannot. Measured from `oak_trapdoor.png`, each hole is exactly 3x3 texels (x in [3,6) and [10,13)); at the cache's 1/8-block resolution, subvoxel centres land at texels 3, 5 and 11, so every hole yields at least one fully cleared subvoxel column. The resolution is sufficient as shipped.

An attempt to satisfy "let light through trapdoors and fences" by uploading a CPU mask of blocks whose `BlockState#getLightBlock` is below maximum was wrong: clearing a whole block's occupancy removes the bars along with the holes, so the trapdoor cast no shadow at all instead of a lattice. The requirement means "light passes through the gaps", not "the block is ignored".

### Suggested Action
Do not reintroduce a per-block light-transmission override. If holes fail to appear, the fault is in the voxelizer's acceptance test, in traversal, or in the denoiser — use `/lightpollution_dump_voxel` to read the mask back and tell those apart.

### Metadata
- Source: user
- Related Files: `gemini_kill_post_light_voxelize.fsh`, `SpellLightClientCommands.java`
- Tags: vanilladi, cutout, voxelization, trapdoor, fence

---

## [LRN-20260823-001] insight

**Logged**: 2026-08-23T00:20:00+08:00
**Priority**: high
**Status**: resolved
**Area**: tests

### Summary
Screen-space bilateral weights constrain nothing across a flat floor; VanillaDI's world-space rejections are what preserve fine shadow detail.

### Details
Two of the port's filters dropped upstream's world-space constraints and kept only screen-space ones, which degenerate on a large flat surface.

`LIGHT_SPATIAL` weighted taps by `exp(-abs(sampleDepth - centerDepth) * 180.0)` and a normal dot. On a floor both stay near 1.0 — window-depth differences between neighbouring pixels are ~1e-4 — so the widest iteration became an unconstrained box blur that averaged away a trapdoor's 3/16 cutout once it projected to a few pixels. Upstream instead hard-rejects any tap more than 0.15 blocks from the centre *in world space* (`spatial.fsh`: `if (d > 0.15) continue;`) and offsets taps by `sqrt(Step)`, not `Step`.

The screen-space shadow fallback had the same shape of bug in the opposite direction. Upstream's `traceScreenSpaceRay` is a 0.5-block contact ray whose acceptance window `0.02 * (1.0 - depth)` closes as depth approaches 1, so it disables itself at range. The port's replacement marched the full distance to the light with tolerances that *grow* with distance, inventing blobby occluders on distant grazing floors whose own voxels were too coarse to veto the hit.

### Suggested Action
When porting a denoiser or a screen-space trace, check whether each rejection term is expressed in world units or screen units, and preserve the world-space ones. A threshold that loosens with distance is almost always a porting error.

### Metadata
- Source: user
- Related Files: `kill_effect_post_common.glsl`, `/tmp/vdiex/VanillaDI-main/assets/minecraft/shaders/program/spatial.fsh`, `temporal.fsh`
- Tags: vanilladi, denoising, shaders, screen-space

---

## [LRN-20260821-002] best_practice

**Logged**: 2026-08-21T03:41:00+08:00
**Priority**: high
**Status**: resolved
**Area**: tests

### Summary
When adding Minecraft post shaders, validate the complete `ShaderInstance` registration path, not only Java compilation.

### Details
The temporal/history work compiled in Java but initially crashed during client resource reload. A second launch also exposed optimized-away sampler/uniform declarations through runtime warnings. The fix was to compile the shader resources in an actual Forge client and remove unused JSON bindings.

### Suggested Action
After every new shader variant, run `runClient`, inspect `latest.log` for shader compile errors and missing sampler/uniform warnings, then stop the client cleanly before the next resource edit.

### Metadata
- Source: error
- Related Files: `GeminiKillEffectPostShaders.java`, `gemini_kill_post_light_temporal.json`, `gemini_kill_post_light_spatial.json`
- Tags: forge, minecraft, glsl, shaders, validation

---

## [LRN-20260820-001] best_practice

**Logged**: 2026-08-20T22:34:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: config

### Summary
Forge userdev runs using Iron's Spellbooks need SRG refmap remapping properties.

### Details
The migrated spell module loads its third-party mixin refmaps only when the run configuration points Mixin at ForgeGradle's generated `createSrgToMcp/output.srg` map.

### Suggested Action
Keep the three `mixin.env`/`GradleStart.srg.srg-mcp` properties in `minecraft.runs.configureEach`.

### Metadata
- Source: error
- Related Files: `build.gradle`
- Tags: forge, mixin, refmap, irons-spellbooks

---
