package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Decides which render stage this mod's world geometry has to be drawn at, and hands
 * out the world-to-view matrix that goes with it.
 *
 * <p>Normally that is whatever stage the effect chose. Under a shader pack it has to
 * be {@code AFTER_LEVEL}, and the reason was measured rather than guessed: with
 * Complementary Reimagined loaded under Oculus 1.8.0, the centre pixel of
 * Minecraft's main render target reads as flat sky blue at every stage from
 * {@code AFTER_SKY} through {@code AFTER_WEATHER}, and only changes at
 * {@code AFTER_LEVEL}. Iris renders terrain colour into its own targets and
 * composites into the main target at the end of the level render, so anything drawn
 * into the main target before that point is simply overwritten. Depth, by contrast,
 * is shared throughout — so moving the draw later costs nothing in occlusion.</p>
 *
 * <p>Several things that were expected to be problems measured clean and are
 * deliberately not handled here: only one colour attachment is bound during the
 * Forge stages, so there is no gbuffer to pollute, and the depth attachment is a
 * {@code DEPTH_COMPONENT} texture exactly as in vanilla, so depth blits do not need
 * a format workaround.</p>
 *
 * <p>{@code AFTER_LEVEL} is a safe destination, which is worth stating precisely
 * because it is not obvious: Forge dispatches it from {@code GameRenderer}, not from
 * {@code LevelRenderer}, and Oculus injects its composite and {@code final} passes
 * before {@code LevelRenderer.renderLevel} returns. So the pack has finished
 * compositing by the time this stage fires. The only Oculus work left afterwards is a
 * colour-space conversion that no-ops on the default sRGB setting, and the depth
 * buffer is not cleared until after the stage either — so depth testing still
 * works.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class SpellRenderStage {
    /**
     * The world-to-view matrix captured at {@code AFTER_WEATHER}.
     *
     * <p>This is the part that actually made effects invisible under a pack, and it
     * was measured, not guessed: with the pack off, Starfall's draw reported half a
     * million samples passing the depth test; with it on, and the only difference
     * being the move to {@code AFTER_LEVEL}, exactly zero passed — while the bound
     * framebuffer, viewport, depth function, depth contents, blend state, cull state
     * and projection matrix were all byte-for-byte identical.</p>
     *
     * <p>What differs is the event's own {@code PoseStack}. {@code AFTER_WEATHER} is
     * dispatched from {@code LevelRenderer} and its stack carries the camera's
     * rotation; {@code AFTER_LEVEL} is dispatched from {@code GameRenderer}, whose
     * stack does not. Vertices here are submitted camera-relative and rely on that
     * rotation being in {@code ModelViewMat}, so without it every triangle lands
     * somewhere off-screen. {@link SpellLightEvents} already carried its own copy of
     * this matrix for exactly this reason; this hoists it somewhere every renderer can
     * reach.</p>
     */
    private static Matrix4f levelView;

    private SpellRenderStage() {
    }

    /**
     * Captures the level's view matrix before anything draws.
     *
     * <p>Highest priority so the copy exists by the time any renderer's handler runs,
     * whichever stage that handler ends up drawing at.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void captureLevelView(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            levelView = new Matrix4f(event.getPoseStack().last().pose());
        }
    }

    /**
     * The world-to-view matrix to push into {@code ModelViewMat} for this event.
     *
     * <p>Falls back to the event's own stack when nothing has been captured yet, which
     * is the correct answer for every stage that {@code LevelRenderer} dispatches.</p>
     */
    public static Matrix4f levelPose(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL
                && levelView != null) {
            return levelView;
        }
        return event.getPoseStack().last().pose();
    }

    /**
     * The same matrix as {@link #levelPose}, wrapped in a stack.
     *
     * <p>For the renderers that bake the world-to-view transform into their vertices
     * instead of pushing it into {@code ModelViewMat}. They need a {@code PoseStack}
     * to walk, so at {@code AFTER_LEVEL} they get a fresh one seeded with the captured
     * matrix rather than the event's own, which by then has lost the camera
     * rotation.</p>
     */
    public static com.mojang.blaze3d.vertex.PoseStack levelPoseStack(
            RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL
                || levelView == null) {
            return event.getPoseStack();
        }
        com.mojang.blaze3d.vertex.PoseStack stack =
                new com.mojang.blaze3d.vertex.PoseStack();
        stack.mulPoseMatrix(levelView);
        return stack;
    }

    /**
     * Whether the caller should draw its world geometry during this event.
     *
     * @param normal the stage this effect uses when no shader pack is involved
     */
    public static boolean shouldDraw(RenderLevelStageEvent event,
                                     RenderLevelStageEvent.Stage normal) {
        if (ShaderPackState.renderingShadowPass()) {
            // Insurance rather than a live fix. Oculus's ShadowRenderer drives chunk
            // and entity rendering directly instead of going through
            // LevelRenderer.renderLevel, so none of the stages this mod uses can
            // fire during the shadow pass. The terrain-layer stages
            // (AFTER_SOLID_BLOCKS and friends) *do* fire there, though, so this stays
            // for whenever something starts drawing on one of those.
            return false;
        }
        return event.getStage() == stageFor(normal);
    }

    /** The stage {@code normal} becomes under the current pipeline. */
    public static RenderLevelStageEvent.Stage stageFor(
            RenderLevelStageEvent.Stage normal) {
        return ShaderPackState.packActive()
                ? RenderLevelStageEvent.Stage.AFTER_LEVEL
                : normal;
    }
}
