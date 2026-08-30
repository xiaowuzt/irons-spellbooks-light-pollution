package com.gang.lightpollution.event;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellLightConfig;
import com.gang.lightpollution.SpellLightConfig.TooltipStyle;
import com.gang.lightpollution.api.ConstellationParams;
import com.gang.lightpollution.api.CrabNebulaParams;
import com.gang.lightpollution.api.LeviathanParams;
import com.gang.lightpollution.api.EclipseSeveranceParams;
import com.gang.lightpollution.api.FuneralNovaParams;
import com.gang.lightpollution.api.FxHandle;
import com.gang.lightpollution.api.HelixNebulaParams;
import com.gang.lightpollution.api.MagnetarParams;
import com.gang.lightpollution.api.MicroquasarParams;
import com.gang.lightpollution.api.PinwheelParams;
import com.gang.lightpollution.api.QuasarJetParams;
import com.gang.lightpollution.api.LightPollutionFx;
import com.gang.lightpollution.api.SecondSunParams;
import com.gang.lightpollution.api.SingularityParams;
import com.gang.lightpollution.api.SkyCollapseParams;
import com.gang.lightpollution.api.StarfallParams;
import com.gang.lightpollution.api.StellarConvergenceParams;
import com.gang.lightpollution.api.WorldTreeParams;
import com.gang.lightpollution.api.LightPollutionText;
import com.gang.lightpollution.api.TidalDisruptionParams;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * Client commands for the presentation toggles.
 *
 * <p>Registered as client commands rather than server ones, and that is not a detail: these control
 * how this client draws things. A server command would be sent to the server, which has no opinion
 * about tooltips and no field to change, so it would silently do nothing in multiplayer.</p>
 *
 * <p>No permission requirement either, for the same reason — nothing here affects anyone else, and
 * needing operator rights to change your own tooltip border would be absurd.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PresentationCommands {
    private PresentationCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        var frame = Commands.literal("frame")
                .executes(context -> setStyle(context.getSource(), null));
        for (TooltipStyle style : TooltipStyle.values()) {
            frame = frame.then(Commands.literal(style.name().toLowerCase(Locale.ROOT))
                    .executes(context -> setStyle(context.getSource(), style)));
        }

        var damage = Commands.literal("damage")
                .executes(context -> setFloatingDamage(context.getSource(), null))
                .then(Commands.literal("on")
                        .executes(context -> setFloatingDamage(context.getSource(), true)))
                .then(Commands.literal("off")
                        .executes(context -> setFloatingDamage(context.getSource(), false)));

        // Exercises the public API the way another mod would, which is the only way to know the
        // seam actually works end to end. Client-side, like everything else here.
        var fx = Commands.literal("fx")
                .then(Commands.literal("tidal_disruption")
                        .executes(context -> spawnTidalDisruption(context.getSource()))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1F, 4.0F))
                                .executes(context -> spawnTidalDisruption(context.getSource(),
                                        FloatArgumentType.getFloat(context, "scale")))))
                .then(Commands.literal("helix_nebula")
                        .executes(context -> spawnHelixNebula(context.getSource(), 1.0F))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1F, 4.0F))
                                .executes(context -> spawnHelixNebula(context.getSource(),
                                        FloatArgumentType.getFloat(context, "scale")))))
                .then(Commands.literal("crab_nebula")
                        .executes(context -> spawnCrabNebula(context.getSource(), 1.0F))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1F, 4.0F))
                                .executes(context -> spawnCrabNebula(context.getSource(),
                                        FloatArgumentType.getFloat(context, "scale")))))

                .then(Commands.literal("magnetar")
                        .executes(context -> spawnMagnetar(context.getSource(), 1.0F))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1F, 4.0F))
                                .executes(context -> spawnMagnetar(context.getSource(),
                                        FloatArgumentType.getFloat(context, "scale")))))
                .then(Commands.literal("microquasar")
                        .executes(context -> spawnMicroquasar(context.getSource(), 1.0F))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1F, 4.0F))
                                .executes(context -> spawnMicroquasar(context.getSource(),
                                        FloatArgumentType.getFloat(context, "scale")))))
                .then(Commands.literal("pinwheel")
                        .executes(context -> spawnPinwheel(context.getSource(), 1.0F))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1F, 4.0F))
                                .executes(context -> spawnPinwheel(context.getSource(),
                                        FloatArgumentType.getFloat(context, "scale")))))
                .then(Commands.literal("quasar_jet")
                        .executes(context -> spawnQuasarJet(context.getSource(), 1.0F))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1F, 4.0F))
                                .executes(context -> spawnQuasarJet(context.getSource(),
                                        FloatArgumentType.getFloat(context, "scale")))))
                .then(Commands.literal("second_sun")
                        .executes(context -> spawnSecondSun(context.getSource(), 1.0F))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1F, 4.0F))
                                .executes(context -> spawnSecondSun(context.getSource(),
                                        FloatArgumentType.getFloat(context, "scale")))))
                .then(Commands.literal("singularity")
                        .executes(context -> spawnSingularity(context.getSource(), 1.0F))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1F, 4.0F))
                                .executes(context -> spawnSingularity(context.getSource(),
                                        FloatArgumentType.getFloat(context, "scale")))))
                .then(Commands.literal("sky_collapse")
                        .executes(context -> spawnSkyCollapse(context.getSource(), 1.0F))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1F, 4.0F))
                                .executes(context -> spawnSkyCollapse(context.getSource(),
                                        FloatArgumentType.getFloat(context, "scale")))))
                .then(Commands.literal("starfall")
                        .executes(context -> spawnStarfall(context.getSource(), 1.0F))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1F, 4.0F))
                                .executes(context -> spawnStarfall(context.getSource(),
                                        FloatArgumentType.getFloat(context, "scale")))))
                .then(Commands.literal("constellation")
                        .executes(context -> spawnConstellation(context.getSource(), 1.0F))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1F, 4.0F))
                                .executes(context -> spawnConstellation(context.getSource(),
                                        FloatArgumentType.getFloat(context, "scale")))))
                .then(Commands.literal("stellar_convergence")
                        .executes(context -> spawnStellarConvergence(context.getSource(), 1.0F))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1F, 4.0F))
                                .executes(context -> spawnStellarConvergence(context.getSource(),
                                        FloatArgumentType.getFloat(context, "scale")))))
                .then(Commands.literal("leviathan")
                        .executes(context -> spawnLeviathan(context.getSource(), 1.0F))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1F, 4.0F))
                                .executes(context -> spawnLeviathan(context.getSource(),
                                        FloatArgumentType.getFloat(context, "scale")))))
                .then(Commands.literal("world_tree")
                        .executes(context -> spawnWorldTree(context.getSource(), 1.0F))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1F, 4.0F))
                                .executes(context -> spawnWorldTree(context.getSource(),
                                        FloatArgumentType.getFloat(context, "scale")))))
                .then(Commands.literal("eclipse_severance")
                        .executes(context -> spawnEclipseSeverance(context.getSource())))
                .then(Commands.literal("funeral_nova")
                        .executes(context -> spawnFuneralNova(context.getSource())))
                .then(Commands.literal("clear")
                        .executes(context -> clearFx(context.getSource())));

        // Exercises the text API the same way. The style toggles in particular had been silently
        // doing nothing, so there needs to be a way to see them actually take effect.
        var text = Commands.literal("text");
        for (LightPollutionText.Style style : LightPollutionText.Style.values()) {
            text = text.then(Commands.literal(style.name().toLowerCase(Locale.ROOT))
                    .executes(context -> showText(context.getSource(), style, "Light Pollution"))
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(context -> showText(context.getSource(), style,
                                    StringArgumentType.getString(context, "message")))));
        }
        var effect = Commands.literal("effect");
        for (LightPollutionText.Style style : LightPollutionText.Style.values()) {
            String name = style.name().toLowerCase(Locale.ROOT);
            effect = effect.then(Commands.literal(name)
                    .then(Commands.literal("on")
                            .executes(context -> setTextEffect(context.getSource(), name, true)))
                    .then(Commands.literal("off")
                            .executes(context -> setTextEffect(context.getSource(), name, false))));
        }
        text = text.then(effect);

        dispatcher.register(Commands.literal("lightpollution")
                .then(frame).then(damage).then(fx).then(text));
    }

    /** The handles handed out by the test command, so it can take them away again. */
    private static final java.util.List<FxHandle> TEST_HANDLES = new java.util.ArrayList<>();

    /** Bumped per spawn so two nebulae in a row do not get identical knots. */
    private static int testSeed;

    private static int spawnTidalDisruption(CommandSourceStack source) {
        return spawnTidalDisruption(source, 1.0F);
    }

    private static int spawnTidalDisruption(CommandSourceStack source, float scale) {
        Vec3 at = inFrontOf(source);
        return report(source, LightPollutionFx.tidalDisruption(at,
                        TidalDisruptionParams.of(source.getRotation().y).scale(scale)),
                "Tidal disruption", at, scale);
    }

    private static int spawnHelixNebula(CommandSourceStack source, float scale) {
        Vec3 at = inFrontOf(source);
        return report(source, LightPollutionFx.helixNebula(at,
                        HelixNebulaParams.of(source.getRotation().y, testSeed++).scale(scale)),
                "Helix nebula", at, scale);
    }

    private static int spawnCrabNebula(CommandSourceStack source, float scale) {
        Vec3 at = inFrontOf(source);
        return report(source, LightPollutionFx.crabNebula(at,
                        CrabNebulaParams.of(testSeed++).scale(scale)),
                "Crab nebula", at, scale);
    }


    private static int spawnMagnetar(CommandSourceStack source, float scale) {
        Vec3 at = inFrontOf(source);
        return report(source, LightPollutionFx.magnetar(at,
                        MagnetarParams.of(source.getRotation().y).scale(scale)),
                "Magnetar", at, scale);
    }

    private static int spawnMicroquasar(CommandSourceStack source, float scale) {
        Vec3 at = inFrontOf(source);
        return report(source, LightPollutionFx.microquasar(at,
                        MicroquasarParams.of(source.getRotation().y).scale(scale)),
                "Microquasar", at, scale);
    }

    private static int spawnPinwheel(CommandSourceStack source, float scale) {
        Vec3 at = inFrontOf(source);
        return report(source, LightPollutionFx.pinwheel(at,
                        PinwheelParams.of(source.getRotation().y).scale(scale)),
                "Pinwheel", at, scale);
    }

    private static int spawnQuasarJet(CommandSourceStack source, float scale) {
        Vec3 at = inFrontOf(source);
        return report(source, LightPollutionFx.quasarJet(at,
                        QuasarJetParams.of(source.getRotation().y).scale(scale)),
                "Quasar jet", at, scale);
    }

    private static int spawnSecondSun(CommandSourceStack source, float scale) {
        Vec3 at = inFrontOf(source);
        // The bearing is where it rises, so it comes from where the caller is looking.
        float bearing = (float) Math.toRadians(source.getRotation().y);
        return report(source, LightPollutionFx.secondSun(at,
                        SecondSunParams.of(bearing).scale(scale)),
                "Second sun", at, scale);
    }

    private static int spawnSingularity(CommandSourceStack source, float scale) {
        Vec3 at = inFrontOf(source);
        return report(source, LightPollutionFx.singularity(at,
                        SingularityParams.of(testSeed++).scale(scale)),
                "Singularity", at, scale);
    }

    private static int spawnSkyCollapse(CommandSourceStack source, float scale) {
        Vec3 at = inFrontOf(source);
        // Lands at the caller's own feet height, which is the closest thing to "the ground here"
        // available without sampling the heightmap.
        return report(source, LightPollutionFx.skyCollapse(at,
                        SkyCollapseParams.of(testSeed++, source.getPosition().y).scale(scale)),
                "Sky collapse", at, scale);
    }

    private static int spawnStarfall(CommandSourceStack source, float scale) {
        Vec3 at = inFrontOf(source);
        return report(source, LightPollutionFx.starfall(at,
                        StarfallParams.of(testSeed++, source.getPosition().y).scale(scale)),
                "Starfall", at, scale);
    }

    private static int spawnConstellation(CommandSourceStack source, float scale) {
        Vec3 at = inFrontOf(source);
        return report(source, LightPollutionFx.constellation(at,
                        ConstellationParams.of((float) Math.toRadians(source.getRotation().y))
                                .scale(scale)),
                "Constellation", at, scale);
    }

    private static int spawnStellarConvergence(CommandSourceStack source, float scale) {
        Vec3 at = inFrontOf(source);
        return report(source, LightPollutionFx.stellarConvergence(at,
                        StellarConvergenceParams.of(testSeed++).scale(scale)),
                "Stellar convergence", at, scale);
    }

    private static int spawnLeviathan(CommandSourceStack source, float scale) {
        Vec3 at = inFrontOf(source);
        return report(source, LightPollutionFx.leviathan(at,
                        LeviathanParams.of((float) Math.toRadians(source.getRotation().y),
                                testSeed++).scale(scale)),
                "Leviathan", at, scale);
    }

    private static int spawnWorldTree(CommandSourceStack source, float scale) {
        Vec3 at = inFrontOf(source);
        return report(source, LightPollutionFx.worldTree(at,
                        WorldTreeParams.of(testSeed++, source.getPosition().y).scale(scale)),
                "World tree", at, scale);
    }

    private static int spawnEclipseSeverance(CommandSourceStack source) {
        Vec3 at = inFrontOf(source);
        // No scale argument: the ported sweep has a fixed size and a fixed duration.
        return report(source, LightPollutionFx.eclipseSeverance(at,
                        EclipseSeveranceParams.of(source.getRotation().y, testSeed++)),
                "Eclipse severance", at, 1.0F);
    }

    private static int spawnFuneralNova(CommandSourceStack source) {
        Vec3 at = inFrontOf(source);
        return report(source, LightPollutionFx.funeralNova(at,
                        FuneralNovaParams.of(testSeed++)),
                "Funeral nova", at, 1.0F);
    }

    /** In front of the caller and a little above, so it is not centred on the camera. */
    private static Vec3 inFrontOf(CommandSourceStack source) {
        return source.getPosition()
                .add(new Vec3(0.0D, 6.0D, 0.0D))
                .add(Vec3.directionFromRotation(0.0F, source.getRotation().y).scale(20.0D));
    }

    private static int report(CommandSourceStack source, FxHandle handle, String what, Vec3 at,
                             float scale) {
        if (handle == null) {
            source.sendFailure(Component.literal(
                    "Refused: too many of that effect are already live."));
            return 0;
        }
        TEST_HANDLES.add(handle);
        source.sendSuccess(() -> Component.literal(
                what + " at " + String.format("%.1f %.1f %.1f", at.x, at.y, at.z)
                        + ", scale " + scale + ". /lightpollution fx clear to remove."), false);
        return 1;
    }

    private static int clearFx(CommandSourceStack source) {
        int removed = 0;
        for (FxHandle handle : TEST_HANDLES) {
            if (handle.isAlive()) {
                handle.remove();
                removed++;
            }
        }
        TEST_HANDLES.clear();
        int count = removed;
        source.sendSuccess(() -> Component.literal("Removed " + count + " effect(s)."), false);
        return 1;
    }

    /** Float a styled message in front of the caller, through the public text API. */
    private static int showText(CommandSourceStack source, LightPollutionText.Style style,
                                String message) {
        Vec3 at = inFrontOf(source);
        LightPollutionText.inWorld(LightPollutionText.styled(message, style), at);
        source.sendSuccess(() -> Component.literal(
                "Showed " + style.name().toLowerCase(Locale.ROOT) + " text at "
                        + String.format("%.1f %.1f %.1f", at.x, at.y, at.z) + "."), false);
        return 1;
    }

    /**
     * Turn one text effect on or off at runtime.
     *
     * <p>This path used to look up a class by a name from before the text code was flattened into
     * this mod, so it had been failing silently. Worth a command precisely because a silent no-op is
     * indistinguishable from working unless you can watch it.</p>
     */
    private static int setTextEffect(CommandSourceStack source, String effect, boolean enabled) {
        boolean known = com.gang.lightpollution.text.DynamicTextApi
                .setEffectEnabled(effect, enabled);
        if (!known) {
            source.sendFailure(Component.literal("No such text effect: " + effect));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Text effect " + effect + " " + (enabled ? "on" : "off") + "."), false);
        return 1;
    }

    /** Turn the floating damage numbers on, off, or to the other state. */
    private static int setFloatingDamage(CommandSourceStack source, Boolean explicit) {
        boolean next = explicit != null ? explicit : !SpellLightConfig.floatingDamage;
        SpellLightConfig.floatingDamage = next;
        source.sendSuccess(() -> Component.literal(
                "Floating damage numbers " + (next ? "on" : "off")
                        + ". This session only — set floatingDamage in the client config"
                        + " to keep it."), false);
        return 1;
    }

    /**
     * Pick a tooltip style, or step to the next one when none is named.
     *
     * <p>Writes the in-memory field only. Deliberately not persisted: reloading the config reads the
     * file again and would overwrite this, so the command is for trying a look and the config file
     * is for keeping it. Saying so in the reply is the only way that is discoverable.</p>
     */
    private static int setStyle(CommandSourceStack source, TooltipStyle explicit) {
        TooltipStyle[] all = TooltipStyle.values();
        TooltipStyle next = explicit != null
                ? explicit
                : all[(SpellLightConfig.tooltipStyle.ordinal() + 1) % all.length];
        SpellLightConfig.tooltipStyle = next;
        source.sendSuccess(() -> Component.literal(
                "Tooltip style: " + next.name().toLowerCase(Locale.ROOT)
                        + ". This session only — set tooltipStyle in the client config"
                        + " to keep it."), false);
        return 1;
    }
}
