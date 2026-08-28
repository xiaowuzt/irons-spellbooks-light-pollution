package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.entity.SkyCollapseEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws Sky Collapse: the fracture across the sky and the slabs falling out of
 * it.
 *
 * <p>The fracture is drawn as bands high above the camera rather than on the
 * real sky dome. Minecraft's sky is rendered before this stage with its own
 * matrices and fog, and reaching into it would mean fighting both; a band placed
 * far enough out reads as being on the sky and stays inside this renderer's own
 * conventions.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class SkyCollapseWorldRenderer {
    private static final int BUFFER_CAPACITY = 262_144;
    /** How far out the fracture bands are placed, in blocks. */
    private static final float SKY_DISTANCE = 240.0F;
    /** Height of the fracture bands above the camera, in blocks. */
    private static final float SKY_HEIGHT = 150.0F;
    /** Segments along the rift, so its arc and its torn edges can vary. */
    private static final int RIFT_SEGMENTS = 24;
    /** Half-width of the rift band, in blocks. */
    private static final float RIFT_HALF_WIDTH = 46.0F;
    private static final double RENDER_DISTANCE = 320.0D;
    private static final double RENDER_DISTANCE_SQR = RENDER_DISTANCE * RENDER_DISTANCE;
    /** Ticks the keystone's impact shake lasts. */
    private static final float SHAKE_TICKS = 20.0F;
    /** Peak shake amplitude, in degrees. */
    private static final float SHAKE_STRENGTH = 7.0F;
    /** Distance at which the keystone can no longer be felt, in blocks. */
    private static final double SHAKE_RANGE = 90.0D;

    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);

    private SkyCollapseWorldRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        // A shader pack composites over the main target after this
        // stage, so under one the draw has to move to AFTER_LEVEL.
        if (!SpellRenderStage.shouldDraw(
                event, RenderLevelStageEvent.Stage.AFTER_WEATHER)) {
            return;
        }
        List<SkyCollapseEntity> collapses = SpellLightEmitter.collectSkyCollapses();
        if (collapses.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance shader = ConstellationShaders.skyCollapse();
        if (minecraft.level == null || shader == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();

        GlStateGuard state = GlStateGuard.capture();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            modelView.setIdentity();
            modelView.mulPoseMatrix(SpellRenderStage.levelPose(event));
            RenderSystem.applyModelViewMatrix();

            BufferBuilder builder = begin();
            int vertices = 0;
            for (SkyCollapseEntity entity : collapses) {
                vertices += emitFracture(builder, entity, partialTick);
                vertices += emitShards(builder, camera, entity, partialTick);
            }
            draw(builder, shader, vertices);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            state.restore();
        }
    }

    /**
     * The rift. One split running across the sky, from horizon up over the zenith
     * and down the far side.
     *
     * <p>It was a web of five branching cracks first. That read as a drawn
     * pattern rather than as breakage, for two reasons: cracks radiating from a
     * point imply something was struck there, and five of them at once gave the
     * eye no single thing to follow. One arc that opens into a real gap is the
     * shape of a sky splitting.</p>
     */
    private static int emitFracture(BufferBuilder builder, SkyCollapseEntity entity,
                                    float partialTick) {
        float glow = entity.fractureGlow(partialTick);
        if (glow <= 0.01F) {
            return 0;
        }
        float spread = entity.fractureProgress(partialTick);
        // g in the top band selects the rift branch in the shader.
        int packed = color(spread, 0.85F, Mth.clamp(glow * 0.5F, 0.0F, 1.0F), glow);

        float bearing = entity.riftBearing();
        float dirX = Mth.cos(bearing);
        float dirZ = Mth.sin(bearing);
        float sideX = -dirZ;
        float sideZ = dirX;

        int vertices = 0;
        for (int segment = 0; segment < RIFT_SEGMENTS; segment++) {
            float t0 = segment / (float) RIFT_SEGMENTS;
            float t1 = (segment + 1) / (float) RIFT_SEGMENTS;
            // Skip the parts the split has not reached; the shader discards the
            // rest, but there is no reason to submit them.
            if (Math.min(Math.abs(t0 * 2.0F - 1.0F), Math.abs(t1 * 2.0F - 1.0F))
                    > spread) {
                continue;
            }

            float along0 = (t0 * 2.0F - 1.0F) * SKY_DISTANCE;
            float along1 = (t1 * 2.0F - 1.0F) * SKY_DISTANCE;
            // An arc over the zenith rather than a flat ceiling, so it follows the
            // sky instead of hanging above the player like a plank.
            float y0 = SKY_HEIGHT * Mth.sin(t0 * Mth.PI);
            float y1 = SKY_HEIGHT * Mth.sin(t1 * Mth.PI);

            vertex(builder, dirX * along0 - sideX * RIFT_HALF_WIDTH, y0,
                    dirZ * along0 - sideZ * RIFT_HALF_WIDTH, t0, 0.0F, packed);
            vertex(builder, dirX * along1 - sideX * RIFT_HALF_WIDTH, y1,
                    dirZ * along1 - sideZ * RIFT_HALF_WIDTH, t1, 0.0F, packed);
            vertex(builder, dirX * along1 + sideX * RIFT_HALF_WIDTH, y1,
                    dirZ * along1 + sideZ * RIFT_HALF_WIDTH, t1, 1.0F, packed);
            vertex(builder, dirX * along0 + sideX * RIFT_HALF_WIDTH, y0,
                    dirZ * along0 + sideZ * RIFT_HALF_WIDTH, t0, 1.0F, packed);
            vertices += 4;
        }
        return vertices;
    }

    /**
     * The slabs. Each is an irregular polygon rather than a quad: a square
     * silhouette was the clearest tell that these were flat cards and not pieces
     * of something broken.
     */
    private static int emitShards(BufferBuilder builder, Vec3 camera,
                                  SkyCollapseEntity entity, float partialTick) {
        float age = entity.getVisualAgeTicks(partialTick);
        int vertices = 0;
        for (int shard = 0; shard < SkyCollapseEntity.SHARD_COUNT; shard++) {
            float brightness = entity.shardBrightness(shard, partialTick);
            if (brightness <= 0.02F) {
                continue;
            }
            Vec3 centre = entity.shardPosition(shard, partialTick);
            if (camera.distanceToSqr(centre) > RENDER_DISTANCE_SQR) {
                continue;
            }
            float span = SkyCollapseEntity.halfSpan(shard);
            float tilt = entity.shardTilt(shard, partialTick);
            float spin = entity.shardSpin(shard, partialTick);
            float fall = Mth.clamp((age - SkyCollapseEntity.shedTick(shard))
                    / (float) SkyCollapseEntity.SHARD_FALL_TICKS, 0.0F, 1.0F);

            // The slab's own plane. It starts horizontal, as a piece of the dome,
            // and tips as it peels away.
            Vector3f edgeA = new Vector3f(Mth.cos(spin), 0.0F, Mth.sin(spin));
            Vector3f edgeB = new Vector3f(-Mth.sin(spin), 0.0F, Mth.cos(spin));
            float tiltCos = Mth.cos(tilt);
            float tiltSin = Mth.sin(tilt);
            edgeB.set(edgeB.x * tiltCos, tiltSin, edgeB.z * tiltCos);

            // g in the low band selects the slab branch in the shader.
            int packed = color(fall, 0.15F,
                    Mth.clamp(brightness * 0.4F, 0.0F, 1.0F),
                    Mth.clamp(brightness, 0.0F, 1.0F));
            float x = (float) (centre.x - camera.x);
            float y = (float) (centre.y - camera.y);
            float z = (float) (centre.z - camera.z);

            int corners = entity.shardCorners(shard);
            for (int corner = 0; corner < corners; corner++) {
                int next = (corner + 1) % corners;
                float a0 = Mth.TWO_PI * corner / corners
                        + entity.shardCornerSkew(shard, corner);
                float a1 = Mth.TWO_PI * next / corners
                        + entity.shardCornerSkew(shard, next);
                float r0 = entity.shardCornerScale(shard, corner) * span;
                float r1 = entity.shardCornerScale(shard, next) * span;

                float x0 = Mth.cos(a0) * r0;
                float y0 = Mth.sin(a0) * r0;
                float x1 = Mth.cos(a1) * r1;
                float y1 = Mth.sin(a1) * r1;

                // A triangle submitted as a quad with the centre doubled. UV0.x is
                // the distance from the centre, so the shader's rim works on any
                // outline; UV0.y is the way around.
                vertex(builder, x, y, z, 0.0F, corner / (float) corners, packed);
                vertex(builder,
                        x + edgeA.x * x0 + edgeB.x * y0,
                        y + edgeA.y * x0 + edgeB.y * y0,
                        z + edgeA.z * x0 + edgeB.z * y0,
                        1.0F, corner / (float) corners, packed);
                vertex(builder,
                        x + edgeA.x * x1 + edgeB.x * y1,
                        y + edgeA.y * x1 + edgeB.y * y1,
                        z + edgeA.z * x1 + edgeB.z * y1,
                        1.0F, next / (float) corners, packed);
                vertex(builder, x, y, z, 0.0F, next / (float) corners, packed);
                vertices += 4;
            }
        }
        return vertices;
    }

    /** Ground shake from the keystone's arrival, 0 when nothing is landing. */
    public static float currentImpactShake(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0.0F;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        float shake = 0.0F;
        for (SkyCollapseEntity entity : SpellLightEmitter.collectSkyCollapses()) {
            float age = entity.getVisualAgeTicks(partialTick);
            for (int shard = 0; shard < SkyCollapseEntity.SHARD_COUNT; shard++) {
                float since = age - SkyCollapseEntity.impactTick(shard);
                if (since < 0.0F || since > SHAKE_TICKS) {
                    continue;
                }
                // Ordinary shards register, but only the keystone really moves the
                // camera; six equal jolts in a row would just be noise.
                float weight = SkyCollapseEntity.isKeystone(shard) ? 1.0F : 0.35F;
                float decay = 1.0F - since / SHAKE_TICKS;
                double distance = Math.sqrt(
                        camera.distanceToSqr(entity.shardLanding(shard)));
                float reach = (float) Mth.clamp(
                        1.0D - distance / SHAKE_RANGE, 0.0D, 1.0D);
                shake = Math.max(shake,
                        SHAKE_STRENGTH * weight * decay * decay * reach);
            }
        }
        return shake;
    }

    private static BufferBuilder begin() {
        finish(effectBuffer);
        effectBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        return effectBuffer;
    }

    private static void vertex(BufferBuilder builder,
                               float x, float y, float z, float u, float v, int color) {
        builder.vertex(x, y, z).uv(u, v)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF,
                        color & 0xFF, (color >>> 24) & 0xFF)
                .endVertex();
    }

    private static void draw(BufferBuilder builder, ShaderInstance shader, int vertices) {
        if (shader == null || !builder.building() || vertices <= 0) {
            finish(builder);
            return;
        }
        try {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShader(() -> shader);
            BufferUploader.drawWithShader(builder.end());
        } catch (RuntimeException | LinkageError failure) {
            finish(builder);
            throw failure;
        }
    }

    private static void finish(BufferBuilder builder) {
        if (builder == null || !builder.building()) {
            return;
        }
        try {
            BufferBuilder.RenderedBuffer rendered = builder.end();
            if (rendered != null) {
                rendered.release();
            }
        } catch (RuntimeException ignored) {
            if (builder == effectBuffer) {
                effectBuffer = new BufferBuilder(BUFFER_CAPACITY);
            }
        }
    }

    private static int color(float red, float green, float blue, float alpha) {
        int r = Math.round(Mth.clamp(red, 0.0F, 1.0F) * 255.0F);
        int g = Math.round(Mth.clamp(green, 0.0F, 1.0F) * 255.0F);
        int b = Math.round(Mth.clamp(blue, 0.0F, 1.0F) * 255.0F);
        int a = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Everything this renderer changes has to go back exactly as it was. */
}
