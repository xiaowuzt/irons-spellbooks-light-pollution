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
    @Nullable
    private static ShaderInstance microquasarJet;
    @Nullable
    private static ShaderInstance helixKnot;
    @Nullable
    private static ShaderInstance magnetarField;
    @Nullable
    private static ShaderInstance tidalStream;
    @Nullable
    private static ShaderInstance quasarBeam;
    @Nullable
    private static ShaderInstance pinwheelDust;
    @Nullable
    private static ShaderInstance crabFilament;
    @Nullable
    private static ShaderInstance effectCore;

    private ConstellationShaders() {
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
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
        register(event, "microquasar_jet", shader -> microquasarJet = shader);
        register(event, "helix_knot", shader -> helixKnot = shader);
        register(event, "magnetar_field", shader -> magnetarField = shader);
        register(event, "tidal_stream", shader -> tidalStream = shader);
        register(event, "quasar_beam", shader -> quasarBeam = shader);
        register(event, "pinwheel_dust", shader -> pinwheelDust = shader);
        register(event, "crab_filament", shader -> crabFilament = shader);
        register(event, "effect_core", shader -> effectCore = shader);
    }

    private static void register(RegisterShadersEvent event, String path,
                                 java.util.function.Consumer<ShaderInstance> callback) {
        register(event, path, DefaultVertexFormat.POSITION_TEX_COLOR, callback);
    }

    /**
     * Register one shader, and survive it failing to compile.
     *
     * <p>This used to let the exception out, which took the whole game down during mod
     * loading — a startup crash with no way past it. That is what happened to a player on
     * an AMD card: one of these shaders declared a function named {@code noise3}, which is
     * a reserved built-in in the GLSL spec, and AMD's compiler declares those built-ins
     * where NVIDIA's does not. A name collision in one effect made the mod unloadable on
     * an entire class of hardware.</p>
     *
     * <p>The name is fixed, but the failure mode is the real defect: driver GLSL
     * differences are not something this can be sure of in advance, so the cost of one has
     * to be a missing effect rather than an unplayable game. Every consumer already
     * null-checks its accessor and returns early, so a null here degrades cleanly.</p>
     */
    private static void register(RegisterShadersEvent event, String path,
                                 com.mojang.blaze3d.vertex.VertexFormat format,
                                 java.util.function.Consumer<ShaderInstance> callback) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, path);
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), id, format),
                    callback);
        } catch (IOException | RuntimeException failure) {
            ShaderCompileDiagnostics.report(event.getResourceProvider(), id, failure);
        }
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

    /** The corkscrew ribbon of SS 433's precessing jets. */
    @Nullable
    public static ShaderInstance microquasarJet() {
        return microquasarJet;
    }

    /** Heads and tails of the Helix Nebula's cometary knots. */
    @Nullable
    public static ShaderInstance helixKnot() {
        return helixKnot;
    }

    /** Closed dipole loops of a magnetar's magnetosphere. */
    @Nullable
    public static ShaderInstance magnetarField() {
        return magnetarField;
    }

    /** The debris stream of a tidal disruption. */
    @Nullable
    public static ShaderInstance tidalStream() {
        return tidalStream;
    }

    /** Channel, knots and terminal lobe of a quasar jet. */
    @Nullable
    public static ShaderInstance quasarBeam() {
        return quasarBeam;
    }

    /** Dust arms of a Wolf-Rayet pinwheel. */
    @Nullable
    public static ShaderInstance pinwheelDust() {
        return pinwheelDust;
    }

    /** Filament cage and interior wind nebula of the Crab. */
    @Nullable
    public static ShaderInstance crabFilament() {
        return crabFilament;
    }

    /** The bright body at the centre of an effect, shared by everything that has one. */
    @Nullable
    public static ShaderInstance effectCore() {
        return effectCore;
    }
}
