package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.entity.WorldTreeEntity;
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
 * Draws World Tree: half-buried roots, a buttressed trunk, six orders of branches,
 * and a canopy of leaf clusters on the outer two orders.
 *
 * <p>The first version was a stump with forty leaves on it, and the numbers say why:
 * the whole crown was nine branch ribbons and forty billboard diamonds, about
 * eighty-five quads, while the trunk alone was eighty-four. One solid post and a
 * sprinkle. This has 2548 branches across six levels and about 13600 leaf clusters
 * spread over 2268 twigs — six leaves per twig, which is what a real twig carries.
 * Piling more onto fewer twigs was the previous mistake: it made each branch read as
 * a bottle brush instead of making the tree read as full.</p>
 *
 * <p>Leaves hang only on the outer two orders, which is what produces the
 * hollow-interior, dense-shell structure a real crown has, along with the gaps at
 * its boundary.</p>
 *
 * <p>Leaves are not camera-facing. That, plus a real leaf outline instead of a
 * diamond, is most of the difference between foliage and floating confetti. It is
 * possible at all because the hue moved out of a uniform and into the vertex colour:
 * a uniform cannot vary inside a draw call, so the old version needed one draw call
 * per leaf and could never have afforded thousands of them.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class WorldTreeWorldRenderer {
    private static final int BUFFER_CAPACITY = 4_194_304;
    /** Vertical divisions of the trunk. */
    private static final int TRUNK_RINGS = 22;
    /** Sides on a branch tube, by level. Twigs need very few. */
    private static final int[] BRANCH_SIDES = {8, 6, 5, 4, 3, 3};
    /**
     * Rings along a branch tube, by level.
     *
     * <p>The finest order gets two — a single straight segment. There are 1701 of
     * them and each is about a block and a half long and a tenth of a block thick,
     * so subdividing them buys nothing but five thousand extra quads a frame.</p>
     */
    private static final int[] BRANCH_RINGS = {5, 4, 3, 3, 3, 2};
    /** How far a branch bows upward along its length, as a fraction of it. */
    private static final float BRANCH_BOW = 0.11F;
    /** Sides on a root tube. */
    private static final int ROOT_SIDES = 7;
    private static final double RENDER_DISTANCE = 240.0D;
    private static final double RENDER_DISTANCE_SQR = RENDER_DISTANCE * RENDER_DISTANCE;
    /** Ticks the trunk's eruption shake lasts. */
    private static final float SHAKE_TICKS = 18.0F;
    /** Peak shake amplitude, in degrees. */
    private static final float SHAKE_STRENGTH = 5.0F;
    /** Distance at which the eruption can no longer be felt, in blocks. */
    private static final double SHAKE_RANGE = 60.0D;

    /** Shader branch selectors, packed into the vertex colour's green channel. */
    private static final float MODE_ROOT = 0.12F;
    private static final float MODE_TRUNK = 0.37F;
    private static final float MODE_BRANCH = 0.62F;
    private static final float MODE_LEAF = 0.90F;

    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);

    private WorldTreeWorldRenderer() {
    }

    /** Roots: half-buried tubes following the terrain out from the trunk's flare. */
    private static int emitRoots(BufferBuilder builder, Vec3 camera,
                                 WorldTreeEntity entity, float partialTick) {
        float progress = entity.rootProgress(partialTick);
        float fade = entity.fade(partialTick);
        if (progress <= 0.01F || fade <= 0.01F) {
            return 0;
        }
        int alpha = Math.round(Mth.clamp(fade, 0.0F, 1.0F) * 255.0F);
        float intensity = 0.30F;
        int vertices = 0;

        for (int root = 0; root < WorldTreeEntity.ROOT_COUNT; root++) {
            int rings = Math.max(2,
                    Math.round(WorldTreeEntity.ROOT_SEGMENTS * progress) + 1);
            Vec3[] path = new Vec3[rings];
            float[] radii = new float[rings];
            for (int ring = 0; ring < rings; ring++) {
                float t = ring / (float) (rings - 1) * progress;
                path[ring] = entity.rootPoint(root, t, partialTick).subtract(camera);
                radii[ring] = entity.rootRadius(root, t);
            }
            TubeMeshBuilder.Ring[] frames = TubeMeshBuilder.frames(path, radii);
            vertices += TubeMeshBuilder.emit(builder, frames, ROOT_SIDES,
                    TubeMeshBuilder.CIRCLE, MODE_ROOT, 0.0F, intensity, alpha,
                    0.0F, progress);

            Vec3 beyond = path[rings - 1].subtract(path[Math.max(0, rings - 2)]);
            double length = beyond.length();
            if (length > 1.0E-4D) {
                Vec3 tip = path[rings - 1].add(beyond.scale(0.8D / length));
                vertices += TubeMeshBuilder.emitCap(builder, frames[rings - 1], tip,
                        ROOT_SIDES, TubeMeshBuilder.CIRCLE, MODE_ROOT, 0.0F,
                        intensity, alpha, progress);
            }
        }
        return vertices;
    }

    /** Where trunk ring {@code x} sits, 0 to 1. Packed toward the flare. */
    private static float trunkWarp(float x) {
        return 0.4F * x + 0.6F * x * x;
    }

    /**
     * The trunk: round, with buttress lobes that deepen toward the ground and blend
     * out above the flare, and a paraboloid taper above that.
     *
     * <p>Sixteen sides rather than seven. A seven-sided prism has visible flats at
     * any distance the player can walk to, and the old version leaned on that
     * polygon count to suggest facets — which read as a crude post, not as bark.</p>
     */
    private static int emitTrunk(BufferBuilder builder, Vec3 camera,
                                 WorldTreeEntity entity, float partialTick) {
        float progress = entity.trunkProgress(partialTick);
        float fade = entity.fade(partialTick);
        if (progress <= 0.01F || fade <= 0.01F) {
            return 0;
        }
        int alpha = Math.round(Mth.clamp(fade, 0.0F, 1.0F) * 255.0F);
        float intensity = 0.30F;
        Vec3 seed = entity.seedPoint(partialTick).subtract(camera);
        float height = WorldTreeEntity.TRUNK_HEIGHT * progress;

        // Three extra rings above the top, tapering to a point: the trunk continues
        // as a leader into the crown, and without them the tube is left open and you
        // can see down inside it while it is still growing.
        int rings = TRUNK_RINGS + 3;
        Vec3[] path = new Vec3[rings];
        float[] radii = new float[rings];
        for (int ring = 0; ring < TRUNK_RINGS; ring++) {
            float y = trunkWarp(ring / (float) (TRUNK_RINGS - 1)) * height;
            path[ring] = seed.add(0.0D, y, 0.0D);
            radii[ring] = WorldTreeEntity.trunkRadius(y);
        }
        float topRadius = radii[TRUNK_RINGS - 1];
        for (int extra = 0; extra < 3; extra++) {
            float up = (extra + 1) / 3.0F;
            path[TRUNK_RINGS + extra] = seed.add(0.0D, height + up * 2.6D, 0.0D);
            radii[TRUNK_RINGS + extra] = topRadius * (1.0F - up * 0.92F);
        }

        // The section needs the ring's real height to know how deep the buttresses
        // are there. Reading it back off the path is exact; recomputing it from the
        // warp would be wrong, because the path has the leader rings appended and so
        // alongUnit no longer maps onto the warp.
        final Vec3[] rows = path;
        final double baseY = seed.y;
        TubeMeshBuilder.Section section = (angle, alongUnit, out) -> {
            int index = Mth.clamp(Math.round(alongUnit * (rows.length - 1)),
                    0, rows.length - 1);
            float y = (float) (rows[index].y - baseY);
            float depth = WorldTreeEntity.buttressDepth(y);
            float lobed = 1.0F + depth
                    * Mth.cos(angle * WorldTreeEntity.BUTTRESS_LOBES);
            out.set(Mth.cos(angle) * lobed, Mth.sin(angle) * lobed);
        };
        return TubeMeshBuilder.emit(builder, TubeMeshBuilder.frames(path, radii),
                WorldTreeEntity.TRUNK_SIDES, section, MODE_TRUNK, 0.0F,
                intensity, alpha, 0.0F, height);
    }

    /** Every branch, all four orders, as tapered tubes. */
    private static int emitBranches(BufferBuilder builder, Vec3 camera,
                                    WorldTreeEntity entity, float partialTick) {
        float progress = entity.branchProgress(partialTick);
        float fade = entity.fade(partialTick);
        if (progress <= 0.01F || fade <= 0.01F) {
            return 0;
        }
        int alpha = Math.round(Mth.clamp(fade, 0.0F, 1.0F) * 255.0F);
        float intensity = 0.30F;
        Vec3 seed = entity.seedPoint(partialTick).subtract(camera);
        int vertices = 0;

        for (WorldTreeEntity.Limb limb : entity.skeleton()) {
            float grown = limb.grown(progress);
            if (grown <= 0.02F) {
                continue;
            }
            int level = Math.min(BRANCH_SIDES.length - 1, limb.level());
            Vec3 from = seed.add(limb.from());
            Vec3 tip = seed.add(limb.from().lerp(limb.to(), grown));
            double length = from.distanceTo(tip);
            // Branches bow upward along their length rather than running dead
            // straight. A straight tube reads as a dowel; the bow is the same
            // light-seeking bias that sets the branch angles, applied within a limb.
            double bow = length * BRANCH_BOW;

            int rings = BRANCH_RINGS[level];
            Vec3[] path = new Vec3[rings];
            float[] radii = new float[rings];
            float tipRadius = Mth.lerp(grown, limb.fromRadius(), limb.toRadius());
            for (int ring = 0; ring < rings; ring++) {
                float t = ring / (float) (rings - 1);
                // Parabolic arch, zero at both ends and peaking in the middle.
                double lift = bow * 4.0D * t * (1.0F - t);
                path[ring] = from.lerp(tip, t).add(0.0D, lift, 0.0D);
                radii[ring] = Mth.lerp(t, limb.fromRadius(), tipRadius);
            }
            // aux carries how far out this limb is, so the shader can lighten the
            // bark toward the twigs the way real bark thins.
            float depth = limb.level() / (float) (WorldTreeEntity.BRANCH_LEVELS - 1);
            vertices += TubeMeshBuilder.emit(builder,
                    TubeMeshBuilder.frames(path, radii), BRANCH_SIDES[level],
                    TubeMeshBuilder.CIRCLE, MODE_BRANCH, depth, intensity, alpha,
                    0.0F, (float) length);
        }
        return vertices;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        // A shader pack composites over the main target after this
        // stage, so under one the draw has to move to AFTER_LEVEL.
        if (!SpellRenderStage.shouldDraw(
                event, RenderLevelStageEvent.Stage.AFTER_WEATHER)) {
            return;
        }
        List<WorldTreeEntity> effects = SpellLightEmitter.collectWorldTrees();
        if (effects.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance shader = ConstellationShaders.worldTree();
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

            for (WorldTreeEntity entity : effects) {
                if (camera.distanceToSqr(entity.seedPoint(partialTick))
                        > RENDER_DISTANCE_SQR) {
                    continue;
                }
                float hardened = entity.hardened(partialTick);

                BufferBuilder wood = begin();
                int vertices = 0;
                vertices += emitRoots(wood, camera, entity, partialTick);
                vertices += emitTrunk(wood, camera, entity, partialTick);
                vertices += emitBranches(wood, camera, entity, partialTick);
                draw(wood, shader, vertices, hardened, true);

                // Leaves go in their own pass with culling off. They have to be
                // two-sided — a canopy where every leaf facing away is invisible
                // loses half its mass — but the wood is closed tubes and needs
                // culling, so the two cannot share a draw.
                BufferBuilder foliage = begin();
                int leaves = emitLeaves(foliage, camera, entity, partialTick);
                draw(foliage, shader, leaves, hardened, false);
            }
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            state.restore();
        }
    }

    /**
     * Leaf clusters on the twigs.
     *
     * <p>Each quad is a cluster card, not a single leaf: the shader draws several
     * leaflets inside it. That is how fifteen hundred quads read as the ten thousand
     * or so leaves a crown of this size wants, and it is also how the leaf keeps a
     * plausible size relative to the twig bearing it — a real leaf is a few times the
     * twig's diameter, which on a 0.7-block twig would mean multi-block leaves, and
     * that is exactly the giant-diamond look the old version had.</p>
     *
     * <p>Oriented by the twig and the phyllotactic spiral, with a droop. Not
     * camera-facing: a billboard swivels as the player walks, so a crown of them
     * shimmers and never self-occludes, which is why the old leaves read as floating
     * rather than as attached.</p>
     */
    private static int emitLeaves(BufferBuilder builder, Vec3 camera,
                                  WorldTreeEntity entity, float partialTick) {
        float crown = entity.crownProgress(partialTick);
        float branches = entity.branchProgress(partialTick);
        float fade = entity.fade(partialTick);
        if (crown <= 0.01F || fade <= 0.01F) {
            return 0;
        }
        int alpha = Math.round(Mth.clamp(fade, 0.0F, 1.0F) * 255.0F);
        float intensity = 0.30F;
        Vec3 seed = entity.seedPoint(partialTick).subtract(camera);
        WorldTreeEntity.Limb[] twigs = entity.twigs();
        int vertices = 0;

        for (WorldTreeEntity.Leaf leaf : entity.leaves()) {
            WorldTreeEntity.Limb twig = twigs[leaf.twig()];
            float grown = twig.grown(branches);
            if (grown <= leaf.along()) {
                // The twig has not reached this leaf's node yet.
                continue;
            }
            Vec3 direction = twig.direction();
            // Same bow the twig is drawn with, or the leaves hang off the chord
            // instead of off the branch.
            double lift = twig.from().distanceTo(twig.to()) * BRANCH_BOW * 4.0D
                    * leaf.along() * (1.0D - leaf.along());
            Vec3 node = seed.add(twig.from().lerp(twig.to(), leaf.along()))
                    .add(0.0D, lift, 0.0D);

            // A basis across the twig, spun to this leaf's phyllotactic angle.
            Vec3 reference = Math.abs(direction.y) < 0.9D
                    ? new Vec3(0.0D, 1.0D, 0.0D)
                    : new Vec3(1.0D, 0.0D, 0.0D);
            Vec3 side = reference.cross(direction).normalize();
            Vec3 other = direction.cross(side).normalize();
            Vec3 lateral = side.scale(Mth.cos(leaf.spin()))
                    .add(other.scale(Mth.sin(leaf.spin())));

            // The blade leans outward from the twig, forward along it, and droops.
            Vec3 axis = lateral.scale(0.78D).add(direction.scale(0.42D))
                    .add(0.0D, -0.34D, 0.0D).normalize();
            Vec3 normal = axis.cross(lateral);
            if (normal.lengthSqr() < 1.0E-8D) {
                normal = axis.cross(direction);
            }
            if (normal.lengthSqr() < 1.0E-8D) {
                continue;
            }
            normal = normal.normalize();
            Vec3 across = normal.cross(axis).normalize();

            float size = leaf.size() * crown;
            float length = size * 2.4F;
            float half = size * 0.85F;
            Vec3 base = node.add(lateral.scale(twig.toRadius() * 0.8D));
            Vec3 tip = base.add(axis.scale(length));

            vertices += leafQuad(builder, base, tip, across, normal, half,
                    leaf.hue(), intensity, alpha);
        }
        return vertices;
    }

    /** One cluster card. UV0.y runs petiole to tip, UV0.x across the blade. */
    private static int leafQuad(BufferBuilder builder, Vec3 base, Vec3 tip,
                                Vec3 across, Vec3 normal, float half, float hue,
                                float intensity, int alpha) {
        Vec3 offset = across.scale(half);
        vertex(builder, base.subtract(offset), 0.0F, 0.0F, normal, MODE_LEAF, hue,
                intensity, alpha);
        vertex(builder, tip.subtract(offset), 0.0F, 1.0F, normal, MODE_LEAF, hue,
                intensity, alpha);
        vertex(builder, tip.add(offset), 1.0F, 1.0F, normal, MODE_LEAF, hue,
                intensity, alpha);
        vertex(builder, base.add(offset), 1.0F, 0.0F, normal, MODE_LEAF, hue,
                intensity, alpha);
        return 4;
    }

    /** Shake from the trunk spearing up, 0 when nothing is erupting. */
    public static float currentTrunkShake(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0.0F;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        float shake = 0.0F;
        for (WorldTreeEntity entity : SpellLightEmitter.collectWorldTrees()) {
            float since = entity.getVisualAgeTicks(partialTick)
                    - WorldTreeEntity.TRUNK_END_TICK;
            if (since < 0.0F || since > SHAKE_TICKS) {
                continue;
            }
            float decay = 1.0F - since / SHAKE_TICKS;
            double distance = Math.sqrt(
                    camera.distanceToSqr(entity.seedPoint(partialTick)));
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

    private static void vertex(BufferBuilder builder, Vec3 at, float u, float v,
                               Vec3 normal, float mode, float aux, float intensity,
                               int alpha) {
        builder.vertex((float) at.x, (float) at.y, (float) at.z)
                .uv(v, u)
                .color(Math.round(Mth.clamp(aux, 0.0F, 1.0F) * 255.0F),
                        Math.round(Mth.clamp(mode, 0.0F, 1.0F) * 255.0F),
                        Math.round(Mth.clamp(intensity, 0.0F, 1.0F) * 255.0F),
                        alpha)
                .normal((float) normal.x, (float) normal.y, (float) normal.z)
                .endVertex();
    }

    private static void draw(BufferBuilder builder, ShaderInstance shader, int vertices,
                             float hardened, boolean cull) {
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
            // Trunk, branches and roots are closed tubes, so their far walls would
            // otherwise add through the near ones and wash the whole tree out.
            if (cull) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShaderTexture(0, SpellBoltRenderer.NOISE);
            RenderSystem.setShader(() -> shader);
            if (shader.getUniform("TreeState") != null) {
                shader.getUniform("TreeState").set(
                        SpellBoltRenderer.boltTime(), hardened, 0.0F, 0.0F);
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
