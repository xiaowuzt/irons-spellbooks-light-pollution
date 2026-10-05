package com.gang.lightpollution.performance;

import com.gang.lightpollution.ExampleMod;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/** Operator diagnostics for integrated and dedicated servers; never mutates the budget. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerPerformanceCommands {
    private ServerPerformanceCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lightpollution_server_perf")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    var source = context.getSource();
                    var snapshot = ServerPerformanceBudget.snapshot();
                    source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                            "Server average MSPT: %.2f; sampled tick span EMA: %.2f ms.",
                            source.getServer().getAverageTickTime(), snapshot.averageTickMillis())), false);
                    if (!snapshot.enabled()) {
                        source.sendSuccess(() -> Component.literal(
                                "Adaptive decoration budget: disabled (no broadcast cap)."), false);
                    } else {
                        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                                "Adaptive decoration budget: %d%%. Current tick so far / limit: "
                                        + "absorption particles %d/%d; damage numbers %d/%d; diagnostics %d/%d.",
                                snapshot.budgetPercent(), snapshot.particleBurstsUsed(), snapshot.particleBurstsLimit(),
                                snapshot.damageTextsUsed(), snapshot.damageTextsLimit(),
                                snapshot.diagnosticsUsed(), snapshot.diagnosticsLimit())), false);
                    }
                    return 1;
                }));
    }
}
