# 铁魔法：光污染 / Iron's Spells: Light Pollution

Iron's Spells: Light Pollution is an Iron's Spells 'n Spellbooks addon for Minecraft 1.20.1 and Forge 47+. It adds 24 legendary-scale spells with animated astronomical scenes and dynamic coloured spell lighting.

## Requirements

- Minecraft 1.20.1
- Forge 47 or newer
- Iron's Spells 'n Spellbooks 1.20.1-3.16.1 or newer
- Iron's Lib 1.20.1-1.1.0 or newer

## Spells

The registry IDs below are also the names used by the server gameplay configuration:

| Name | Registry ID |
| --- | --- |
| Celestial Judgment / 苍穹裁决 | `celestial_judgment` |
| Stargrave Singularity / 葬星奇点 | `stargrave_singularity` |
| Eclipse Severance / 星蚀断界斩 | `eclipse_severance` |
| Funeral Nova / 终焉葬星 | `funeral_nova` |
| Chromatic Accretion / 虹蚀吸积 | `chromatic_accretion` |
| Starless / 熄星 | `starless` |
| Pyre Star (Constellation) / 焚星 | `constellation` |
| Silhouette / 逆光 | `silhouette` |
| Starfall / 星坠 | `starfall` |
| Sky Collapse / 天倾 | `sky_collapse` |
| Stellar Convergence / 星链天顶 | `stellar_convergence` |
| Second Sun / 第二个太阳 | `second_sun` |
| Singularity / 奇点 | `singularity` |
| Leviathan / 星界巨蛇 | `leviathan` |
| World Tree / 世界树 | `world_tree` |
| Gargantua / 卡冈图雅 | `gargantua` |
| Cosmic Horseshoe / 宇宙马蹄铁 | `cosmic_horseshoe` |
| Microquasar / 微类星体 | `microquasar` |
| Helix Nebula / 螺旋星云 | `helix_nebula` |
| Magnetar / 磁星 | `magnetar` |
| Tidal Disruption / 潮汐撕裂 | `tidal_disruption` |
| Quasar Jet / 类星体喷流 | `quasar_jet` |
| Wolf-Rayet Pinwheel / 沃夫-拉叶风车 | `pinwheel` |
| Crab Nebula / 蟹状星云 | `crab_nebula` |

World Tree is a sanctuary spell. Allies in its area are healed on a pulse and receive absorption; magic, projectile, and fire protection can each be enabled or disabled. Its roots and crown are visual guardians and do not attack.

## Configuration

Gameplay values are server-authoritative. After the first launch, edit:

`config/irons_spellbooks_light_pollution-server.toml`

The first five spells and World Tree use dedicated sections such as `celestialJudgment`, `stargraveSingularity`, `funeralNova`, `chromaticAccretion`, and `worldTree`. The other spells use `spells.<id>` sections with the common keys `cooldownSeconds`, `manaCost`, `castTimeTicks`, `castRange`, `lifetimeTicks`, `effectRadius`, `damageFraction`, `secondaryDamageFraction`, `damageIntervalTicks`, `phaseOneTick`, `phaseTwoTick`, `phaseThreeTick`, and `maxTargets`; `<id>` is camel-case (`skyCollapse`, `cosmicHorseshoe`, and so on). Dedicated sections also expose effect, timeline, damage, and protection values. World Tree exposes:

- `worldTree.healingFraction` (default `0.08`, maximum-health fraction per pulse)
- `worldTree.healingIntervalTicks` (default `20`, where 20 ticks = 1 second)
- `worldTree.absorptionHearts` (default `4.0`)
- `worldTree.protectMagic`, `worldTree.protectProjectile`, `worldTree.protectFire` (default `true`)

Funeral Nova also exposes `accretionDamageTick` and `collapseDamageTick`; these control
when its first two damage pulses resolve and stay synchronized with the client timeline.

Damage and healing fractions use decimal values (`0.08` means 8%). Forge validates numeric ranges and falls back to defaults for invalid values. Restart the server or reload the Forge config after editing.

If a server was created with an early development build, its generic spell values may still be
nested under `spells.spells`. Version 1.7.0 reports this legacy path at load time; copy those
values into the matching `spells.<spellId>` sections before removing the old entries.

Client lighting and presentation options are in:

`config/irons_spellbooks_light_pollution-client.toml`

The `enabled` switch controls screen-space spell lighting. `qualityPreset` (`LOW`, `MEDIUM`, `HIGH`,
`ULTRA`) applies a safe ceiling to light count and shadow quality; `maxLights` and `shadowSteps`
can further lower that ceiling. The renderer keeps the brightest active sources when the cap is hit.

## Building

Use the Gradle wrapper from a Java 17 environment:

```text
./gradlew build
```

The resulting jar is written to `build/libs/`.
