package com.gang.lightpollution.client.renderer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Emits lightning bolts as camera-facing ribbons for the shared bolt program.
 *
 * <p>The approach is taken from Some of FX
 * (github.com/YangMao-Minister/some_of_fx, MIT, (c) 2026 Pizuka): the bolt is a
 * signed distance to a straight centreline, and the jitter comes from displacing
 * the sample point rather than from moving vertices. The width is therefore the
 * shader's to choose, and the detail is per pixel instead of per segment.</p>
 *
 * <p>The one thing that has to be arranged here is that the ribbon's width is
 * constant <em>on screen</em>. A ribbon a fixed number of blocks wide thins to a
 * thread at range and swells to a plank up close, which is exactly what made the
 * geometry-based bolts look wrong; scaling the width with the distance to the
 * camera fixes it.</p>
 */
public final class SpellBoltRenderer {
    /**
     * The noise the bolt's wander is read from. A real tile rather than a hash:
     * sin-based hashes band visibly at the low frequencies that set the bolt's
     * overall path, and that banding is what made the first version read as a
     * smooth ribbon rather than as lightning.
     */
    public static final net.minecraft.resources.ResourceLocation NOISE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    com.gang.lightpollution.ExampleMod.MODID, "textures/effect/noise_tile.png");

    /** Ribbon width per block of distance. Sets the bolt's on-screen thickness. */
    private static final float WIDTH_PER_DISTANCE = 0.045F;
    /** Narrowest a ribbon may get, in blocks. */
    private static final float MIN_HALF_WIDTH = 0.35F;
    /** Widest a ribbon may get, in blocks, so a nearby bolt cannot fill the view. */
    private static final float MAX_HALF_WIDTH = 9.0F;

    private SpellBoltRenderer() {
    }

    /**
     * Writes one bolt into the buffer. Returns the vertex count added.
     *
     * @param seed  varies the bolt's path; two bolts with the same seed and the
     *              same endpoints are identical
     * @param grown how much of the bolt exists yet, 0 to 1
     */
    public static int emit(BufferBuilder builder, Vec3 camera, Vec3 from, Vec3 to,
                           float intensity, float fade, int seed, float grown) {
        if (intensity <= 0.01F || fade <= 0.01F || grown <= 0.01F) {
            return 0;
        }
        Vector3f along = new Vector3f(
                (float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
        if (along.lengthSquared() < 1.0E-6F) {
            return 0;
        }
        along.mul(Mth.clamp(grown, 0.0F, 1.0F));

        // Midpoint distance rather than either end: a bolt running away from the
        // camera would otherwise be sized by whichever end happened to be passed
        // first.
        Vec3 middle = from.add(to).scale(0.5D);
        float distance = (float) Math.sqrt(camera.distanceToSqr(middle));
        float halfWidth = Mth.clamp(distance * WIDTH_PER_DISTANCE,
                MIN_HALF_WIDTH, MAX_HALF_WIDTH);

        Vector3f toCamera = new Vector3f(
                (float) (camera.x - middle.x),
                (float) (camera.y - middle.y),
                (float) (camera.z - middle.z));
        Vector3f side = new Vector3f(along).cross(toCamera);
        if (side.lengthSquared() < 1.0E-6F) {
            // Looking straight down the bolt; any perpendicular will do.
            side.set(along.z, along.x, along.y).cross(along);
        }
        if (side.lengthSquared() < 1.0E-6F) {
            return 0;
        }
        side.normalize().mul(halfWidth);

        int packed = color(
                (seed & 0x3F) / 64.0F,
                0.0F,
                Mth.clamp(intensity * 0.25F, 0.0F, 1.0F),
                Mth.clamp(fade, 0.0F, 1.0F));

        float originX = (float) (from.x - camera.x);
        float originY = (float) (from.y - camera.y);
        float originZ = (float) (from.z - camera.z);
        float endX = originX + along.x;
        float endY = originY + along.y;
        float endZ = originZ + along.z;

        vertex(builder, originX - side.x, originY - side.y, originZ - side.z,
                0.0F, 0.0F, packed);
        vertex(builder, endX - side.x, endY - side.y, endZ - side.z,
                1.0F, 0.0F, packed);
        vertex(builder, endX + side.x, endY + side.y, endZ + side.z,
                1.0F, 1.0F, packed);
        vertex(builder, originX + side.x, originY + side.y, originZ + side.z,
                0.0F, 1.0F, packed);
        return 4;
    }

    /** Wall-clock seconds, wrapped, for the bolt program's flicker uniform. */
    public static float boltTime() {
        return (System.currentTimeMillis() % 600_000L) / 1000.0F;
    }

    private static void vertex(BufferBuilder builder,
                               float x, float y, float z, float u, float v, int color) {
        builder.vertex(x, y, z).uv(u, v)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF,
                        color & 0xFF, (color >>> 24) & 0xFF)
                .endVertex();
    }

    private static int color(float red, float green, float blue, float alpha) {
        int r = Math.round(Mth.clamp(red, 0.0F, 1.0F) * 255.0F);
        int g = Math.round(Mth.clamp(green, 0.0F, 1.0F) * 255.0F);
        int b = Math.round(Mth.clamp(blue, 0.0F, 1.0F) * 255.0F);
        int a = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
