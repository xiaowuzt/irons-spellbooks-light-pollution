# Errors

Command failures and integration errors.

---

## [ERR-20260823-001] funeral_nova_legacy_branch_red_wash

**Logged**: 2026-08-23T01:10:00+08:00
**Priority**: high
**Status**: pending
**Area**: tests

### Summary
Funeral Nova's post chain forces the `SCREEN_LIGHTING` legacy branch, whose `SceneSampler` is undeclared and therefore reads the depth texture — the whole frame becomes a red depth wash.

### Error
No exception and no log line. Visually: during Funeral Nova stages 3, 4, 5, 7, 8, 9 and 10 the frame is replaced by the depth buffer rendered as colour (saturated red, since a depth texture sampled through `sampler2D` yields `(d, 0, 0, 1)` and Minecraft's depth is ~0.99 across most of the screen). Every earlier pass's output is discarded and all later passes operate on that.

### Context
- `GeminiKillEffectPostProcessor` sets `LightDataParams` to `(1, 1, 0, 0)`, and `kill_effect_post_common.glsl`'s `SCREEN_LIGHTING` `main()` takes the `LightDataParams.z < 0.5` branch into `slLegacyScreenLighting()`.
- That function reads `SceneSampler`, `LightViewPos`, `LightColor` and `Center1`. All four are declared in the shared global uniform block, so the program links — but none is declared in `gemini_kill_post_screen_lighting.json`, so Minecraft never binds or uploads them (see LRN-20260823-004). `SceneSampler` keeps unit 0, which `DepthSampler` occupies.
- The legacy branch also depends on `InverseProjectionMat`, which `GeminiKillEffectPostProcessor` never uploads, so fixing only the JSON trades the red wash for still-wrong, shadowless lighting.
- Unrelated to the spell-light pipeline, which always uploads a non-zero light count and so never enters this branch. Found while auditing that pipeline; deliberately left untouched.

### Suggested Fix
Declare `SceneSampler` plus the `LightViewPos` / `LightColor` / `Center1` uniforms in `gemini_kill_post_screen_lighting.json` (append, so `DepthSampler` keeps unit 0), and upload `InverseProjectionMat` from `GeminiKillEffectPostProcessor` as well. Do not add `InverseViewProjectionMat` — it is declared in the GLSL but unused in that section, so it is stripped and would produce a fresh warning.

### Metadata
- Reproducible: yes
- Related Files: `GeminiKillEffectPostProcessor.java`, `gemini_kill_post_screen_lighting.json`, `kill_effect_post_common.glsl`
- Tags: forge, glsl, shaders, funeral-nova, uniforms

---

## [ERR-20260821-003] glsl150_bit_encoding

**Logged**: 2026-08-21T03:55:00+08:00
**Priority**: high
**Status**: resolved
**Area**: tests

### Summary
VanillaDI's IEEE depth bit helpers cannot compile in the Forge 1.20.1 GLSL 150 shader profile.

### Error
`global function uintBitsToFloat requires "#version 330" or later`

### Context
- Target: `D:\铁魔法：光污染`
- Variant: `gemini_kill_post_light_temporal`
- The shared common include is compiled as `#version 150`.

### Suggested Fix
Use the paired normalized RGBA depth packing already used by the Forge port; do not introduce GLSL 330-only bit functions into the shared include.

### Metadata
- Reproducible: yes
- Related Files: `src/main/resources/assets/irons_spellbooks_light_pollution/shaders/include/kill_effect_post_common.glsl`
- Tags: forge, glsl, shader-compatibility

---

## [ERR-20260821-002] temporal_glsl_reserved_identifier

**Logged**: 2026-08-21T03:37:00+08:00
**Priority**: high
**Status**: resolved
**Area**: tests

### Summary
The first client launch after adding temporal depth history failed because `packed` is reserved by the GLSL compiler used by Minecraft 1.20.1.

### Error
`abstract parameters not allowed in function definition "slUnpackDepth"`

### Context
- The new temporal shader declared `slUnpackDepth(vec4 packed)`.
- Forge's OpenGL 3.2 shader compiler rejected the identifier during resource reload.

### Suggested Fix
Avoid GLSL reserved words in helper parameters and locals; the parameter was renamed to `value` and the client reached resource loading successfully.

### Metadata
- Reproducible: yes
- Related Files: `src/main/resources/assets/irons_spellbooks_light_pollution/shaders/include/kill_effect_post_common.glsl`

---

## [ERR-20260820-001] runServer_mixin

**Logged**: 2026-08-20T22:34:00+08:00
**Priority**: medium
**Status**: pending
**Area**: config

### Summary
Standalone `runServer` reaches dependency loading but stops in the third-party Iron's Spellbooks mixin set.

### Error
`InvalidMixinException: @Shadow method m_21244_ ... was not located in LivingEntity`.

### Context
- Target: `D:\铁魔法：光污染`
- Forge: `47.4.22`
- Iron's Spellbooks: `1.20.1-3.16.1`
- GeckoLib, Curios, and Player Animator were present and their refmaps remapped successfully.

### Suggested Fix
Use the same full dependency/modpack combination as the source DLC runtime; the spell migration itself builds successfully.

### Metadata
- Reproducible: yes
- Related Files: `build.gradle`, `gradle.properties`

---
