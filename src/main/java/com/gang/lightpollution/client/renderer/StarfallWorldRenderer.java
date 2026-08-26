package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.entity.StarfallEntity;
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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.List;

/**
 * Draws Starfall's meteors as bodies rather than points of light.
 *
 * <p>Each meteor used to be nothing but its entry in the screen-space light pass
 * plus a couple of vanilla flame particles, so however bright it was it still
 * read as a glowing ball drifting down. What was missing is everything that
 * makes a meteor legible as one: a hard-edged incandescent head, a trail behind
 * it pointing the way it came, and a shock spreading across the ground where it
 * lands.</p>
 *
 * <p>Trails and ground shocks share one program and one buffer, so the whole
 * shower costs two draw calls plus one per visible head.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class StarfallWorldRenderer {
    private static final int SPHERE_LAT = 10;
    private static final int SPHERE_LON = 16;
    private static final int BUFFER_CAPACITY = 262_144;
    /** How far past the body the head's hull extends, to carry its glow. */
    private static final float HEAD_HULL_SCALE = 2.4F;
    /** Bodies drawn per frame at most, so a finale cannot spike the frame time. */
    private static final int MAX_HEADS = 10;
    private static final double RENDER_DISTANCE = 224.0D;
    private static final double RENDER_DISTANCE_SQR = RENDER_DISTANCE * RENDER_DISTANCE;
    /** Ticks over which the ground shock expands after a meteor lands. */
    private static final float SHOCK_TICKS = 9.0F;
    /** Segments along a trail. More than a single quad so it can bend and taper. */
    private static final int TRAIL_SEGMENTS = 6;
    /** Arc segments in the ring. Enough that the circle has no visible corners. */
    private static final int MACH_SEGMENTS = 24;
    /** Ticks the ring takes to expand and vanish. */
    private static final float MACH_RING_LIFE = 12.0F;

    /** Ticks the finale's impact shake lasts. */
    private static final float SHAKE_TICKS = 14.0F;
    /** Peak shake amplitude, in degrees. */
    private static final float SHAKE_STRENGTH = 5.5F;
    /** Distance at which the finale's impact can no longer be felt, in blocks. */
    private static final double SHAKE_RANGE = 70.0D;
    /** Trail width as a multiple of the body's radius. */
    private static final float TRAIL_WIDTH_RATIO = 1.7F;

    /**
     * Radius of a meteor's body, in blocks. One place, because the trail's width
     * is derived from it and the two drifting apart is what made the proportions
     * look wrong.
     */
    private static float headRadius(boolean finale, float fall) {
        return (finale ? 7.0F : 1.7F) * (0.82F + fall * 0.3F);
    }

    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);
    private static float[][] unitSphereVertices;

    private StarfallWorldRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        List<StarfallEntity> showers = SpellLightEmitter.collectStarfalls();
        if (showers.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance meteorShader = ConstellationShaders.meteor();
        ShaderInstance headShader = ConstellationShaders.star();
        if (minecraft.level == null || meteorShader == null || headShader == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();

        GlState state = GlState.capture();
        // Camera rotation goes in ModelViewMat and the vertices stay raw and
        // camera-relative, matching the other world renderers. See
        // ConstellationWorldRenderer for what baking it into the vertices instead
        // did to the star's position.
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            modelView.setIdentity();
            modelView.mulPoseMatrix(event.getPoseStack().last().pose());
            RenderSystem.applyModelViewMatrix();
            drawTrailsAndShocks(showers, camera, partialTick, meteorShader);
            drawHeads(showers, camera, partialTick, headShader);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            state.restore();
        }
    }

    /**
     * One buffer for every trail and every ground shock in every active shower.
     * The shader picks between the two from a vertex-colour channel, so they do
     * not need separate passes.
     */
    private static void drawTrailsAndShocks(List<StarfallEntity> showers, Vec3 camera,
                                            float partialTick,
                                            ShaderInstance shader) {
        BufferBuilder builder = begin();
        int vertices = 0;
        for (StarfallEntity entity : showers) {
            float age = entity.getVisualAgeTicks(partialTick);
            for (int meteor = 0; meteor < StarfallEntity.METEOR_COUNT; meteor++) {
                int impact = StarfallEntity.impactTick(meteor);
                boolean finale = StarfallEntity.isFinaleMeteor(meteor);

                if (age < StarfallEntity.spawnTick(meteor)) {
                    continue;
                }
                if (age < impact) {
                    Vec3 position = entity.meteorPosition(meteor, partialTick);
                    if (camera.distanceToSqr(position) > RENDER_DISTANCE_SQR) {
                        continue;
                    }
                    vertices += emitTrail(builder, camera, entity, meteor,
                            position, age, finale);
                    if (entity.hasShockRing(meteor)) {
                        vertices += emitMachRing(builder, camera, entity, meteor,
                                position, age, finale);
                    }
                } else {
                    float since = age - impact;
                    if (since > SHOCK_TICKS) {
                        continue;
                    }
                    Vec3 landing = entity.meteorLanding(meteor);
                    if (camera.distanceToSqr(landing) > RENDER_DISTANCE_SQR) {
                        continue;
                    }
                    vertices += emitShock(builder, camera, landing, meteor,
                            since / SHOCK_TICKS, finale);
                }
            }
        }
        draw(builder, shader, vertices, null);
    }

    /**
     * A tapered ribbon behind the body, turned to face the camera. Split into
     * segments so the near end can be wide and hot and the tip narrow and dark
     * without the interpolation flattening it.
     */
    private static int emitTrail(BufferBuilder builder, Vec3 camera,
                                 StarfallEntity entity, int meteor, Vec3 position,
                                 float age, boolean finale) {
        // The trail points back up the meteor's own heading, which is a slant
        // rather than straight up.
        Vec3 heading = entity.meteorHeading(meteor);
        Vector3f back = new Vector3f((float) -heading.x, (float) -heading.y,
                (float) -heading.z).normalize();

        float fall = Mth.clamp((age - StarfallEntity.spawnTick(meteor))
                / (float) StarfallEntity.fallTicks(meteor), 0.0F, 1.0F);
        float scale = finale ? 3.4F : 1.0F;
        float length = (9.0F + fall * 23.0F) * scale;
        // Tied to the body's own size rather than set independently: the two were
        // picked separately before, so enlarging the head left a wide body
        // dragging a thin thread behind it.
        float width = headRadius(finale, fall) * TRAIL_WIDTH_RATIO;

        // Perpendicular to both the trail and the view, so the ribbon presents
        // its full width from wherever it is watched.
        Vector3f toCamera = new Vector3f(
                (float) (camera.x - position.x),
                (float) (camera.y - position.y),
                (float) (camera.z - position.z));
        Vector3f side = new Vector3f(back).cross(toCamera);
        if (side.lengthSquared() < 1.0E-6F) {
            // Looking straight down the trail; any perpendicular will do.
            side.set(back.z, back.x, back.y).cross(back);
        }
        side.normalize();

        float intensity = (0.55F + fall * 0.95F) * (finale ? 1.5F : 1.0F);
        int packed = color(fall, 0.0F, Mth.clamp(intensity / 4.0F, 0.0F, 1.0F), 1.0F);

        float originX = (float) (position.x - camera.x);
        float originY = (float) (position.y - camera.y);
        float originZ = (float) (position.z - camera.z);
        int vertices = 0;
        for (int segment = 0; segment < TRAIL_SEGMENTS; segment++) {
            float near = segment / (float) TRAIL_SEGMENTS;
            float far = (segment + 1) / (float) TRAIL_SEGMENTS;
            float nearWidth = width * (1.0F - near * 0.78F) * 0.5F;
            float farWidth = width * (1.0F - far * 0.78F) * 0.5F;
            float nearAlong = length * near;
            float farAlong = length * far;

            vertex(builder,
                    originX + back.x * nearAlong - side.x * nearWidth,
                    originY + back.y * nearAlong - side.y * nearWidth,
                    originZ + back.z * nearAlong - side.z * nearWidth,
                    near, 0.0F, packed);
            vertex(builder,
                    originX + back.x * farAlong - side.x * farWidth,
                    originY + back.y * farAlong - side.y * farWidth,
                    originZ + back.z * farAlong - side.z * farWidth,
                    far, 0.0F, packed);
            vertex(builder,
                    originX + back.x * farAlong + side.x * farWidth,
                    originY + back.y * farAlong + side.y * farWidth,
                    originZ + back.z * farAlong + side.z * farWidth,
                    far, 1.0F, packed);
            vertex(builder,
                    originX + back.x * nearAlong + side.x * nearWidth,
                    originY + back.y * nearAlong + side.y * nearWidth,
                    originZ + back.z * nearAlong + side.z * nearWidth,
                    near, 1.0F, packed);
            vertices += 4;
        }
        return vertices;
    }

    /**
     * Ground shake from the finale's arrival, 0 when nothing is landing.
     *
     * <p>A body that size has to be felt as well as seen, and the impact is
     * otherwise over in a couple of frames. Only the finale does this: forty rain
     * meteors each nudging the camera would be unplayable.</p>
     */
    public static float currentImpactShake(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0.0F;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        float shake = 0.0F;
        for (StarfallEntity entity : SpellLightEmitter.collectStarfalls()) {
            float age = entity.getVisualAgeTicks(partialTick);
            for (int meteor = 0; meteor < StarfallEntity.METEOR_COUNT; meteor++) {
                if (!StarfallEntity.isFinaleMeteor(meteor)) {
                    continue;
                }
                float since = age - StarfallEntity.impactTick(meteor);
                if (since < 0.0F || since > SHAKE_TICKS) {
                    continue;
                }
                // Falls away over the window rather than cutting out, or the
                // camera snaps back to still and the hit reads as a glitch.
                float decay = 1.0F - since / SHAKE_TICKS;
                // Distance matters: an impact on the horizon should not rattle
                // the camera as hard as one at your feet.
                double distance = Math.sqrt(
                        camera.distanceToSqr(entity.meteorLanding(meteor)));
                float reach = (float) Mth.clamp(
                        1.0D - distance / SHAKE_RANGE, 0.0D, 1.0D);
                shake = Math.max(shake, SHAKE_STRENGTH * decay * decay * reach);
            }
        }
        return shake;
    }

    /**
     * The shock ring the meteor leaves in the air behind it.
     *
     * <p>Two earlier attempts put a luminous cap on the nose instead. Both read as
     * an object rather than as air, for the same reason: the head is already the
     * brightest thing on screen, so more light in the same place cannot be
     * perceived as a separate structure -- it just thickens the blob. A ring works
     * because it is perpendicular to the travel, sits clear of the body, and is
     * left behind, which is what conveys speed.</p>
     *
     * <p>One ring, not a series. Several at once read as beads threaded on the
     * path rather than as a single front expanding away from it.</p>
     */
    private static int emitMachRing(BufferBuilder builder, Vec3 camera,
                                    StarfallEntity entity, int meteor, Vec3 position,
                                    float age, boolean finale) {
        Vec3 heading = entity.meteorHeading(meteor);
        Vector3f forward = new Vector3f((float) heading.x, (float) heading.y,
                (float) heading.z).normalize();

        float fall = Mth.clamp((age - StarfallEntity.spawnTick(meteor))
                / (float) StarfallEntity.fallTicks(meteor), 0.0F, 1.0F);
        float scale = finale ? 3.4F : 1.0F;
        float intensity = (0.9F + fall * 1.7F) * (finale ? 1.6F : 1.0F);

        // Anchored to distance travelled rather than to the frame, so the ring
        // stays put in the air while the meteor pulls away from it.
        float shed = fall * StarfallEntity.fallTicks(meteor);
        float aged = Mth.clamp(shed / MACH_RING_LIFE, 0.0F, 1.0F);
        if (aged >= 1.0F) {
            return 0;
        }

        // Two axes spanning the plane the ring lies in. The ring is symmetric, so
        // their roll is irrelevant.
        Vector3f right = new Vector3f(forward.z, forward.x, forward.y).cross(forward);
        if (right.lengthSquared() < 1.0E-6F) {
            right.set(1.0F, 0.0F, 0.0F);
        }
        right.normalize();
        Vector3f up = new Vector3f(forward).cross(right).normalize();

        // Behind the body by however far it has moved since the ring was shed.
        float behind = shed * (float) StarfallEntity.fallHeight(meteor)
                / StarfallEntity.fallTicks(meteor) * 0.6F;
        float radius = (1.2F + aged * 15.0F) * scale;
        float fade = 1.0F - aged * aged;

        float centreX = (float) (position.x - camera.x) - forward.x * behind;
        float centreY = (float) (position.y - camera.y) - forward.y * behind;
        float centreZ = (float) (position.z - camera.z) - forward.z * behind;
        // g in the top band selects the ring branch in the shader.
        int packed = color(aged, 0.85F,
                Mth.clamp(intensity / 4.0F, 0.0F, 1.0F), fade);

        int vertices = 0;
        for (int segment = 0; segment < MACH_SEGMENTS; segment++) {
            float a0 = Mth.TWO_PI * segment / MACH_SEGMENTS;
            float a1 = Mth.TWO_PI * (segment + 1) / MACH_SEGMENTS;
            // Thins as it expands, like a real front losing energy.
            float inner = radius * (0.88F - aged * 0.1F);
            emitRingQuad(builder, centreX, centreY, centreZ, right, up,
                    a0, a1, segment / (float) MACH_SEGMENTS,
                    (segment + 1) / (float) MACH_SEGMENTS,
                    inner, radius, packed);
            vertices += 4;
        }
        return vertices;
    }

    /** One arc segment of a ring, from its inner edge to its outer edge. */
    private static void emitRingQuad(BufferBuilder builder,
                                     float centreX, float centreY, float centreZ,
                                     Vector3f right, Vector3f up,
                                     float a0, float a1, float v0, float v1,
                                     float inner, float outer,
                                     int packed) {
        float c0 = Mth.cos(a0);
        float s0 = Mth.sin(a0);
        float c1 = Mth.cos(a1);
        float s1 = Mth.sin(a1);
        // UV0.x crosses the ring's thickness so the shader can shape its edges.
        // UV0.y is the fraction of the way around the whole ring, not of this one
        // segment: passing 0..1 per segment repeated the shader's breakup pattern
        // inside every segment, which is what made one ring look like a string of
        // separate little arcs.
        vertex(builder,
                centreX + (right.x * c0 + up.x * s0) * inner,
                centreY + (right.y * c0 + up.y * s0) * inner,
                centreZ + (right.z * c0 + up.z * s0) * inner,
                0.0F, v0, packed);
        vertex(builder,
                centreX + (right.x * c0 + up.x * s0) * outer,
                centreY + (right.y * c0 + up.y * s0) * outer,
                centreZ + (right.z * c0 + up.z * s0) * outer,
                1.0F, v0, packed);
        vertex(builder,
                centreX + (right.x * c1 + up.x * s1) * outer,
                centreY + (right.y * c1 + up.y * s1) * outer,
                centreZ + (right.z * c1 + up.z * s1) * outer,
                1.0F, v1, packed);
        vertex(builder,
                centreX + (right.x * c1 + up.x * s1) * inner,
                centreY + (right.y * c1 + up.y * s1) * inner,
                centreZ + (right.z * c1 + up.z * s1) * inner,
                0.0F, v1, packed);
    }

    /** A flat expanding front on the ground where a meteor landed. */
    private static int emitShock(BufferBuilder builder, Vec3 camera,
                                 Vec3 landing, int meteor, float progress, boolean finale) {
        float radius = (float) StarfallEntity.blastRadius(meteor) * 2.3F;
        float fade = 1.0F - progress;
        int packed = color(progress, 1.0F,
                Mth.clamp((finale ? 2.2F : 1.3F) / 4.0F, 0.0F, 1.0F), fade * fade);

        // Just clear of the surface, so it reads as lying on the ground instead
        // of z-fighting with it.
        float x = (float) (landing.x - camera.x);
        float y = (float) (landing.y - camera.y) + 0.03F;
        float z = (float) (landing.z - camera.z);
        vertex(builder, x - radius, y, z - radius, 0.0F, 0.0F, packed);
        vertex(builder, x - radius, y, z + radius, 0.0F, 1.0F, packed);
        vertex(builder, x + radius, y, z + radius, 1.0F, 1.0F, packed);
        vertex(builder, x + radius, y, z - radius, 1.0F, 0.0F, packed);
        return 4;
    }

    /**
     * The bodies. These reuse Constellation's analytic star program, which gives
     * a hard silhouette with limb darkening -- the thing a billboard cannot do
     * and the reason a meteor stops looking like a blob of light.
     */
    private static void drawHeads(List<StarfallEntity> showers, Vec3 camera,
                                  float partialTick,
                                  ShaderInstance shader) {
        int drawn = 0;
        for (StarfallEntity entity : showers) {
            float age = entity.getVisualAgeTicks(partialTick);
            for (int meteor = 0; meteor < StarfallEntity.METEOR_COUNT && drawn < MAX_HEADS;
                    meteor++) {
                if (age < StarfallEntity.spawnTick(meteor)
                        || age >= StarfallEntity.impactTick(meteor)) {
                    continue;
                }
                Vec3 position = entity.meteorPosition(meteor, partialTick);
                if (camera.distanceToSqr(position) > RENDER_DISTANCE_SQR) {
                    continue;
                }
                boolean finale = StarfallEntity.isFinaleMeteor(meteor);
                float fall = Mth.clamp((age - StarfallEntity.spawnTick(meteor))
                        / (float) StarfallEntity.fallTicks(meteor), 0.0F, 1.0F);
                // The body is the thing being watched, so it has to have real
                // size on screen; at well under a block across it read as a spark
                // however bright it was.
                float radius = headRadius(finale, fall);
                float brightness = entity.meteorBrightness(meteor, partialTick)
                        * (finale ? 1.45F : 1.0F);
                // Friction heats it as it comes down, so the body runs from
                // orange to near-white rather than sitting at one colour.
                float heat = Mth.clamp(0.42F + fall * 0.5F, 0.0F, 1.0F);
                drawHead(begin(), camera, position, radius, heat,
                        brightness, age, shader);
                drawn++;
            }
        }
    }

    private static void drawHead(BufferBuilder builder, Vec3 camera,
                                 Vec3 position, float radius, float heat,
                                 float brightness, float age, ShaderInstance shader) {
        int packed = color(Mth.frac(age * 0.03F), heat,
                Mth.clamp(brightness * 0.42F, 0.0F, 1.0F), 1.0F);
        float relativeX = (float) (position.x - camera.x);
        float relativeY = (float) (position.y - camera.y);
        float relativeZ = (float) (position.z - camera.z);
        // Same contract as ConstellationWorldRenderer: raw camera-relative, put
        // into view space by the one ModelViewMat set up in render().
        Vector3f sphereCenter = new Vector3f(relativeX, relativeY, relativeZ);

        float hullRadius = radius * HEAD_HULL_SCALE;
        float[][] sphere = unitSphere();
        for (float[] point : sphere) {
            vertex(builder,
                    relativeX + point[0] * hullRadius,
                    relativeY + point[1] * hullRadius,
                    relativeZ + point[2] * hullRadius,
                    radius, 0.0F, packed);
        }
        draw(builder, shader, sphere.length, sphereCenter);
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

    private static void draw(BufferBuilder builder, ShaderInstance shader, int vertices,
                             Vector3f sphereCenter) {
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
            if (sphereCenter != null && shader.getUniform("SphereCenter") != null) {
                shader.getUniform("SphereCenter")
                        .set(sphereCenter.x, sphereCenter.y, sphereCenter.z);
            }
            // Uniforms persist on the ShaderInstance between draws, and Stellar
            // Convergence tints this same program per star, so reset it.
            if (shader.getUniform("TintColor") != null) {
                shader.getUniform("TintColor").set(1.0F, 1.0F, 1.0F, 1.0F);
            }
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

    private static float[][] unitSphere() {
        if (unitSphereVertices != null) {
            return unitSphereVertices;
        }
        float[][] vertices = new float[SPHERE_LAT * SPHERE_LON * 4][3];
        int index = 0;
        for (int latitude = 0; latitude < SPHERE_LAT; latitude++) {
            double theta0 = Math.PI * latitude / SPHERE_LAT;
            double theta1 = Math.PI * (latitude + 1) / SPHERE_LAT;
            for (int longitude = 0; longitude < SPHERE_LON; longitude++) {
                double phi0 = Math.PI * 2.0D * longitude / SPHERE_LON;
                double phi1 = Math.PI * 2.0D * (longitude + 1) / SPHERE_LON;
                vertices[index++] = spherePoint(theta0, phi0);
                vertices[index++] = spherePoint(theta0, phi1);
                vertices[index++] = spherePoint(theta1, phi1);
                vertices[index++] = spherePoint(theta1, phi0);
            }
        }
        unitSphereVertices = vertices;
        return vertices;
    }

    private static float[] spherePoint(double theta, double phi) {
        double sinTheta = Math.sin(theta);
        return new float[] {
                (float) (sinTheta * Math.cos(phi)),
                (float) Math.cos(theta),
                (float) (sinTheta * Math.sin(phi))};
    }

    /**
     * This draws inside LevelRenderer, so every state it changes has to go back
     * exactly as it was or the leak reaches whatever the level draws next.
     */
    private record GlState(boolean blend, boolean depth, boolean cull, boolean depthWrite,
                           int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        private static GlState capture() {
            return new GlState(
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
        }

        private void restore() {
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            if (depth) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            if (cull) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            RenderSystem.depthMask(depthWrite);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
