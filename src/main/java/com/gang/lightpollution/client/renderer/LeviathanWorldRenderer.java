package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.api.LeviathanParams;
import com.gang.lightpollution.fx.FxRegistry;
import com.gang.lightpollution.fx.LeviathanShape;
import com.gang.lightpollution.fx.LeviathanSource;
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
 * Draws Leviathan as an actual snake.
 *
 * <p>The body is one closed tube whose cross-section is a rounded trapezoid with a
 * flat ventral plate — not a cylinder, because a snake's belly is a flat row of
 * scutes and a round pipe can never read as one. The section morphs into a broad,
 * dorsally flattened spade over the first tenth of the length, which is the head,
 * and the rings are packed toward that end so the skull, brow and neck pinch are
 * actually sampled. On top of that: recurved fangs, eyes recessed under the brow,
 * and an asymmetric gape, because a snake drops its lower jaw and barely lifts the
 * upper one.</p>
 *
 * <p>Nothing here is camera-facing. The first version was a flat ribbon that
 * swivelled to face the viewer, which is why it read as a painted band.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class LeviathanWorldRenderer {
    private static final int BUFFER_CAPACITY = 2_097_152;
    /** Divisions around the body. Enough that the silhouette has no flat facets. */
    private static final int BODY_SIDES = 16;
    /** Rings along the body. */
    private static final int BODY_RINGS = 96;
    /** Divisions around a jaw. */
    private static final int JAW_SIDES = 8;
    /** Rings along a jaw. */
    private static final int JAW_RINGS = 8;
    /** Length of a jaw, in blocks. Matched to the head, which spans 9 blocks. */
    private static final float JAW_LENGTH = 9.0F;
    /** How wide the jaws open at full gape, in radians. */
    private static final float JAW_GAPE = 1.05F;
    /**
     * Share of the gape taken by the lower jaw.
     *
     * <p>A snake's gape is almost entirely mandibular — the lower jaw swings down
     * and the quadrate lets it drop further, while the upper jaw lifts only
     * slightly. Opening both by the same angle, as the first version did, reads as a
     * pair of pincers rather than a mouth.</p>
     */
    private static final float LOWER_JAW_SHARE = 0.85F;
    /** Large fangs per jaw. */
    private static final int FANGS_PER_JAW = 2;
    /** Small teeth per jaw behind the fangs. */
    private static final int TEETH_PER_JAW = 8;
    /** How far a fang curves backward, as a fraction of its length. */
    private static final float FANG_RECURVE = 0.42F;
    private static final double RENDER_DISTANCE = 340.0D;
    private static final double RENDER_DISTANCE_SQR = RENDER_DISTANCE * RENDER_DISTANCE;
    /** Ticks the bite's shake lasts. */
    private static final float SHAKE_TICKS = 16.0F;
    /** Peak shake amplitude, in degrees. */
    private static final float SHAKE_STRENGTH = 6.0F;
    /** Distance at which the bite can no longer be felt, in blocks. */
    private static final double SHAKE_RANGE = 70.0D;

    /** Shader branch selectors, packed into the vertex colour's green channel. */
    private static final float MODE_BODY = 0.10F;
    private static final float MODE_TOOTH = 0.45F;
    private static final float MODE_EYE = 0.68F;
    private static final float MODE_JAW = 0.90F;

    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);

    private LeviathanWorldRenderer() {
    }

    /**
     * Where ring {@code x} of {@code BODY_RINGS} sits along the body, 0 to 1.
     *
     * <p>Packed toward the head. Uniform spacing over 90 blocks gives about a block
     * per ring, and the entire skull is nine blocks long — so a uniform tube spends
     * nine rings on the head and eighty-seven on a nearly straight taper. This puts
     * roughly a third of them in the front tenth.</p>
     */
    private static float ringWarp(float x) {
        return 0.12F * x + 0.88F * x * x;
    }

    /**
     * The body's cross-section: a rounded trapezoid through the trunk, morphing to a
     * broad flat spade over the head, with a supraocular brow ridge.
     */
    private static TubeMeshBuilder.Section bodySection(float extended) {
        return (angle, alongUnit, out) -> {
            float t = ringWarp(alongUnit) * extended;
            // 1 through the skull, falling to 0 by the time the neck is over.
            float headness = 1.0F - Mth.clamp((t - 0.05F) / 0.08F, 0.0F, 1.0F);

            float heightRatio = Mth.lerp(headness,
                    LeviathanShape.SECTION_HEIGHT_RATIO, 0.55F);
            float ventral = LeviathanShape.SECTION_VENTRAL_TAPER
                    * (1.0F - 0.5F * headness);
            TubeMeshBuilder.trapezoid(angle, heightRatio, ventral, out);

            // The head is broader than the neck, which is what makes it a head.
            out.x *= 1.0F + 0.35F * headness;

            if (headness > 0.001F) {
                // Supraocular brow: the scales above the eye overhang the socket,
                // giving a real ridge. Peaks dorsolaterally, at roughly 50 degrees
                // off the flank, and it is geometry rather than shading because a
                // painted brow disappears the moment the light moves.
                float dorsal = Mth.sin(angle);
                float lateral = Math.abs(Mth.cos(angle));
                float ridge = (float) Math.exp(-Math.pow((lateral - 0.62F) / 0.22F, 2.0D))
                        * Mth.clamp(dorsal, 0.0F, 1.0F);
                out.y += ridge * 0.26F * headness;
                out.x += ridge * Math.signum(Mth.cos(angle)) * 0.12F * headness;
            }
        };
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        // A shader pack composites over the main target after this
        // stage, so under one the draw has to move to AFTER_LEVEL.
        if (!SpellRenderStage.shouldDraw(
                event, RenderLevelStageEvent.Stage.AFTER_WEATHER)) {
            return;
        }
        // The spell's own anchors plus anything another mod asked for through the API. The
        // renderer does not distinguish them, which is the point of the source interface.
        List<LeviathanSource> effects =
                new java.util.ArrayList<>(SpellLightEmitter.collectLeviathans());
        effects.addAll(FxRegistry.leviathans());
        if (effects.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance shader = ConstellationShaders.leviathan();
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

            for (LeviathanSource entity : effects) {
                if (camera.distanceToSqr(entity.anchor(partialTick))
                        > RENDER_DISTANCE_SQR) {
                    continue;
                }
                BufferBuilder builder = begin();
                int vertices = 0;
                vertices += emitBody(builder, camera, entity, partialTick);
                vertices += emitJaws(builder, camera, entity, partialTick);
                vertices += emitEyes(builder, camera, entity, partialTick);
                draw(builder, shader, vertices, LeviathanShape.unravel(entity.getVisualAgeTicks(partialTick), entity.shapeParams().lifetimeTicks()));
            }
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            state.restore();
        }
    }

    /** The body: one closed tube along the spine, capped at the tail. */
    private static int emitBody(BufferBuilder builder, Vec3 camera,
                                LeviathanSource entity, float partialTick) {
        float extended = LeviathanShape.extended(entity.getVisualAgeTicks(partialTick));
        float brightness = LeviathanShape.brightness(entity.getVisualAgeTicks(partialTick), entity.shapeParams().lifetimeTicks());
        if (extended <= 0.01F || brightness <= 0.02F) {
            return 0;
        }
        int alpha = Math.round(Mth.clamp(brightness, 0.0F, 1.0F) * 255.0F);
        float intensity = Mth.clamp(brightness * 0.3F, 0.0F, 1.0F);

        // Only the arrived portion exists, so the ring count follows how much of
        // the body has swum in.
        int rings = Math.max(2, Math.round(BODY_RINGS * (0.35F + 0.65F * extended)));
        Vec3[] path = new Vec3[rings];
        float[] radii = new float[rings];
        float[] rolls = new float[rings];
        for (int index = 0; index < rings; index++) {
            float t = ringWarp(index / (float) (rings - 1)) * extended;
            path[index] = LeviathanShape.spinePoint(entity.shapeParams(), entity.anchor(partialTick), t, entity.getVisualAgeTicks(partialTick)).subtract(camera);
            radii[index] = LeviathanShape.bodyRadius(entity.shapeParams(), t, entity.getVisualAgeTicks(partialTick), entity.shapeParams().lifetimeTicks());
            rolls[index] = LeviathanShape.roll(entity.shapeParams(), t, entity.getVisualAgeTicks(partialTick));
        }

        TubeMeshBuilder.Section section = bodySection(extended);
        // An explicit roll, so the flat ventral plate stays down. Parallel transport
        // would let it spiral onto the flanks over ninety blocks.
        TubeMeshBuilder.Ring[] frames = TubeMeshBuilder.frames(path, radii, rolls);
        // UV0.x carries distance along the body in blocks, so the shader can lay
        // scale rows at a real physical size instead of stretching them with length.
        int vertices = TubeMeshBuilder.emit(builder, frames, BODY_SIDES, section,
                MODE_BODY, 0.0F, intensity, alpha,
                0.0F, extended * LeviathanShape.BODY_LENGTH);

        // Close the tail to a point rather than leaving the tube open.
        TubeMeshBuilder.Ring last = frames[frames.length - 1];
        Vec3 beyond = path[rings - 1].subtract(path[Math.max(0, rings - 2)]);
        double length = beyond.length();
        if (length > 1.0E-4D) {
            Vec3 tip = path[rings - 1].add(beyond.scale(1.2D / length));
            vertices += TubeMeshBuilder.emitCap(builder, last, tip, BODY_SIDES,
                    section, MODE_BODY, 0.0F, intensity, alpha,
                    extended * LeviathanShape.BODY_LENGTH);
        }
        return vertices;
    }

    /**
     * The eyes: two short tapered stubs sitting in the sockets under the brow.
     *
     * <p>Placed at 60% back along the head and on its upper third, which is where a
     * snake's are, and angled forward-lateral. They matter out of proportion to
     * their size — a long tube with a mouth at the end is a worm, and eyes are most
     * of what turns it into a face.</p>
     */
    private static int emitEyes(BufferBuilder builder, Vec3 camera,
                                LeviathanSource entity, float partialTick) {
        float flesh = LeviathanShape.flesh(entity.getVisualAgeTicks(partialTick));
        float brightness = LeviathanShape.brightness(entity.getVisualAgeTicks(partialTick), entity.shapeParams().lifetimeTicks());
        if (flesh <= 0.15F || brightness <= 0.02F) {
            return 0;
        }
        int alpha = Math.round(Mth.clamp(brightness, 0.0F, 1.0F) * 255.0F);
        float intensity = Mth.clamp(brightness * 0.34F, 0.0F, 1.0F);

        HeadFrame head = headFrame(entity, partialTick);
        if (head == null) {
            return 0;
        }
        float radius = LeviathanShape.bodyRadius(entity.shapeParams(), 0.045F, entity.getVisualAgeTicks(partialTick), entity.shapeParams().lifetimeTicks());
        Vec3 socket = LeviathanShape.spinePoint(entity.shapeParams(), entity.anchor(partialTick), 0.045F, entity.getVisualAgeTicks(partialTick)).subtract(camera);
        float grown = Mth.clamp((flesh - 0.15F) / 0.4F, 0.0F, 1.0F);
        float size = radius * 0.30F * grown;

        int vertices = 0;
        for (int side = 0; side < 2; side++) {
            float sign = side == 0 ? 1.0F : -1.0F;
            // Out along the flank, up onto the top third, and angled forward.
            Vector3f outward = new Vector3f(
                    head.side.x * sign * 0.86F + head.up.x * 0.42F + head.forward.x * 0.26F,
                    head.side.y * sign * 0.86F + head.up.y * 0.42F + head.forward.y * 0.26F,
                    head.side.z * sign * 0.86F + head.up.z * 0.42F + head.forward.z * 0.26F)
                    .normalize();
            Vec3 base = socket.add(outward.x * radius * 0.72D,
                    outward.y * radius * 0.72D, outward.z * radius * 0.72D);
            Vec3 tip = socket.add(outward.x * (radius * 1.30D),
                    outward.y * (radius * 1.30D), outward.z * (radius * 1.30D));

            Vec3[] path = {base, base.lerp(tip, 0.55D), tip};
            float[] radii = {size * 0.55F, size, size * 0.72F};
            TubeMeshBuilder.Ring[] frames = TubeMeshBuilder.frames(path, radii);
            vertices += TubeMeshBuilder.emit(builder, frames, 8, TubeMeshBuilder.CIRCLE,
                    MODE_EYE, 0.0F, intensity, alpha, 0.0F, 1.0F);
            vertices += TubeMeshBuilder.emitCap(builder, frames[2], tip.add(
                            outward.x * size * 0.6D, outward.y * size * 0.6D,
                            outward.z * size * 0.6D), 8,
                    TubeMeshBuilder.CIRCLE, MODE_EYE, 0.0F, intensity, alpha, 1.0F);
        }
        return vertices;
    }

    /** The head's own axes: where it points, which way is up, which way is sideways. */
    private record HeadFrame(Vector3f forward, Vector3f side, Vector3f up) {
    }

    /**
     * Resolves the head's frame from the spine, rolled with the body so the jaws
     * hinge about the animal's own transverse axis rather than the world's.
     */
    @org.jetbrains.annotations.Nullable
    private static HeadFrame headFrame(LeviathanSource entity, float partialTick) {
        Vec3 head = LeviathanShape.spinePoint(entity.shapeParams(), entity.anchor(partialTick), 0.0F, entity.getVisualAgeTicks(partialTick));
        Vec3 neck = LeviathanShape.spinePoint(entity.shapeParams(), entity.anchor(partialTick), 0.05F, entity.getVisualAgeTicks(partialTick));
        Vector3f forward = new Vector3f(
                (float) (head.x - neck.x), (float) (head.y - neck.y),
                (float) (head.z - neck.z));
        if (forward.lengthSquared() < 1.0E-8F) {
            return null;
        }
        forward.normalize();
        Vector3f side = new Vector3f(0.0F, 1.0F, 0.0F).cross(forward);
        if (side.lengthSquared() < 1.0E-8F) {
            side.set(1.0F, 0.0F, 0.0F);
        }
        side.normalize();
        Vector3f up = new Vector3f(forward).cross(side).normalize();

        // Same bank the body has, so the mouth is not level while the neck is
        // rolled over.
        float roll = LeviathanShape.roll(entity.shapeParams(), 0.02F, entity.getVisualAgeTicks(partialTick));
        float cos = Mth.cos(roll);
        float sin = Mth.sin(roll);
        Vector3f rolledSide = new Vector3f(
                side.x * cos + up.x * sin, side.y * cos + up.y * sin,
                side.z * cos + up.z * sin).normalize();
        Vector3f rolledUp = new Vector3f(
                up.x * cos - side.x * sin, up.y * cos - side.y * sin,
                up.z * cos - side.z * sin).normalize();
        return new HeadFrame(forward, rolledSide, rolledUp);
    }

    /**
     * The two jaws, plus their teeth.
     *
     * <p>Tubes rather than flat plates: from the side a plate is invisible, and the
     * gape is the beat the whole spell builds to. The two do not open by the same
     * angle — see {@link #LOWER_JAW_SHARE}.</p>
     */
    private static int emitJaws(BufferBuilder builder, Vec3 camera,
                                LeviathanSource entity, float partialTick) {
        float flesh = LeviathanShape.flesh(entity.getVisualAgeTicks(partialTick));
        float brightness = LeviathanShape.brightness(entity.getVisualAgeTicks(partialTick), entity.shapeParams().lifetimeTicks());
        if (flesh <= 0.2F || brightness <= 0.02F) {
            return 0;
        }
        HeadFrame head = headFrame(entity, partialTick);
        if (head == null) {
            return 0;
        }
        int alpha = Math.round(Mth.clamp(brightness, 0.0F, 1.0F) * 255.0F);
        float intensity = Mth.clamp(brightness * 0.3F, 0.0F, 1.0F);

        // Hinged at the back of the skull, not at the snout, so the jaws frame the
        // head instead of sprouting off the end of it like a beak.
        Vec3 hinge = LeviathanShape.spinePoint(entity.shapeParams(), entity.anchor(partialTick), 0.075F, entity.getVisualAgeTicks(partialTick)).subtract(camera);
        float open = LeviathanShape.jawOpen(entity.getVisualAgeTicks(partialTick)) * JAW_GAPE;
        float base = LeviathanShape.bodyRadius(entity.shapeParams(), 0.045F, entity.getVisualAgeTicks(partialTick), entity.shapeParams().lifetimeTicks());
        float length = JAW_LENGTH * Math.min(1.0F, flesh * 1.4F);

        int vertices = 0;
        for (int jaw = 0; jaw < 2; jaw++) {
            boolean lower = jaw == 0;
            float share = lower ? LOWER_JAW_SHARE : -(1.0F - LOWER_JAW_SHARE);
            float angle = open * share;
            // Rotate the head's forward axis about the transverse axis: negative
            // swings down, positive up.
            Vector3f axis = rotateAbout(head.forward(), head.up(), -angle);

            Vec3[] path = new Vec3[JAW_RINGS];
            float[] radii = new float[JAW_RINGS];
            for (int ring = 0; ring < JAW_RINGS; ring++) {
                float u = ring / (float) (JAW_RINGS - 1);
                path[ring] = hinge.add(axis.x * length * u,
                        axis.y * length * u, axis.z * length * u);
                // Tapers to a thin edge, so it reads as a jaw rather than a horn.
                radii[ring] = base * (0.80F - u * 0.66F);
            }
            TubeMeshBuilder.Ring[] frames = TubeMeshBuilder.frames(path, radii);
            // The jaw's section is flatter than round: a mandible is a bar, not a
            // rod, and a round one reads as a tusk.
            vertices += TubeMeshBuilder.emit(builder, frames, JAW_SIDES,
                    TubeMeshBuilder.roundedTrapezoid(0.62F, 0.1F),
                    MODE_JAW, 0.0F, intensity, alpha, 0.0F, 1.0F);
            vertices += emitTeeth(builder, hinge, axis, head, lower, length, base,
                    intensity, alpha);
        }
        return vertices;
    }

    /**
     * Fangs and teeth along one jaw's biting edge.
     *
     * <p>Recurved — every tooth leans back toward the throat. That is the single
     * most recognisable thing about a snake's mouth, it is what stops prey backing
     * out, and it cannot be faked in a shader because the silhouette is the point.
     * Two long fangs near the front, then a diminishing row behind them.</p>
     */
    private static int emitTeeth(BufferBuilder builder, Vec3 hinge, Vector3f axis,
                                 HeadFrame head, boolean lower, float jawLength,
                                 float jawRadius, float intensity, int alpha) {
        // Teeth stand off the jaw toward the inside of the mouth.
        Vector3f inward = new Vector3f(head.up()).mul(lower ? 1.0F : -1.0F);
        // And they curve back the way the throat is.
        Vector3f backward = new Vector3f(axis).mul(-1.0F);
        int total = FANGS_PER_JAW + TEETH_PER_JAW;
        int vertices = 0;

        for (int tooth = 0; tooth < total; tooth++) {
            boolean fang = tooth < FANGS_PER_JAW;
            // Fangs sit forward, at a quarter and a third along; the rest fill in
            // behind them.
            float along = fang
                    ? 0.24F + tooth * 0.10F
                    : 0.44F + (tooth - FANGS_PER_JAW)
                            / (float) Math.max(1, TEETH_PER_JAW - 1) * 0.48F;
            float size = jawRadius * (fang ? 0.42F : 0.20F);
            float reach = jawRadius * (fang ? 1.7F : 0.85F);
            // The jaw tapers, so teeth near the tip have less to stand on.
            float jawHere = jawRadius * (0.80F - along * 0.66F);

            for (int side = 0; side < 2; side++) {
                float lateral = (side == 0 ? 1.0F : -1.0F) * jawHere * 0.55F;
                Vec3 root = hinge.add(
                        axis.x * jawLength * along + head.side().x * lateral
                                + inward.x * jawHere * 0.3D,
                        axis.y * jawLength * along + head.side().y * lateral
                                + inward.y * jawHere * 0.3D,
                        axis.z * jawLength * along + head.side().z * lateral
                                + inward.z * jawHere * 0.3D);
                Vec3 mid = root.add(
                        inward.x * reach * 0.55D + backward.x * reach * FANG_RECURVE * 0.3D,
                        inward.y * reach * 0.55D + backward.y * reach * FANG_RECURVE * 0.3D,
                        inward.z * reach * 0.55D + backward.z * reach * FANG_RECURVE * 0.3D);
                Vec3 tip = root.add(
                        inward.x * reach + backward.x * reach * FANG_RECURVE,
                        inward.y * reach + backward.y * reach * FANG_RECURVE,
                        inward.z * reach + backward.z * reach * FANG_RECURVE);

                Vec3[] path = {root, mid, tip};
                float[] radii = {size, size * 0.55F, size * 0.10F};
                TubeMeshBuilder.Ring[] frames = TubeMeshBuilder.frames(path, radii);
                vertices += TubeMeshBuilder.emit(builder, frames, 5,
                        TubeMeshBuilder.CIRCLE, MODE_TOOTH, fang ? 1.0F : 0.4F,
                        intensity, alpha, 0.0F, 1.0F);
            }
        }
        return vertices;
    }

    /** Rotates {@code vector} about {@code axis} by {@code angle} radians. */
    private static Vector3f rotateAbout(Vector3f vector, Vector3f axis, float angle) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        Vector3f cross = new Vector3f(axis).cross(vector);
        float dot = axis.dot(vector);
        return new Vector3f(
                vector.x * cos + cross.x * sin + axis.x * dot * (1.0F - cos),
                vector.y * cos + cross.y * sin + axis.y * dot * (1.0F - cos),
                vector.z * cos + cross.z * sin + axis.z * dot * (1.0F - cos))
                .normalize();
    }

    /** Shake from the bite, 0 when nothing is biting. */
    public static float currentBiteShake(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0.0F;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        float shake = 0.0F;
        for (LeviathanSource entity : SpellLightEmitter.collectLeviathans()) {
            float since = entity.getVisualAgeTicks(partialTick)
                    - LeviathanShape.BITE_TICK;
            if (since < 0.0F || since > SHAKE_TICKS) {
                continue;
            }
            float decay = 1.0F - since / SHAKE_TICKS;
            double distance = Math.sqrt(camera.distanceToSqr(
                    LeviathanShape.spinePoint(entity.shapeParams(), entity.anchor(partialTick), 0.0F, entity.getVisualAgeTicks(partialTick))));
            float reach = (float) Mth.clamp(1.0D - distance / SHAKE_RANGE, 0.0D, 1.0D);
            shake = Math.max(shake, SHAKE_STRENGTH * decay * decay * reach);
        }
        return shake;
    }

    private static BufferBuilder begin() {
        finish(effectBuffer);
        effectBuffer.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        return effectBuffer;
    }

    private static void draw(BufferBuilder builder, ShaderInstance shader, int vertices,
                             float unravel) {
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
            // Closed tubes now, so the far wall would otherwise add through the
            // near one and wash the body out.
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShaderTexture(0, SpellBoltRenderer.NOISE);
            RenderSystem.setShader(() -> shader);
            if (shader.getUniform("BodyState") != null) {
                shader.getUniform("BodyState").set(
                        SpellBoltRenderer.boltTime(), unravel, 0.0F, 0.0F);
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

    /** Everything this renderer changes has to go back exactly as it was. */
}
