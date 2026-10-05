package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.entity.RadiantCollapseEntity;
import com.gang.lightpollution.SpellLightConfig;
import net.minecraft.client.renderer.ShaderInstance;

/** Dedicated world-pass renderer settings; NoopRenderer handles the anchor entity itself. */
public final class RadiantCollapseRenderer {
    private RadiantCollapseRenderer() {}
    public static void prepare(ShaderInstance shader,RadiantCollapseEntity entity,float partial) {
        BlackHoleUniforms.vec4(shader,"bhEffect",entity.shellRadius(partial),entity.closeTick(),entity.lifetime(),1);
        BlackHoleUniforms.vec4(shader,"bhR4Options",SpellLightConfig.radiantCollapseTimeScale,
                SpellLightConfig.radiantCollapseVisualScale,SpellLightConfig.radiantCollapseIntensity,0);
    }
}
