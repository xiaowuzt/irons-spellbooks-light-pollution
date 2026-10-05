package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.entity.StasisSingularityEntity;
import net.minecraft.client.renderer.ShaderInstance;

/** Dedicated world-pass renderer settings; NoopRenderer handles the anchor entity itself. */
public final class StasisSingularityRenderer {
    private StasisSingularityRenderer() {}
    public static void prepare(ShaderInstance shader,StasisSingularityEntity entity,float partial) {
        BlackHoleUniforms.vec4(shader,"bhEffect",0,0,entity.localTimeScale(),2);
    }
}
