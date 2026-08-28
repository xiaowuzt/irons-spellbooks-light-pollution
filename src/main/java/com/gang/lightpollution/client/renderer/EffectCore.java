package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.client.ConstellationShaders;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the bright body at the centre of an effect, in three stacked layers.
 *
 * <p>This exists because six of the seven effects added in one batch drew only their outer
 * structure and left the middle literally empty — jets emerging from nothing, field lines
 * looping around air, a nebula with no star in it. The user's report was exactly that:
 * "中间是空的". Anything with a nucleus, a white dwarf, a neutron star, a binary or a hole
 * needs a body, and the outer structure needs something to be attached to.</p>
 *
 * <p>Three layers — bloom, corona, core — because one falloff cannot be both bright in the
 * middle and soft at the edge. Drawn largest first so the core lands on top.</p>
 *
 * <p>All three are billboards, but the core is shaded as a sphere rather than as a disc: the disc
 * version was reported as looking fake, and it had no limb, no rotation and no surface, so it read
 * as a white circle painted on the air. The sphere is raytraced in the fragment shader on the same
 * quad, which is why this still costs four vertices per body per layer.</p>
 */
public final class EffectCore {
    /** One body queued for drawing. */
    private record Body(Vec3 at, double radius, float r, float g, float b, float intensity) {
    }

    private static final List<Body> QUEUE = new ArrayList<>();
    /** Bloom reaches this many times the body radius. */
    private static final double BLOOM_SCALE = 5.5D;
    /** Corona reaches this many times the body radius. */
    private static final double CORONA_SCALE = 2.4D;

    private EffectCore() {
    }

    /** Queue a body. Nothing is drawn until {@link #flush} runs. */
    public static void add(Vec3 at, double radius, float r, float g, float b,
                           float intensity) {
        if (radius > 0.0D && intensity > 0.003F) {
            QUEUE.add(new Body(at, radius, r, g, b, intensity));
        }
    }

    /**
     * Draw everything queued: the glow first, then the bodies on top of it.
     *
     * <p>Three passes rather than one, because the shader picks its layer from the colour
     * modulator's alpha and that is per draw call, not per vertex. The body is a fourth pass with a
     * different shader entirely, since it is real geometry and the glow is not.</p>
     */
    public static void flush(BufferBuilder builder, Vec3 camera) {
        if (QUEUE.isEmpty()) {
            return;
        }
        ShaderInstance glow = ConstellationShaders.effectCore();
        ShaderInstance surface = ConstellationShaders.starSurface();
        if (glow == null) {
            QUEUE.clear();
            return;
        }
        try {
            layer(builder, camera, glow, BLOOM_SCALE, 0.0F);
            layer(builder, camera, glow, CORONA_SCALE, 1.0F);
            if (surface == null) {
                // No sphere shader, so fall back to the flat core rather than drawing nothing.
                layer(builder, camera, glow, 1.0D, 2.0F);
            } else {
                bodies(builder, camera, surface);
            }
        } finally {
            QUEUE.clear();
        }
    }

    /**
     * The bodies themselves, as real spheres.
     *
     * <p>Culling is enabled for this pass alone, which is the point of using real geometry: the far
     * hemisphere is dropped, so every fragment is drawn exactly once and the additive blend does not
     * double the brightness through the middle of the ball.</p>
     */
    private static void bodies(BufferBuilder builder, Vec3 camera, ShaderInstance shader) {
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        int vertices = 0;
        for (Body body : QUEUE) {
            vertices += SphereMeshBuilder.emit(builder, camera, body.at(), body.radius(),
                    CurveRibbon.pack(body.r(), body.g(), body.b(), body.intensity()));
        }
        if (vertices <= 0) {
            release(builder);
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
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShader(() -> shader);
            BufferUploader.drawWithShader(builder.end());
        } catch (RuntimeException | LinkageError failure) {
            release(builder);
            throw failure;
        } finally {
            RenderSystem.disableCull();
        }
    }

    private static void layer(BufferBuilder builder, Vec3 camera, ShaderInstance shader,
                              double scale, float selector) {
        // POSITION_TEX_COLOR_NORMAL, because the core layer shades a sphere and needs a
        // world-space frame to do it in. The normal carries the billboard's right vector; the
        // fragment stage gets the other two axes from the view ray.
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        int vertices = 0;
        for (Body body : QUEUE) {
            vertices += disc(builder, camera, body.at(), body.radius() * scale,
                    CurveRibbon.pack(body.r(), body.g(), body.b(), body.intensity()));
        }
        if (vertices <= 0) {
            release(builder);
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
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, selector);
            RenderSystem.setShader(() -> shader);
            BufferUploader.drawWithShader(builder.end());
        } catch (RuntimeException | LinkageError failure) {
            release(builder);
            throw failure;
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static int disc(BufferBuilder builder, Vec3 camera, Vec3 at, double radius,
                            int colour) {
        Vec3 toCamera = camera.subtract(at);
        if (toCamera.lengthSqr() < 1.0e-8D) {
            return 0;
        }
        Vec3 forward = toCamera.normalize();
        Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0e-8D) {
            right = forward.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        Vec3 axis = right.normalize();
        right = axis.scale(radius);
        Vec3 up = axis.cross(forward).normalize().scale(radius);

        float cx = (float) (at.x - camera.x);
        float cy = (float) (at.y - camera.y);
        float cz = (float) (at.z - camera.z);
        corner(builder, cx, cy, cz, right, up, axis, -1, -1, 0.0F, 0.0F, colour);
        corner(builder, cx, cy, cz, right, up, axis, 1, -1, 1.0F, 0.0F, colour);
        corner(builder, cx, cy, cz, right, up, axis, 1, 1, 1.0F, 1.0F, colour);
        corner(builder, cx, cy, cz, right, up, axis, -1, 1, 0.0F, 1.0F, colour);
        return 4;
    }

    private static void corner(BufferBuilder builder, float cx, float cy, float cz,
                               Vec3 right, Vec3 up, Vec3 axis, int sx, int sy,
                               float u, float v, int colour) {
        builder.vertex(cx + (float) (right.x * sx + up.x * sy),
                        cy + (float) (right.y * sx + up.y * sy),
                        cz + (float) (right.z * sx + up.z * sy))
                .uv(u, v)
                .color((colour >> 16) & 0xFF, (colour >> 8) & 0xFF,
                        colour & 0xFF, (colour >>> 24) & 0xFF)
                // The unscaled right vector. The shader rebuilds the up axis from this and the
                // view ray, so it must be the unit direction rather than the radius-scaled edge.
                .normal((float) axis.x, (float) axis.y, (float) axis.z)
                .endVertex();
    }

    private static void release(BufferBuilder builder) {
        if (!builder.building()) {
            return;
        }
        try {
            BufferBuilder.RenderedBuffer rendered = builder.end();
            if (rendered != null) {
                rendered.release();
            }
        } catch (RuntimeException ignored) {
            // Nothing useful to do — the buffer is reset by its owner next frame.
        }
    }
}
