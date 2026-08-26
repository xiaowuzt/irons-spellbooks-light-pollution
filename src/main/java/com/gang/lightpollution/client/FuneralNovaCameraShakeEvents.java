package com.gang.lightpollution.client;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.renderer.GeminiKillEffectWorldRenderer;
import com.gang.lightpollution.client.renderer.LeviathanWorldRenderer;
import com.gang.lightpollution.client.renderer.SingularityWorldRenderer;
import com.gang.lightpollution.client.renderer.SkyCollapseWorldRenderer;
import com.gang.lightpollution.client.renderer.StarfallWorldRenderer;
import com.gang.lightpollution.client.renderer.WorldTreeWorldRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Camera shake for the effects heavy enough to warrant it: Funeral Nova's
 * Hypernova, Starfall's colossal finale, Sky Collapse's shards,
 * Singularity's detonation, Leviathan's bite and World Tree's eruption.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FuneralNovaCameraShakeEvents {
    private static final long HASH_MULTIPLIER = 0x9E3779B9L;
    private static final float UINT_RANGE = 0x1.0p32F;

    private FuneralNovaCameraShakeEvents() {
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float partialTick = (float) event.getPartialTick();
        // Whichever is shaking harder wins, rather than summing: several effects
        // at once should not add up to something that cannot be aimed through.
        float shake = Math.max(
                GeminiKillEffectWorldRenderer.currentCameraShake(partialTick),
                Math.max(StarfallWorldRenderer.currentImpactShake(partialTick),
                        Math.max(SkyCollapseWorldRenderer.currentImpactShake(partialTick),
                                Math.max(SingularityWorldRenderer.currentBlastShake(partialTick),
                                        Math.max(LeviathanWorldRenderer.currentBiteShake(partialTick),
                                                WorldTreeWorldRenderer.currentTrunkShake(partialTick))))));
        long nowMillis = System.currentTimeMillis();
        if (shake < 0.001F) {
            return;
        }

        long frameId = nowMillis / 16L;
        float seedPitch = (float) ((frameId * HASH_MULTIPLIER) & 0xFFFFFFFFL);
        float seedYaw = (float) (((frameId + 127L) * HASH_MULTIPLIER) & 0xFFFFFFFFL);
        float pitchShake = (seedPitch / UINT_RANGE * 2.0F - 1.0F) * shake * 0.6F;
        float yawShake = (seedYaw / UINT_RANGE * 2.0F - 1.0F) * shake * 0.8F;

        event.setPitch(event.getPitch() + Mth.clamp(pitchShake, -8.0F, 8.0F));
        event.setYaw(event.getYaw() + Mth.clamp(yawShake, -8.0F, 8.0F));
    }
}
