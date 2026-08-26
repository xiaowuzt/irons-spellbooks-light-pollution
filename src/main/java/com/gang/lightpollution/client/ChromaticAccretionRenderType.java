package com.gang.lightpollution.client;

import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;

/** Holds the client-only shader used by the Chromatic Accretion spell. */
public final class ChromaticAccretionRenderType {
    @Nullable
    private static ShaderInstance shader;

    private ChromaticAccretionRenderType() {
    }

    public static void setShader(@Nullable ShaderInstance shaderInstance) {
        shader = shaderInstance;
    }

    @Nullable
    public static ShaderInstance getShader() {
        return shader;
    }
}
