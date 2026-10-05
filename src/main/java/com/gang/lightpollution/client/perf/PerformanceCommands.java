package com.gang.lightpollution.client.perf;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellLightConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.List;

/** Explicit diagnostics only; no HUD clutter or per-frame logging. Commands are session overrides. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class PerformanceCommands {
    private PerformanceCommands() {}
    @SubscribeEvent public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lightpollution_perf").executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.literal(AdaptiveVisualQuality.status()), false);
            for (String row : PerfTracker.report()) ctx.getSource().sendSuccess(() -> Component.literal(row), false);
            return 1;
        }).then(Commands.literal("reset").executes(ctx -> {
            AdaptiveVisualQuality.reset();
            ctx.getSource().sendSuccess(() -> Component.literal("Performance measurements reset."), false);
            return 1;
        })));
        event.getDispatcher().register(Commands.literal("lightpollution_quality")
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("fixed", "balanced", "fidelity"), builder))
                        .executes(ctx -> {
                            var mode = switch (StringArgumentType.getString(ctx, "mode")) {
                                case "fixed" -> AdaptiveQualityController.Mode.FIXED;
                                case "balanced" -> AdaptiveQualityController.Mode.AUTO_BALANCED;
                                case "fidelity" -> AdaptiveQualityController.Mode.AUTO_FIDELITY;
                                default -> null;
                            };
                            if (mode == null) { ctx.getSource().sendFailure(Component.literal("Use fixed, balanced or fidelity.")); return 0; }
                            SpellLightConfig.adaptiveMode = mode;
                            AdaptiveVisualQuality.reset();
                            ctx.getSource().sendSuccess(() -> Component.literal("Visual quality: " + mode + " (this session)."), false);
                            return 1;
                        })));
        event.getDispatcher().register(Commands.literal("lightpollution_target_fps")
                .then(Commands.argument("fps", IntegerArgumentType.integer(20, 240)).executes(ctx -> {
                    SpellLightConfig.adaptiveTargetFps = IntegerArgumentType.getInteger(ctx, "fps");
                    AdaptiveVisualQuality.invalidate();
                    ctx.getSource().sendSuccess(() -> Component.literal("Adaptive target: " + SpellLightConfig.adaptiveTargetFps + " FPS (this session)."), false);
                    return 1;
                })));
    }
}
