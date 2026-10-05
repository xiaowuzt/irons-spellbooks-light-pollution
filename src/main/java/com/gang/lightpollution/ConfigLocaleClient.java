package com.gang.lightpollution;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only first-options-load and hot-language-change observer; no renderer dependencies. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConfigLocaleClient {
    private static int ticks;
    private ConfigLocaleClient() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ticks++ % 20 != 0) return;
        var options = Minecraft.getInstance().options;
        if (options != null) ConfigLocalization.setClientLanguage(options.languageCode);
    }
}
