package com.gang.lightpollution.client;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.BlackHoleVisualConfig;
import com.gang.lightpollution.client.renderer.BlackHoleRenderer;
import com.gang.lightpollution.client.renderer.RedshiftAbyssRenderer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Locale;

/** Local session overrides only. These commands send no packet and cannot change gameplay. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BlackHoleDebugCommands {
    private BlackHoleDebugCommands() {}
    @SubscribeEvent public static void register(RegisterClientCommandsEvent event) {
        var quality = Commands.literal("black_hole_quality");
        for (var mode : BlackHoleVisualConfig.Mode.values()) {
            quality.then(Commands.literal(mode.name().toLowerCase(Locale.ROOT)).executes(context -> {
                BlackHoleVisualConfig.mode = mode;
                BlackHoleRenderer.reset(); RedshiftAbyssRenderer.reset();
                context.getSource().sendSuccess(() -> Component.literal("bhMode=" + mode + " (local session override)"), false);
                return 1;
            }));
        }
        event.getDispatcher().register(quality);
        event.getDispatcher().register(Commands.literal("black_hole_debug")
                .then(Commands.argument("mode", IntegerArgumentType.integer(0, 4)).executes(context -> {
                    BlackHoleVisualConfig.debugMode = IntegerArgumentType.getInteger(context, "mode");
                    context.getSource().sendSuccess(() -> Component.literal("bhDebugMode=" + BlackHoleVisualConfig.debugMode
                            + " (0 final, 1 normal, 2 steps, 3 depth, 4 R2 grid/R6 path)"), false);
                    return 1;
                })));
    }
}
