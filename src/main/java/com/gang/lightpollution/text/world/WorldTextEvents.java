package com.gang.lightpollution.text.world;

import com.gang.lightpollution.ExampleMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ticks the floating texts and draws them.
 *
 * <p>Ported from ArcaneVortex's {@code WorldTextEvents}, credited in {@code CREDITS.txt}.</p>
 *
 * <p>Drawn at {@code AFTER_PARTICLES}, which is where vanilla nameplates go, so the text sorts
 * against the world the same way a name tag does. Note this is <em>not</em> the stage the spell
 * effects in this mod use — they need the later one to survive a shader pack's compositing, but
 * text goes through the normal buffer source and does not.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class WorldTextEvents {
    private WorldTextEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            WorldTextManager.tick();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || WorldTextManager.activePopups().isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        try {
            WorldTextRenderer.renderAll(poseStack, buffers, event.getPartialTick());
            buffers.endBatch();
        } finally {
            poseStack.popPose();
        }
    }

    /** Level change: the popups hold positions in the old level, so they have to go. */
    @SubscribeEvent
    public static void onLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            WorldTextManager.clear();
        }
    }
}
