package com.gang.lightpollution.client;

import com.gang.lightpollution.ExampleMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Holds the client-only programs that draw celestial bodies.
 *
 * <p>The star program is shared: Constellation's star and Starfall's meteor heads
 * are the same kind of thing, an analytic burning sphere, and only differ in
 * radius and colour temperature.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ConstellationShaders {
    @Nullable
    private static ShaderInstance star;
    @Nullable
    private static ShaderInstance meteor;
    @Nullable
    private static ShaderInstance skyCollapse;
    @Nullable
    private static ShaderInstance convergence;
    @Nullable
    private static ShaderInstance secondSun;
    @Nullable
    private static ShaderInstance bolt;
    @Nullable
    private static ShaderInstance singularity;
    @Nullable
    private static ShaderInstance singularityCore;
    @Nullable
    private static ShaderInstance leviathan;
    @Nullable
    private static ShaderInstance worldTree;

    private ConstellationShaders() {
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        register(event, "constellation_star", shader -> star = shader);
        register(event, "starfall_meteor", shader -> meteor = shader);
        register(event, "sky_collapse", shader -> skyCollapse = shader);
        register(event, "stellar_convergence", shader -> convergence = shader);
        register(event, "second_sun", shader -> secondSun = shader);
        register(event, "spell_bolt", shader -> bolt = shader);
        register(event, "singularity", shader -> singularity = shader);
        register(event, "singularity_core", shader -> singularityCore = shader);
        // These two carry a real surface normal so the shader can shade per
        // fragment. Everything else here is flat or billboarded and does not.
        register(event, "leviathan", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
                shader -> leviathan = shader);
        register(event, "world_tree", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
                shader -> worldTree = shader);
    }

    private static void register(RegisterShadersEvent event, String path,
                                 java.util.function.Consumer<ShaderInstance> callback)
            throws IOException {
        register(event, path, DefaultVertexFormat.POSITION_TEX_COLOR, callback);
    }

    private static void register(RegisterShadersEvent event, String path,
                                 com.mojang.blaze3d.vertex.VertexFormat format,
                                 java.util.function.Consumer<ShaderInstance> callback)
            throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, path),
                        format),
                callback);
    }

    @Nullable
    public static ShaderInstance star() {
        return star;
    }

    /** Trails and ground shocks for Starfall's meteors. */
    @Nullable
    public static ShaderInstance meteor() {
        return meteor;
    }

    /** Sky fracture and falling slabs for Sky Collapse. */
    @Nullable
    public static ShaderInstance skyCollapse() {
        return skyCollapse;
    }

    /** Filaments, column and burst for Stellar Convergence. */
    @Nullable
    public static ShaderInstance convergence() {
        return convergence;
    }

    /** The sky disc for Second Sun. */
    @Nullable
    public static ShaderInstance secondSun() {
        return secondSun;
    }

    /** Shared lightning, used by Stellar Convergence and Singularity. */
    @Nullable
    public static ShaderInstance bolt() {
        return bolt;
    }

    /** Core and shockwave spheres for Singularity. */
    @Nullable
    public static ShaderInstance singularity() {
        return singularity;
    }

    /** The solid, depth-writing body at Singularity's centre. */
    @Nullable
    public static ShaderInstance singularityCore() {
        return singularityCore;
    }

    /** Body, fins and jaws for Leviathan. */
    @Nullable
    public static ShaderInstance leviathan() {
        return leviathan;
    }

    /** Roots, trunk, branches and leaf panes for World Tree. */
    @Nullable
    public static ShaderInstance worldTree() {
        return worldTree;
    }
}
