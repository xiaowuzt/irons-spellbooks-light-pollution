package com.gang.lightpollution;

import java.util.LinkedHashMap;
import java.util.Map;

/** Concrete bilingual field semantics. Defaults and bounds come from Forge, never a duplicate table. */
public final class ConfigDescriptions {
    private static final Map<String, ConfigComments.Text> EXACT = new LinkedHashMap<>();
    private static final Map<String, ConfigComments.Text> SUFFIX = new LinkedHashMap<>();
    private static final Map<String, String> SPELL_NAMES = new LinkedHashMap<>();
    static {
        exact("redshiftAbyss.sourceTimeRate", "Physical simulation seconds per game second, multiplied by rotationSpeed for original gas drift and rotation. Uses game time with the entity seed phase; zero freezes flow.", "每游戏秒对应的物理模拟秒数，与 rotationSpeed 相乘控制原始气体流动及自转；使用带实体种子相位的游戏时间，0 冻结流动。");
        exact("redshiftAbyss.rotationSpeed", "Multiplier applied with sourceTimeRate to both original radial gas drift and inner-cloud rotation. 1 preserves source speed; zero freezes gas, not spell lifetime.", "与 sourceTimeRate 相乘，同时控制原始径向气体流动及内侧云团自转；1 为原始流速，0 冻结气体但不暂停法术生命周期。");
        exact("spells.helixNebula.effectRadius", "Reference size: size / 22 scales the shared expanding shell, swept hit thickness and collapse reach. A moving band, not a filled damage sphere.", "参考尺寸：此值 / 22 缩放共享扩张气壳、扫掠判定厚度及崩塌范围；运动环带不是实心伤害球。");
        exact("spells.leviathan.effectRadius", "Reference size: size / 28 scales the shared curved body and contact/bite radii. This is not a spherical damage radius.", "参考尺寸：此值 / 28 缩放共享曲线躯体及接触、啃咬判定半径；不是球形伤害半径。");
        exact("spells.singularity.effectRadius", "Reference size: size / 22 scales shared core/bolt geometry, the 20-block pull/crush radius and the 18-block burst radius.", "参考尺寸：此值 / 22 缩放共享核心及电弧几何、默认 20 格的吸引挤压半径与默认 18 格的爆炸半径。");
        exact("spells.stellarConvergence.effectRadius", "Reference size: size / 18 scales shared star positions, the 6-block gameplay column radius and 12-block final burst radius. Fixed decorative column/burst meshes are not scaled.", "参考尺寸：此值 / 18 缩放共享星点位置、默认 6 格的玩法光柱半径与默认 12 格的最终爆炸半径；固定装饰光柱与爆炸网格不缩放。");
        exact("spells.redshiftAbyss.vortexStrength", "Tangential attraction relative to radial pull; zero gives radial-only attraction. Does not affect the client gas animation.", "切向吸引相对径向拉力的倍率；0 表示纯径向吸引，不影响客户端气体动画。");
        exact("spells.redshiftAbyss.pullStrength", "Radial and tangential attraction multiplier; zero also skips drag. Final speed is still capped at 1.2 blocks/tick.", "径向和切向吸引倍率；0 同时跳过阻力。最终速度仍限制在每 tick 1.2 格。");
        exact("spells.eventHorizon.pullStrength", "Radial attraction multiplier on existing checks; zero also skips drag. Final speed is still capped at 1.2 blocks/tick.", "既有检查的径向吸引倍率；0 同时跳过阻力。最终速度仍限制在每 tick 1.2 格。");
        exact("spells.singularity.pullStrength", "Inward acceleration multiplier during charging; zero disables attraction, not crushing or detonation.", "充能期间向内加速度倍率；0 关闭吸引但不关闭挤压与爆炸伤害。");
        exact("spells.singularity.crushMaxDamageFraction", "Near-core damage fraction, interpolated from damageFraction by squared closeness. Set both to zero to disable crushing.", "近核心挤压伤害比例，按接近程度的平方从 damageFraction 插值；两项同时为 0 关闭挤压伤害。");
        exact("spells.gargantua.blastRadius", "Spherical radius of the one-shot detonation around the hovering hole; independent of its visual disk and attraction radius.", "悬浮黑洞一次性爆炸的球形判定半径，与视觉吸积盘及吸引范围独立。");
        exact("spells.gargantua.pullStrength", "Creature/projectile attraction multiplier before existing speed/capture caps. Zero disables pull, not tidal damage or horizon kills.", "生物及投射物吸引倍率，仍受既有速度与捕获上限限制；0 关闭拉力但不关闭潮汐伤害与视界内秒杀。");
        exact("spells.gargantua.tidalMaxDamageFraction", "Near-horizon damage fraction of target maximum health, interpolated from damageFraction by squared closeness. The horizon kill is independent.", "近视界伤害的目标最大生命值比例，按接近程度的平方从 damageFraction 插值；视界内秒杀独立生效。");
        exact("enabled", "Enable this mod's spell light propagation; spell entities and their gameplay remain active.", "启用本模组法术的动态光照传播；关闭后法术实体及玩法仍然有效。");
        exact("maxLights", "Maximum light emitters submitted to the spell-light pass, also capped by the quality preset and adaptive controller.", "提交给法术光照阶段的最大发光源数量，还受画质预设和自适应控制器限制。");
        exact("shadowSteps", "Maximum samples along each spell-light shadow ray, also capped by the selected quality level.", "每条法术光照阴影射线的最大采样步数，还受所选画质级别限制。");
        exact("qualityPreset", "Static quality ceiling for lights, shadow samples and effect detail: LOW, MEDIUM, HIGH or ULTRA.", "光源数、阴影采样和效果细节的静态画质上限：LOW、MEDIUM、HIGH 或 ULTRA。");
        exact("adaptiveMode", "Adaptive client rendering policy. OFF keeps the quality ceiling; automatic modes reduce presentation load without changing server gameplay.", "客户端自适应渲染策略；OFF 保持画质上限，自动模式降低表现开销但不改变服务器玩法。");
        exact("adaptiveTargetFps", "Frame-rate target for the adaptive controller; not an FPS limiter.", "自适应画质控制器的目标帧率，不是帧率限制器。");
        exact("adaptiveMinimumQuality", "Lowest automatic quality level; qualityPreset is still the ceiling.", "自动降级允许的最低画质，qualityPreset 仍是上限。");
        exact("tooltipStyle", "Treatment of this mod's tooltips: vanilla box, panel, arcane, orbit, astral, ring, sigil or pinwheel. /lightpollution frame changes the runtime selection.", "本模组提示框样式：原版、面板、奥术、轨道、星尘、圆环、符印或风车；/lightpollution frame 可改变运行时选择。");
        exact("floatingDamage", "Draw floating numbers for this mod's damage. Presentation only; /lightpollution damage changes the runtime selection.", "显示本模组伤害的飘字，仅影响表现；/lightpollution damage 可改变运行时选择。");
        exact("cinematicFlashStrength", "Strength of local impact glow accents, not a fullscreen flash; zero disables these accents.", "局部命中辉光强度，不是全屏闪光；设为 0 关闭这些点缀。");
        exact("localDistortionStrength", "Strength of local heat haze and shock refraction; zero disables distortion without hiding spell geometry.", "局部热浪与冲击折射强度；设为 0 关闭扭曲但保留法术几何。");
        exact("eventHorizon", "Client appearance of Event Horizon; gameplay is in spells.eventHorizon in the server config.", "无光视界的客户端外观；玩法参数位于服务端配置的 spells.eventHorizon。");
        exact("redshiftAbyss", "Client appearance of Redshift Abyss; gameplay is in spells.redshiftAbyss in the server config.", "赤移天渊的客户端外观；玩法参数位于服务端配置的 spells.redshiftAbyss。");
        exact("server.maxActiveSpellEntities", "Maximum active spell anchor entities per server dimension; further casts are refused at the limit.", "每个服务器维度允许的法术锚点实体总数上限；达到上限后拒绝新施法。");
        exact("server.maxEntitiesPerSpell", "Maximum active anchor entities of one spell type per server dimension.", "每个服务器维度中同一种法术允许的活动锚点实体上限。");
        exact("server.targetScanLimit", "Shared upper bound on living targets processed by one spell query; spell-specific limits can only reduce it.", "单次法术查询处理的生物目标总上限；每法术目标上限只能进一步收紧。");
        exact("blackHole.bhTimeScale", "Stasis local movement and its own pulse-rate multiplier; never changes world TPS or third-party AI clocks. Snapshotted on cast.", "静滞奇点的局部运动与自身伤害脉冲速率倍率；绝不改变世界 TPS 或其他模组 AI 时钟。施法时保存快照。");
        exact("blackHole.bhDiskTiltDegrees", "Tilt of new black-hole disk normals from world +Y; snapshotted on cast.", "新黑洞盘法向相对世界 +Y 轴的倾角，施法时保存快照。");
        exact("blackHole.bhAbsorbItems", "Allow Stasis to absorb only item stacks without custom NBT and listed in black_hole_absorbable; whitelist checks remain mandatory.", "允许静滞奇点吸收没有自定义 NBT 且列入 black_hole_absorbable 标签的物品堆；白名单检查始终保留。");
        exact("blackHole.bhBreakBlocks", "Opt-in Stasis block absorption. Still requires a player owner, mobGriefing, a loaded chunk, black_hole_fragile tag, no block entity and an uncancelled Forge break event.", "显式开启静滞奇点方块吸收。仍须满足玩家施法者、mobGriefing、区块已加载、black_hole_fragile 标签、无方块实体及 Forge 破坏事件未被取消。");
        exact("adaptiveDecorationBudget", "Adaptively reduce optional server presentation under sustained tick load; never changes damage, targets, attraction, charge counters or spell timing.", "在服务器持续卡顿时自适应减少可选表现；不改变伤害、目标、引力、充能计数或法术时序。");
        exact("particleBurstsPerTick", "Maximum decorative projectile-absorption particle broadcasts per server tick when the decoration budget is enabled.", "启用表现预算时，每服务器 tick 的投射物吸收装饰粒子广播上限。");
        exact("damageTextsPerTick", "Maximum floating damage-number broadcasts per server tick when budgeting is enabled; health changes are unaffected.", "启用表现预算时，每服务器 tick 的伤害飘字广播上限，不影响生命值变化。");
        exact("diagnosticsPerTick", "Maximum optional diagnostic messages per server tick when the decoration budget is enabled.", "启用表现预算时，每服务器 tick 的可选诊断消息上限。");
        exact("bhMode", "Black-hole rendering path: AUTO selects a supported path; HIGH, LOW or PARTICLES explicitly select quality/fallback.", "黑洞渲染路径：AUTO 自动选择可用路径，HIGH、LOW 或 PARTICLES 显式选择画质或粒子回退。");
        exact("bhDebugMode", "Black-hole diagnostic view: 0 final image, 1 normals, 2 steps/deflection, 3 depth/support, 4 calibration grid or two-dimensional reference.", "黑洞诊断视图：0 最终画面、1 法向、2 步数或偏折、3 深度或支持状况、4 校准网格或二维参考。");
        exact("bhExposure", "Exposure multiplier of the shared black-hole compositor; affects brightness, not server damage.", "共享黑洞合成器的曝光倍率；影响亮度，不影响服务器伤害。");
        exact("bhDopplerStrength", "Approaching/receding disk asymmetry multiplier for the shared black-hole renderer.", "共享黑洞渲染器中盘面接近侧与远离侧的多普勒非对称倍率。");
        exact("bhMaxVisible", "Maximum simultaneously rendered black-hole instances; extra instances still exist and affect gameplay.", "同时绘制的黑洞实例数量上限；未绘制的实例仍存在并影响玩法。");
        exact("celestialJudgment.damageFraction", "Impact damage as a fraction of target maximum health; 0 disables this damage channel.", "苍穹裁决：命中按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("stargraveSingularity.damageFraction", "Collapse damage as a fraction of target maximum health; 0 disables this damage channel.", "葬星奇点：崩塌按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.starless.damageFraction", "Starvation hit damage as a fraction of target maximum health; 0 disables this damage channel.", "熄星：饥暗阶段命中按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.constellation.damageFraction", "Minimum outer-edge burn per pulse damage as a fraction of target maximum health; 0 disables this damage channel.", "焚星：每次灼烧脉冲的外缘最小伤害按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.silhouette.damageFraction", "Shadow burn per pulse damage as a fraction of target maximum health; 0 disables this damage channel.", "逆光：每次阴影灼烧脉冲按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.starfall.damageFraction", "Ordinary meteor impact damage as a fraction of target maximum health; 0 disables this damage channel.", "星坠：普通陨星冲击按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.skyCollapse.damageFraction", "Ordinary falling-star impact damage as a fraction of target maximum health; 0 disables this damage channel.", "天倾：普通坠星冲击按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.stellarConvergence.damageFraction", "Orbital-beam pulse damage as a fraction of target maximum health; 0 disables this damage channel.", "星链天顶：轨道光束脉冲按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.secondSun.damageFraction", "Solar heat pulse damage as a fraction of target maximum health; 0 disables this damage channel.", "第二个太阳：恒星热浪脉冲按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.singularity.damageFraction", "Outer-edge crush damage fraction. Interpolated toward crushMaxDamageFraction near the core; set both to zero to disable crushing.", "外缘挤压伤害比例，接近核心时向 crushMaxDamageFraction 插值；两项同时为 0 才关闭挤压伤害。");
        exact("spells.leviathan.damageFraction", "Serpent-body contact pulse damage as a fraction of target maximum health; 0 disables this damage channel.", "星界巨蛇：巨蛇躯体接触脉冲按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.eventHorizon.damageFraction", "Accretion pulse damage as a fraction of target maximum health; 0 disables this damage channel.", "无光视界：吸积脉冲按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.redshiftAbyss.damageFraction", "Accretion pulse damage as a fraction of target maximum health; 0 disables this damage channel.", "赤移天渊：吸积脉冲按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.stasisSingularity.damageFraction", "Outer stasis pulse damage as a fraction of target maximum health; 0 disables this damage channel.", "静滞奇点：静滞区外层脉冲按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.gargantua.damageFraction", "Outer-edge tidal shear damage fraction of target maximum health. Zero does not disable the separate near-horizon fraction or the fixed horizon kill.", "外缘潮汐剪切伤害的目标最大生命值比例；0 不关闭独立的近视界伤害与固定视界内秒杀。");
        exact("spells.cosmicHorseshoe.damageFraction", "Einstein-ring pulse damage as a fraction of target maximum health; 0 disables this damage channel.", "宇宙马蹄铁：爱因斯坦环脉冲按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.microquasar.damageFraction", "Precessing-jet sweep damage as a fraction of target maximum health; 0 disables this damage channel.", "微类星体：进动喷流扫掠按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.helixNebula.damageFraction", "Expanding shell passage damage as a fraction of target maximum health; 0 disables this damage channel.", "螺旋星云：膨胀壳层通过按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.magnetar.damageFraction", "Magnetospheric contact pulse damage as a fraction of target maximum health; 0 disables this damage channel.", "磁星：磁层接触脉冲按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.tidalDisruption.damageFraction", "Tidal-debris stream contact damage as a fraction of target maximum health; 0 disables this damage channel.", "潮汐撕裂：潮汐碎屑流接触按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.quasarJet.damageFraction", "Jet/lobe contact pulse damage as a fraction of target maximum health; 0 disables this damage channel.", "类星体喷流：喷流或射电瓣接触脉冲按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.pinwheel.damageFraction", "Spiral dust-arm contact damage as a fraction of target maximum health; 0 disables this damage channel.", "沃夫-拉叶风车：螺旋尘埃臂接触按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.crabNebula.damageFraction", "Nebula filament contact damage as a fraction of target maximum health; 0 disables this damage channel.", "蟹状星云：星云丝状结构接触按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.schwarzschildLens.damageFraction", "Unused optical-only channel (not exposed in the final schema) damage as a fraction of target maximum health; 0 disables this damage channel.", "施瓦西镜界：纯视觉法术未使用通道（最终配置不提供）按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.radiantCollapse.damageFraction", "Unused pre-collapse channel (not exposed in the final schema) damage as a fraction of target maximum health; 0 disables this damage channel.", "辉环崩解：未使用的预崩解通道（最终配置不提供）按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.starless.secondaryDamageFraction", "Release explosion damage as a fraction of target maximum health; 0 disables this damage channel.", "熄星：释放爆发按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.constellation.secondaryDamageFraction", "Maximum near-centre burn per pulse damage as a fraction of target maximum health; 0 disables this damage channel.", "焚星：每次灼烧脉冲的近心最大伤害按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.starfall.secondaryDamageFraction", "Final meteor impact damage as a fraction of target maximum health; 0 disables this damage channel.", "星坠：最终陨星冲击按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.skyCollapse.secondaryDamageFraction", "Final falling-star impact damage as a fraction of target maximum health; 0 disables this damage channel.", "天倾：最终坠星冲击按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.stellarConvergence.secondaryDamageFraction", "Convergence burst damage as a fraction of target maximum health; 0 disables this damage channel.", "星链天顶：汇聚爆发按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.secondSun.secondaryDamageFraction", "Final nova damage as a fraction of target maximum health; 0 disables this damage channel.", "第二个太阳：最终新星爆发按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.singularity.secondaryDamageFraction", "Final singularity blast damage as a fraction of target maximum health; 0 disables this damage channel.", "奇点：最终奇点爆发按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.leviathan.secondaryDamageFraction", "Final serpent bite damage as a fraction of target maximum health; 0 disables this damage channel.", "星界巨蛇：最终蛇噬按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.eventHorizon.secondaryDamageFraction", "One-shot closing collapse damage as a fraction of target maximum health; 0 disables this damage channel.", "无光视界：闭合时的一次性崩塌按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.redshiftAbyss.secondaryDamageFraction", "One-shot closing collapse damage as a fraction of target maximum health; 0 disables this damage channel.", "赤移天渊：闭合时的一次性崩塌按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.stasisSingularity.secondaryDamageFraction", "Inner-horizon pulse damage as a fraction of target maximum health; 0 disables this damage channel.", "静滞奇点：内层视界脉冲按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.radiantCollapse.secondaryDamageFraction", "Collapsing swept spherical shell, once per target damage as a fraction of target maximum health; 0 disables this damage channel.", "辉环崩解：崩解时扫过的球形壳层，每个目标仅一次按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.schwarzschildLens.secondaryDamageFraction", "Unused optical-only channel damage as a fraction of target maximum health; 0 disables this damage channel.", "施瓦西镜界：纯视觉未使用通道按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.gargantua.secondaryDamageFraction", "Unused duplicate blast channel damage as a fraction of target maximum health; 0 disables this damage channel.", "卡冈图雅：未使用的重复爆炸通道按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.cosmicHorseshoe.secondaryDamageFraction", "Terminal Einstein ring damage as a fraction of target maximum health; 0 disables this damage channel.", "宇宙马蹄铁：最终爱因斯坦环按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.microquasar.secondaryDamageFraction", "Terminal jet sweep damage as a fraction of target maximum health; 0 disables this damage channel.", "微类星体：终结喷流扫掠按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.helixNebula.secondaryDamageFraction", "Central white-dwarf collapse damage as a fraction of target maximum health; 0 disables this damage channel.", "螺旋星云：中心白矮星崩塌按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.magnetar.secondaryDamageFraction", "Giant magnetic flare damage as a fraction of target maximum health; 0 disables this damage channel.", "磁星：巨型磁场耀发按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.tidalDisruption.secondaryDamageFraction", "Accretion flare damage as a fraction of target maximum health; 0 disables this damage channel.", "潮汐撕裂：吸积耀发按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.quasarJet.secondaryDamageFraction", "Terminal hotspot damage as a fraction of target maximum health; 0 disables this damage channel.", "类星体喷流：最终热点爆发按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.pinwheel.secondaryDamageFraction", "Final stellar flare damage as a fraction of target maximum health; 0 disables this damage channel.", "沃夫-拉叶风车：最终恒星耀发按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.crabNebula.secondaryDamageFraction", "Terminal pulsar burst damage as a fraction of target maximum health; 0 disables this damage channel.", "蟹状星云：最终脉冲星爆发按目标最大生命值比例造成的伤害；设为 0 关闭此伤害通道。");
        exact("spells.starless.phaseOneTick", "Starvation hit / fully formed void at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "熄星：从生成计时，在此 tick 饥暗命中或空洞完全形成。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.starless.phaseTwoTick", "Release buildup at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "熄星：从生成计时，在此 tick 释放蓄势。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.starless.phaseThreeTick", "Release hit at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "熄星：从生成计时，在此 tick 释放命中。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.silhouette.phaseOneTick", "Light-inversion ramp duration at channel start and after release, clamped to at least one tick before expiry.", "引导开始与结束时光暗反转渐变的 tick 数；至少 1 tick，并限制在消失之前。");
        exact("spells.skyCollapse.phaseOneTick", "Fixed shared impact schedule (not configurable) at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "天倾：从生成计时，在此 tick 固定共享冲击时序（不可配）。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.skyCollapse.phaseTwoTick", "Fixed shared impact schedule (not configurable) at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "天倾：从生成计时，在此 tick 固定共享冲击时序（不可配）。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.skyCollapse.phaseThreeTick", "Fixed shared impact schedule (not configurable) at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "天倾：从生成计时，在此 tick 固定共享冲击时序（不可配）。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.leviathan.phaseTwoTick", "Final bite at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "星界巨蛇：从生成计时，在此 tick 最终蛇噬。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.gargantua.phaseOneTick", "End of opening tear at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "卡冈图雅：从生成计时，在此 tick 开裂结束。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.gargantua.phaseTwoTick", "Start of critical contraction at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "卡冈图雅：从生成计时，在此 tick 临界收缩开始。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.gargantua.phaseThreeTick", "Detonation at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "卡冈图雅：从生成计时，在此 tick 引爆。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.eventHorizon.phaseOneTick", "End of opening at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "无光视界：从生成计时，在此 tick 展开完成。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.eventHorizon.phaseTwoTick", "Closing collapse at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "无光视界：从生成计时，在此 tick 闭合崩塌。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.redshiftAbyss.phaseOneTick", "End of opening at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "赤移天渊：从生成计时，在此 tick 展开完成。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.redshiftAbyss.phaseTwoTick", "Closing collapse at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "赤移天渊：从生成计时，在此 tick 闭合崩塌。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.schwarzschildLens.phaseOneTick", "End of opening at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "施瓦西镜界：从生成计时，在此 tick 展开完成。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.schwarzschildLens.phaseTwoTick", "Start of closing at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "施瓦西镜界：从生成计时，在此 tick 开始闭合。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.radiantCollapse.phaseOneTick", "End of opening at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "辉环崩解：从生成计时，在此 tick 展开完成。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.radiantCollapse.phaseTwoTick", "Start of swept-shell collapse at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "辉环崩解：从生成计时，在此 tick 扫掠球壳开始崩解。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.stasisSingularity.phaseOneTick", "End of opening at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "静滞奇点：从生成计时，在此 tick 展开完成。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.stasisSingularity.phaseTwoTick", "Start of closing at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "静滞奇点：从生成计时，在此 tick 开始闭合。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.stellarConvergence.phaseOneTick", "First beam pulses at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "星链天顶：从生成计时，在此 tick 开始光束脉冲。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.stellarConvergence.phaseTwoTick", "Convergence burst at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "星链天顶：从生成计时，在此 tick 汇聚爆发。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.secondSun.phaseOneTick", "Start of heat pulses at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "第二个太阳：从生成计时，在此 tick 开始热浪脉冲。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.secondSun.phaseTwoTick", "Nova explosion at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "第二个太阳：从生成计时，在此 tick 新星爆发。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.singularity.phaseOneTick", "Start of accretion at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "奇点：从生成计时，在此 tick 开始吸积。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.singularity.phaseTwoTick", "Final blast at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "奇点：从生成计时，在此 tick 最终爆发。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.cosmicHorseshoe.phaseOneTick", "Ring formation complete at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "宇宙马蹄铁：从生成计时，在此 tick 圆环形成完毕。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.cosmicHorseshoe.phaseTwoTick", "Terminal ring pulse at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "宇宙马蹄铁：从生成计时，在此 tick 终结圆环脉冲。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.microquasar.phaseOneTick", "Formation complete / sweeping jets start at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "微类星体：从生成计时，在此 tick 形成完毕并开始喷流扫掠。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.microquasar.phaseTwoTick", "Terminal sweep at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "微类星体：从生成计时，在此 tick 终结扫掠。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.helixNebula.phaseOneTick", "Shell damage window opens at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "螺旋星云：从生成计时，在此 tick 壳层伤害窗口开启。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.helixNebula.phaseTwoTick", "White-dwarf collapse at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "螺旋星云：从生成计时，在此 tick 白矮星崩塌。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.magnetar.phaseOneTick", "Magnetosphere formation complete at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "磁星：从生成计时，在此 tick 磁层形成完毕。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.magnetar.phaseTwoTick", "Giant flare at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "磁星：从生成计时，在此 tick 巨型耀发。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.tidalDisruption.phaseOneTick", "Debris-stream damage starts at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "潮汐撕裂：从生成计时，在此 tick 开始碎屑流伤害。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.tidalDisruption.phaseTwoTick", "Debris fades at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "潮汐撕裂：从生成计时，在此 tick 碎屑开始淡出。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.tidalDisruption.phaseThreeTick", "Accretion flare at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "潮汐撕裂：从生成计时，在此 tick 吸积耀发。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.quasarJet.phaseOneTick", "Jet formation complete at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "类星体喷流：从生成计时，在此 tick 喷流形成完毕。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.quasarJet.phaseTwoTick", "Hotspot explosion at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "类星体喷流：从生成计时，在此 tick 热点爆发。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.pinwheel.phaseOneTick", "Dust-arm contact begins at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "沃夫-拉叶风车：从生成计时，在此 tick 尘埃臂接触开始。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.pinwheel.phaseTwoTick", "Terminal stellar flare at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "沃夫-拉叶风车：从生成计时，在此 tick 终结恒星耀发。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.crabNebula.phaseOneTick", "Nebula formation complete at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "蟹状星云：从生成计时，在此 tick 星云形成完毕。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("spells.crabNebula.phaseTwoTick", "Terminal pulsar burst at this tick after spawn. Active phase boundaries are clamped into ordered slots before expiry.", "蟹状星云：从生成计时，在此 tick 终结脉冲星爆发。有效阶段边界会限制为消失前有序且互不重叠的时刻。");
        exact("funeralNova.blackHoleStartTick", "Funeral Nova black-hole appearance tick after spawn; clamped before expiry and visual phases remain ordered.", "终焉葬星：从生成计时的黑洞开始显现时刻；限制在消失之前，视觉阶段保持有序。");
        exact("funeralNova.accretionStartTick", "Funeral Nova accretion tick after spawn; clamped before expiry and visual phases remain ordered.", "终焉葬星：从生成计时的吸积开始时刻；限制在消失之前，视觉阶段保持有序。");
        exact("funeralNova.collapseStartTick", "Funeral Nova contraction tick after spawn; clamped before expiry and visual phases remain ordered.", "终焉葬星：从生成计时的坍缩开始时刻；限制在消失之前，视觉阶段保持有序。");
        exact("funeralNova.voidStartTick", "Funeral Nova void tick after spawn; clamped before expiry and visual phases remain ordered.", "终焉葬星：从生成计时的虚空阶段开始时刻；限制在消失之前，视觉阶段保持有序。");
        exact("funeralNova.flashStartTick", "Funeral Nova flash tick after spawn; clamped before expiry and visual phases remain ordered.", "终焉葬星：从生成计时的闪光阶段开始时刻；限制在消失之前，视觉阶段保持有序。");
        exact("funeralNova.hypernovaStartTick", "Funeral Nova hypernova tick after spawn; clamped before expiry and visual phases remain ordered.", "终焉葬星：从生成计时的超新星爆发开始时刻；限制在消失之前，视觉阶段保持有序。");
        exact("funeralNova.afterglowStartTick", "Funeral Nova afterglow tick after spawn; clamped before expiry and visual phases remain ordered.", "终焉葬星：从生成计时的余辉开始时刻；限制在消失之前，视觉阶段保持有序。");
        exact("funeralNova.fadeOutStartTick", "Funeral Nova fade-out tick after spawn; clamped before expiry and visual phases remain ordered.", "终焉葬星：从生成计时的淡出开始时刻；限制在消失之前，视觉阶段保持有序。");
        exact("funeralNova.accretionDamageTick", "Funeral Nova one-shot accretion damage tick after spawn; clamped before expiry and visual phases remain ordered.", "终焉葬星：从生成计时的一次性吸积伤害结算时刻；限制在消失之前，视觉阶段保持有序。");
        exact("funeralNova.collapseDamageTick", "Funeral Nova one-shot collapse damage tick after spawn; clamped before expiry and visual phases remain ordered.", "终焉葬星：从生成计时的一次性坍缩伤害结算时刻；限制在消失之前，视觉阶段保持有序。");
        exact("chromaticAccretion.formationEndTick", "Chromatic Accretion formation end tick after spawn. Formation, four distinct pulses, collapse and expiry are ordered even for conflicting settings.", "虹蚀吸积：从生成计时的形成结束时刻。即使设置冲突，也会保证形成、四次独立脉冲、崩塌、消失依次发生。");
        exact("chromaticAccretion.collapseTick", "Chromatic Accretion final collapse tick after spawn. Formation, four distinct pulses, collapse and expiry are ordered even for conflicting settings.", "虹蚀吸积：从生成计时的最终崩塌时刻。即使设置冲突，也会保证形成、四次独立脉冲、崩塌、消失依次发生。");
        exact("chromaticAccretion.pulseOneTick", "Chromatic Accretion first pulse tick after spawn. Formation, four distinct pulses, collapse and expiry are ordered even for conflicting settings.", "虹蚀吸积：从生成计时的第一次脉冲时刻。即使设置冲突，也会保证形成、四次独立脉冲、崩塌、消失依次发生。");
        exact("chromaticAccretion.pulseTwoTick", "Chromatic Accretion second pulse tick after spawn. Formation, four distinct pulses, collapse and expiry are ordered even for conflicting settings.", "虹蚀吸积：从生成计时的第二次脉冲时刻。即使设置冲突，也会保证形成、四次独立脉冲、崩塌、消失依次发生。");
        exact("chromaticAccretion.pulseThreeTick", "Chromatic Accretion third pulse tick after spawn. Formation, four distinct pulses, collapse and expiry are ordered even for conflicting settings.", "虹蚀吸积：从生成计时的第三次脉冲时刻。即使设置冲突，也会保证形成、四次独立脉冲、崩塌、消失依次发生。");
        exact("chromaticAccretion.pulseFourTick", "Chromatic Accretion fourth pulse tick after spawn. Formation, four distinct pulses, collapse and expiry are ordered even for conflicting settings.", "虹蚀吸积：从生成计时的第四次脉冲时刻。即使设置冲突，也会保证形成、四次独立脉冲、崩塌、消失依次发生。");
        exact("effects.enabled", "Render dynamic text effects; when off, control codes are still removed.", "渲染动态文本效果；关闭后仍移除控制码。");
        exact("effects.rainbowEnabled", "Enable the &p flowing rainbow gradient.", "启用 &p 流动彩虹渐变。");
        exact("effects.rainbowSpeed", "Hue cycles per second for &p and gradient effects.", "&p 和渐变效果每秒流动的色相循环数。");
        exact("effects.glitchEnabled", "Enable &g flicker and broken afterimages; the main glyphs stay still.", "启用 &g 闪烁与破碎残影，主字形保持静止。");
        exact("effects.glitchFrequency", "Glitch-state update attempts per second for &g, &h, &u and &m.", "&g、&h、&u 与 &m 每秒尝试更新故障状态的次数。");
        exact("effects.cyberEnabled", "Enable &h cyber glitches, red/cyan separation and pixel afterimages.", "启用 &h 赛博故障、红青分色与像素残影。");
        exact("effects.magicEnabled", "Enable &q dark-magic purple/cyan glow.", "启用 &q 暗黑魔法紫青色辉光。");
        exact("effects.holographicEnabled", "Enable &y holographic, iridescent metallic highlights.", "启用 &y 全息镭射和幻彩金属高光。");
        exact("effects.energyBarEnabled", "Enable &s translucent cyan energy-bar text.", "启用 &s 半透明青色能量条文本。");
        exact("effects.lavaEnabled", "Enable &t glowing lava/plasma text.", "启用 &t 熔岩或等离子发光文本。");
        exact("effects.parchmentEnabled", "Enable &v parchment spellbook gradients and cyan accents.", "启用 &v 羊皮纸法术书渐变与青色点缀。");
        exact("effects.redSpeedNeonEnabled", "Enable &u red glow, speed lines, dark stripes and glitches.", "启用 &u 红色发光、速度线、暗色条纹与故障效果。");
        exact("effects.synthwaveNeonEnabled", "Enable &m cyan/magenta 3D neon, pixel edges, glow and VHS glitches.", "启用 &m 青紫三维霓虹、像素边缘、辉光与 VHS 故障。");
        exact("performance.refreshIntervalMs", "Dynamic text animation refresh period; lower values update more often and cost more CPU.", "动态文本动画刷新间隔；越小更新越频繁，CPU 开销越大。");
        exact("performance.maxTextLength", "Maximum visible characters receiving dynamic effects per text run; the remainder stays static, not truncated.", "每段文本参与动态效果的最大可见字符数；其余部分保持静态，不会截断。");
        exact("performance.compatibleScreens", "Screen-class-name fragments permitting dynamic effects; \"*\" means all, \"hud\" means HUD text with no open screen.", "允许动态效果的界面类名片段；\"*\" 表示全部，\"hud\" 表示未打开界面时的 HUD 文本。");
        SUFFIX.put("cooldownSeconds", new ConfigComments.Text("Cooldown after casting.", "施法后的冷却时间。"));
        SUFFIX.put("manaCost", new ConfigComments.Text("Mana spent for this cast.", "每次施法消耗的法力。"));
        SUFFIX.put("castTimeTicks", new ConfigComments.Text("Casting/channel preparation duration; 20 ticks = one game second.", "施法准备时长；20 tick 为一游戏秒。"));
        SUFFIX.put("castRange", new ConfigComments.Text("Maximum distance used to select the cast location or target.", "选择施法位置或目标时允许的最大距离。"));
        SUFFIX.put("targetRange", new ConfigComments.Text("Maximum target-selection distance.", "选择目标时允许的最大距离。"));
        SUFFIX.put("lifetimeTicks", new ConfigComments.Text("Anchor lifetime after spawning; the safe minimum preserves required phases and the entity expires before later damage.", "法术锚点生成后的存续时间；安全下限保留必要阶段，实体消失后不再结算伤害。"));
        SUFFIX.put("effectRadius", new ConfigComments.Text("Radius of the target query / influence region; shaped attacks may use narrower hit tests inside it. Not a universal visual scale.", "目标查询或影响区域半径；形状攻击可在其内部采用更窄的命中判定，并非通用视觉尺寸。"));
        SUFFIX.put("damageIntervalTicks", new ConfigComments.Text("Ticks between repeated damage checks; does not repeat one-shot impacts or final bursts.", "连续伤害检查之间的 tick 间隔，不会让一次性冲击或终结爆发重复结算。"));
        SUFFIX.put("maxActiveEntities", new ConfigComments.Text("Concurrent anchors of this spell per dimension; also capped by server.maxEntitiesPerSpell and server.maxActiveSpellEntities. Independent of target count.", "每维度此法术的并发锚点数量，还受 server.maxEntitiesPerSpell 与 server.maxActiveSpellEntities 限制；独立于目标数量。"));
        SUFFIX.put("maxTargets", new ConfigComments.Text("Living targets per filtered query, also capped by server.targetScanLimit. Multi-sample attacks may make several queries per pulse; not a total hit counter.", "筛选后每次查询的生物目标数量，还受 server.targetScanLimit 限制；多采样攻击每次脉冲可能查询多次，这不是总命中次数上限。"));
        SUFFIX.put("healFraction", new ConfigComments.Text("Caster healing as a fraction of the caster's maximum health at impact.", "命中时按施法者最大生命值比例恢复的生命值。"));
        SUFFIX.put("healingFraction", new ConfigComments.Text("Health restored to each allied target per pulse, as a fraction of that ally's maximum health.", "每次脉冲按友方目标最大生命值比例恢复的生命值。"));
        SUFFIX.put("healingIntervalTicks", new ConfigComments.Text("Ticks between allied healing pulses.", "友方治疗脉冲的 tick 间隔。"));
        SUFFIX.put("absorptionHearts", new ConfigComments.Text("Absorption granted to allies; one heart is two health points.", "给予友方的伤害吸收心数；一颗心等于两点生命值。"));
        SUFFIX.put("protectMagic", new ConfigComments.Text("Prevent magic damage to allied targets inside the sanctuary.", "阻止庇护区内友方目标受到魔法伤害。"));
        SUFFIX.put("protectProjectile", new ConfigComments.Text("Prevent projectile damage to allied targets inside the sanctuary.", "阻止庇护区内友方目标受到投射物伤害。"));
        SUFFIX.put("protectFire", new ConfigComments.Text("Prevent fire damage to allied targets inside the sanctuary.", "阻止庇护区内友方目标受到火焰伤害。"));
        SUFFIX.put("attackRadius", new ConfigComments.Text("Horizontal radius of the attack cone.", "攻击扇区的水平半径。"));
        SUFFIX.put("attackAngleDegrees", new ConfigComments.Text("Full horizontal cone angle, centred on the cast direction.", "以施法方向为中心的完整水平攻击角。"));
        SUFFIX.put("verticalTolerance", new ConfigComments.Text("Maximum vertical separation allowed when selecting targets.", "选择目标时允许的最大垂直高度差。"));
        SUFFIX.put("damage", new ConfigComments.Text("Base flat damage before the spell's power scaling; two health points equal one heart.", "经过法术强度缩放前的基础固定伤害；两点生命值等于一颗心。"));
        SUFFIX.put("impactTick", new ConfigComments.Text("One-shot impact tick measured from entity spawn; clamped before expiry.", "从实体生成计时的一次性冲击时刻，实际值限制在消失之前。"));
        SUFFIX.put("damageTick", new ConfigComments.Text("One-shot damage tick measured from entity spawn; clamped before expiry.", "从实体生成计时的一次性伤害时刻，实际值限制在消失之前。"));
        SUFFIX.put("accretionDamageFraction", new ConfigComments.Text("Accretion hit as a fraction of target maximum health.", "吸积阶段按目标最大生命值比例造成的伤害。"));
        SUFFIX.put("collapseDamageFraction", new ConfigComments.Text("Collapse hit as a fraction of target maximum health.", "崩塌阶段按目标最大生命值比例造成的伤害。"));
        SUFFIX.put("hypernovaDamageFraction", new ConfigComments.Text("Hypernova hit as a fraction of target maximum health.", "超新星爆发按目标最大生命值比例造成的伤害。"));
        SUFFIX.put("pulseDamageFraction", new ConfigComments.Text("Damage of each of the four pulses, as a fraction of target maximum health.", "四次脉冲中每次按目标最大生命值比例造成的伤害。"));
        SUFFIX.put("projectileDamageFraction", new ConfigComments.Text("Extra collapse damage per absorbed projectile charge, as a fraction of target maximum health.", "每层吸收投射物充能增加的崩塌伤害，按目标最大生命值比例计算。"));
        SUFFIX.put("maxProjectileCharge", new ConfigComments.Text("Maximum stored projectile charges; absorption does not exceed this charge cap.", "储存的投射物充能层数上限，吸收不会超过此充能上限。"));
        SUFFIX.put("storedDamageShare", new ConfigComments.Text("Fraction of eligible damage dealt by the caster that is stored for the collapse.", "将施法者造成的符合条件伤害按此比例储存，用于最终崩塌。"));
        SUFFIX.put("storedDamageCapFraction", new ConfigComments.Text("Stored collapse damage cap for each target, as a fraction of that target's maximum health.", "对每个目标结算的储存崩塌伤害上限，按该目标最大生命值比例计算。"));
        SUFFIX.put("swallowedDamageStep", new ConfigComments.Text("Extra release damage per swallowed spell light, as a fraction of target maximum health.", "每吞噬一个法术光源增加的释放伤害，按目标最大生命值比例计算。"));
        SUFFIX.put("swallowedDamageCap", new ConfigComments.Text("Maximum swallowed-light count contributing to the release damage bonus.", "参与释放伤害加成的吞噬光源数量上限。"));
        SUFFIX.put("blastDamageFraction", new ConfigComments.Text("One-shot detonation damage as a fraction of target maximum health.", "一次性引爆按目标最大生命值比例造成的伤害。"));
        SUFFIX.put("windDamageFraction", new ConfigComments.Text("Pulsar-wind contact damage per check, as a fraction of target maximum health.", "每次脉冲星风接触检查按目标最大生命值比例造成的伤害。"));
        SUFFIX.put("visualScale", new ConfigComments.Text("Client visual size multiplier; does not change server hit tests, pull or damage radius.", "客户端视觉尺寸倍率，不改变服务器命中判定、吸引范围或伤害半径。"));
        SUFFIX.put("rotationSpeed", new ConfigComments.Text("Client disk-flow speed multiplier; zero freezes disk flow, not the spell timer.", "客户端盘面流动速度倍率；设为 0 冻结盘面流动，不暂停法术计时。"));
        SUFFIX.put("diskBrightness", new ConfigComments.Text("Client disk/photon-ring emission multiplier; zero suppresses emission, not the black core or gameplay.", "客户端盘面与光子环发光倍率；设为 0 关闭发光，但保留黑色核心与玩法。"));
        SUFFIX.put("temperature", new ConfigComments.Text("Client disk-temperature multiplier, shifting amber gas toward white/blue.", "客户端盘温度倍率，使琥珀色气体随温度上升转为白色或蓝色。"));
        SUFFIX.put("dopplerStrength", new ConfigComments.Text("Client approaching/receding gas colour and brightness asymmetry multiplier.", "客户端气体接近侧与远离侧的颜色及亮度非对称倍率。"));
        SPELL_NAMES.put("celestialJudgment", "苍穹裁决");
        SPELL_NAMES.put("stargraveSingularity", "葬星奇点");
        SPELL_NAMES.put("eclipseSeverance", "星蚀断界斩");
        SPELL_NAMES.put("funeralNova", "终焉葬星");
        SPELL_NAMES.put("chromaticAccretion", "虹蚀吸积");
        SPELL_NAMES.put("starless", "熄星");
        SPELL_NAMES.put("constellation", "焚星");
        SPELL_NAMES.put("silhouette", "逆光");
        SPELL_NAMES.put("starfall", "星坠");
        SPELL_NAMES.put("skyCollapse", "天倾");
        SPELL_NAMES.put("stellarConvergence", "星链天顶");
        SPELL_NAMES.put("secondSun", "第二个太阳");
        SPELL_NAMES.put("singularity", "奇点");
        SPELL_NAMES.put("leviathan", "星界巨蛇");
        SPELL_NAMES.put("worldTree", "世界树");
        SPELL_NAMES.put("gargantua", "卡冈图雅");
        SPELL_NAMES.put("cosmicHorseshoe", "宇宙马蹄铁");
        SPELL_NAMES.put("microquasar", "微类星体");
        SPELL_NAMES.put("helixNebula", "螺旋星云");
        SPELL_NAMES.put("magnetar", "磁星");
        SPELL_NAMES.put("tidalDisruption", "潮汐撕裂");
        SPELL_NAMES.put("quasarJet", "类星体喷流");
        SPELL_NAMES.put("pinwheel", "沃夫-拉叶风车");
        SPELL_NAMES.put("crabNebula", "蟹状星云");
        SPELL_NAMES.put("eventHorizon", "无光视界");
        SPELL_NAMES.put("redshiftAbyss", "赤移天渊");
        SPELL_NAMES.put("schwarzschildLens", "施瓦西镜界");
        SPELL_NAMES.put("radiantCollapse", "辉环崩解");
        SPELL_NAMES.put("stasisSingularity", "静滞奇点");
    }

    private ConfigDescriptions() {}
    private static void exact(String key, String english, String chinese) {
        EXACT.put(key, new ConfigComments.Text(english, chinese));
    }

    private static ConfigComments.Text description(String path) {
        ConfigComments.Text exact = EXACT.get(path);
        if (exact != null) return exact;
        String[] parts = path.split("\\.");
        String id = parts.length >= 3 && parts[0].equals("spells") ? parts[1] : parts[0];
        ConfigComments.Text suffix = SUFFIX.get(parts[parts.length - 1]);
        if (suffix != null && SPELL_NAMES.containsKey(id)) {
            return new ConfigComments.Text(id + ": " + suffix.english(), SPELL_NAMES.get(id) + "：" + suffix.chinese());
        }
        throw new IllegalArgumentException("Missing concrete bilingual config description: " + path);
    }

    public static String chinese(String path) { return description(path).chinese(); }

    public static String english(String path, String original) {
        return description(path).english();
    }

    static String spellName(String id, boolean chinese) {
        if (chinese) return SPELL_NAMES.getOrDefault(id, id);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (i == 0) result.append(Character.toUpperCase(c));
            else if (Character.isUpperCase(c)) result.append(' ').append(c);
            else result.append(c);
        }
        return result.toString();
    }

    public static String unit(String path, boolean chinese) {
        String key = path.substring(path.lastIndexOf('.') + 1);
        String en; String zh;
        if (key.equals("sourceTimeRate")) { en = "simulation seconds per game second"; zh = "模拟秒/游戏秒"; }
        else if (key.equals("massSolar")) { en = "solar masses"; zh = "太阳质量"; }
        else if (key.equals("accretionRate")) { en = "fraction of Eddington accretion rate"; zh = "爱丁顿吸积率比例"; }
        else if (key.endsWith("Rs") || path.equals("schwarzschildLens.diskThickness")) { en = "Schwarzschild radii (Rs)"; zh = "史瓦西半径 Rs"; }
        else if (key.endsWith("PerTick")) { en = "optional messages/tick"; zh = "可选消息/tick"; }
        else if (key.endsWith("Ticks") || key.endsWith("Tick")) { en = "game ticks (20 = 1 second)"; zh = "游戏 tick（20 tick = 1 秒）"; }
        else if (key.endsWith("Seconds")) { en = "seconds"; zh = "秒"; }
        else if (key.equals("manaCost")) { en = "mana points"; zh = "法力点"; }
        else if (key.endsWith("Radius") || key.endsWith("Range") || key.equals("verticalTolerance")) { en = "blocks"; zh = "格"; }
        else if (key.endsWith("Degrees")) { en = "degrees"; zh = "度"; }
        else if (key.equals("damage")) { en = "health points (2 = 1 heart)"; zh = "生命值点（2 点 = 1 心）"; }
        else if (key.equals("absorptionHearts")) { en = "hearts (1 = 2 health points)"; zh = "心（1 心 = 2 生命值点）"; }
        else if (key.endsWith("Fraction") || key.equals("swallowedDamageStep")) { en = "fraction of maximum health (0.01 = 1%)"; zh = "最大生命值比例（0.01 = 1%）"; }
        else if (key.equals("storedDamageShare")) { en = "fraction of dealt damage (0.01 = 1%)"; zh = "已造成伤害的比例（0.01 = 1%）"; }
        else if (key.equals("refreshIntervalMs")) { en = "milliseconds"; zh = "毫秒"; }
        else if (key.equals("adaptiveTargetFps")) { en = "frames/second"; zh = "帧/秒"; }
        else if (key.equals("rainbowSpeed")) { en = "hue cycles/second"; zh = "色相循环/秒"; }
        else if (key.equals("glitchFrequency")) { en = "attempts/second"; zh = "次/秒"; }
        else if (key.equals("compatibleScreens")) { en = "list of class-name substrings"; zh = "类名片段列表"; }
        else if (key.equals("maxTextLength")) { en = "visible characters"; zh = "可见字符"; }
        else if (key.startsWith("max") || key.equals("shadowSteps") || key.endsWith("Cap") || key.endsWith("Limit") || key.equals("bhMaxVisible")) { en = "count"; zh = "数量"; }
        else { en = "dimensionless / selector"; zh = "无量纲或选项"; }
        return chinese ? zh : en;
    }
}
