package com.gang.lightpollution.client;

import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;

/** Holds the client-only shader instance used by the Stargrave Singularity. */
public final class StargraveSingularityRenderType {
    @Nullable
    private static ShaderInstance shader;

    private StargraveSingularityRenderType() {
    }

    public static void setShader(@Nullable ShaderInstance shaderInstance) {
        shader = shaderInstance;
    }

    @Nullable
    public static ShaderInstance getShader() {
        return shader;
    }
}
