package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Hooks spell anchor lifetimes into the shared real-light pass. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SpellLightEvents {
    /**
     * The AFTER_LEVEL event exposes GameRenderer's projection PoseStack, not
     * the world/model-view stack used while LevelRenderer writes the depth
     * buffer.  Capture the latter from an earlier level stage and consume the
     * copy after the scene is complete.
     */
    @Nullable
    private static Matrix4f levelViewMatrix;
    @Nullable
    private static BlockPos lastTargetPos;
    @Nullable
    private static BlockState lastTargetState;

    private SpellLightEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) {
            SpellLightEmitter.add(event.getEntity());
            reportMissingRenderer(event.getEntity());
        }
    }

    /**
     * Names any entity type that reaches the client without a renderer.
     *
     * <p>Vanilla's entity loop dereferences the renderer without a null check,
     * so a type that was never registered takes the client down with a bare
     * {@code NullPointerException} inside {@code EntityRenderDispatcher} —
     * naming neither the entity nor the mod that spawned it. Logging the type
     * as it arrives is the only way to identify it from a crash report.</p>
     */
    private static void reportMissingRenderer(Entity entity) {
        if (Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity) == null) {
            ExampleMod.LOGGER.error(
                    "Entity type {} joined the client level with no renderer; "
                            + "vanilla will crash the moment it is rendered",
                    ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide) {
            SpellLightEmitter.remove(event.getEntity());
        }
    }

    /**
     * A left-click can begin or continue mining before the server's block
     * update arrives.  Start collecting immediately so the old voxel bit is
     * removed on the first frame in which the new depth buffer shows the hole.
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide) {
            SpellLightPostProcessor.requestVoxelRefresh();
        }
    }

    /** Placement and toggling a door/trapdoor also change the rendered occluder. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) {
            SpellLightPostProcessor.requestVoxelRefresh();
        }
    }

    /**
     * The interaction event fires when mining starts, not necessarily when
     * the server/client actually replaces the block. Watching the client
     * crosshair target catches that later state transition without touching
     * Minecraft internals or forcing a full voxel rebuild every frame.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            lastTargetPos = null;
            lastTargetState = null;
            return;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult blockHit)) {
            lastTargetPos = null;
            lastTargetState = null;
            return;
        }
        BlockPos targetPos = blockHit.getBlockPos();
        BlockState targetState = minecraft.level.getBlockState(targetPos);
        if (lastTargetPos == null || !lastTargetPos.equals(targetPos)
                || lastTargetState == null || !lastTargetState.equals(targetState)) {
            if (lastTargetPos != null && lastTargetPos.equals(targetPos)
                    && lastTargetState != null && !lastTargetState.equals(targetState)) {
                SpellLightPostProcessor.requestVoxelRefresh();
            }
            lastTargetPos = targetPos.immutable();
            lastTargetState = targetState;
        }
    }

    /**
     * Lowest priority so this runs after every effect has drawn.
     *
     * <p>Under a shader pack all the world geometry moves to {@code AFTER_LEVEL},
     * which is also where this screen-space pass runs. Handler order within a stage
     * is otherwise undefined, and this pass reads the colour buffer — so it has to be
     * last or it lights a frame that is still half-empty.</p>
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void renderLights(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            // This PoseStack is the same world->view stack passed to
            // LevelRenderer.renderLevel (camera yaw/pitch/roll included).
            levelViewMatrix = new Matrix4f(event.getPoseStack().last().pose());
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL
                || levelViewMatrix == null) {
            return;
        }
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
        Matrix4f view = new Matrix4f(levelViewMatrix);
        // Consume exactly the view captured for this level render.  If a
        // transition dispatches AFTER_LEVEL without an earlier weather stage,
        // do not reuse a matrix from the previous world/frame.
        levelViewMatrix = null;
        java.util.List<SpellLightEmitter.Light> lights =
                SpellLightEmitter.collect(event.getPartialTick());
        boolean lit = SpellLightPostProcessor.render(
                lights, projection, view, event.getCamera(), event.getPartialTick());
        // Silhouette's inversion rides along inside the light blend, so it only
        // needs its own pass on frames where that blend did not happen.
        if (!lit) {
            SpellLightPostProcessor.renderSilhouetteOnly(
                    projection, view, event.getCamera(), event.getPartialTick());
        }
        // After the light pass, so the void darkens the illuminated scene rather
        // than being lit over. Runs even when the light list is empty: a void that
        // has not released yet emits nothing.
        SpellLightPostProcessor.renderStarless(SpellLightEmitter.collectVoids(),
                projection, view, event.getCamera(), event.getPartialTick());
        // Last: this one refracts whatever is already on screen, so it has to see
        // the finished frame including the meteors' own light and trails.
        SpellLightPostProcessor.renderMeteorShocks(SpellLightEmitter.collectStarfalls(),
                projection, view, event.getCamera(), event.getPartialTick());
        // Same reason, and last for the same reason: Singularity's shock is a
        // refraction of the finished frame, not something added to it.
        SpellLightPostProcessor.renderSingularityLenses(
                SpellLightEmitter.collectSingularities(),
                projection, view, event.getCamera(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SpellLightEmitter.clear();
        levelViewMatrix = null;
        lastTargetPos = null;
        lastTargetState = null;
        SpellLightPostProcessor.release();
    }
}
