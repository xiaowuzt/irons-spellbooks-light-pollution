package com.gang.lightpollution.client.gpu;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.tooltip.TextPinwheel;
import com.gang.lightpollution.client.tooltip.TooltipText;
import com.gang.lightpollution.text.DynamicTextParser;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Font metrics and parsed presentation must not outlive a resource pack or connection. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class TextCacheEvents {
    private TextCacheEvents() {
    }

    public static void clear() {
        TextPinwheel.clearCache();
        TooltipText.clearCache();
        DynamicTextParser.clearCache();
        EffectRenderType.clearCache();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD,
            value = Dist.CLIENT)
    public static final class Reload {
        private Reload() {
        }

        @SubscribeEvent
        public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> clear());
        }
    }
}
