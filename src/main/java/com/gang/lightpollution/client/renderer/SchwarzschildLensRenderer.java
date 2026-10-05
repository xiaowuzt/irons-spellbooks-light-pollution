package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.entity.SchwarzschildLensEntity;
import com.gang.lightpollution.SpellLightConfig;
import net.minecraft.client.renderer.ShaderInstance;

/** Dedicated world-pass renderer settings; NoopRenderer handles the anchor entity itself. */
public final class SchwarzschildLensRenderer {
    private SchwarzschildLensRenderer() {}
    public static void prepare(ShaderInstance shader,SchwarzschildLensEntity entity,float partial) {
        BlackHoleUniforms.vec4(shader,"bhEffect",1,0,0,0);
        BlackHoleUniforms.vec4(shader,"bhR2Gas",SpellLightConfig.schwarzschildLensDiskThickness,
                SpellLightConfig.schwarzschildLensNoiseContrast,SpellLightConfig.schwarzschildLensFlowSpeed,
                SpellLightConfig.schwarzschildLensDiskBrightness);
    }
}
