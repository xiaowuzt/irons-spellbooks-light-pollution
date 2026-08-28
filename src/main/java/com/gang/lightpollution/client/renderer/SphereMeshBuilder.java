package com.gang.lightpollution.client.renderer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Builds a real sphere: actual geometry with a true outward normal per vertex.
 *
 * <p>This exists because the central bodies were billboards and read as such. The last attempt
 * raytraced a sphere on a camera-facing quad, which fixed the shading but not the impression — the
 * shaded part is only the innermost fifth of what is on screen, since the bloom around it reaches
 * five and a half times the body radius. Ninety-seven percent of the visible area was still a flat
 * radial falloff, so it still looked painted on.</p>
 *
 * <p>A real sphere also gets the things a billboard can never have: a silhouette that is correct
 * from every angle, a near hemisphere that hides the far one, and surface features that stay put on
 * the surface as the camera moves rather than swimming with the view.</p>
 *
 * <p>Vertex layout is {@code POSITION_TEX_COLOR_NORMAL}, matching the tube builder. UV0 is the
 * longitude and latitude, so a shader can put a texture or a noise field on the surface. Positions
 * are camera-relative, like every other world renderer here.</p>
 */
public final class SphereMeshBuilder {
    /**
     * Latitude bands and longitude divisions at the default detail.
     *
     * <p>Twenty by thirty-two is 640 quads. That sounds like a lot for one body until you notice
     * these are the only spheres on screen and there are rarely more than two — the tree in this
     * same mod is seven thousand quads.</p>
     */
    public static final int RINGS = 20;
    public static final int SECTORS = 32;

    private SphereMeshBuilder() {
    }

    /**
     * Emit a sphere. Returns the vertex count added.
     *
     * <p>The poles collapse to a point, so their rows are emitted as quads with a doubled edge
     * rather than as triangles. That keeps the whole mesh in one QUADS draw, which is what lets a
     * body cost a single call.</p>
     *
     * @param colour packed as {@link CurveRibbon#pack}: rgb is the body colour, alpha the intensity
     */
    public static int emit(BufferBuilder builder, Vec3 camera, Vec3 centre, double radius,
                           int rings, int sectors, int colour) {
        if (rings < 2 || sectors < 3 || radius <= 0.0D) {
            return 0;
        }
        float cx = (float) (centre.x - camera.x);
        float cy = (float) (centre.y - camera.y);
        float cz = (float) (centre.z - camera.z);
        int vertices = 0;

        for (int ring = 0; ring < rings; ring++) {
            float phi0 = (float) (Math.PI * ring / rings);
            float phi1 = (float) (Math.PI * (ring + 1) / rings);
            float v0 = ring / (float) rings;
            float v1 = (ring + 1) / (float) rings;

            for (int sector = 0; sector < sectors; sector++) {
                float theta0 = (float) (Mth.TWO_PI * sector / sectors);
                float theta1 = (float) (Mth.TWO_PI * (sector + 1) / sectors);
                float u0 = sector / (float) sectors;
                float u1 = (sector + 1) / (float) sectors;

                // Longitude before latitude, which is the winding whose face normal points out of
                // the sphere. Worth spelling out because the other order compiles, draws, and is
                // wrong in a way that looks like a shading bug: with back faces culled it keeps the
                // far hemisphere instead of the near one, every normal then faces away from the
                // eye, dot(normal, view) clamps to zero, and the limb term goes flat — which is
                // exactly the flat-looking body this class was written to replace.
                corner(builder, cx, cy, cz, radius, phi0, theta0, u0, v0, colour);
                corner(builder, cx, cy, cz, radius, phi0, theta1, u1, v0, colour);
                corner(builder, cx, cy, cz, radius, phi1, theta1, u1, v1, colour);
                corner(builder, cx, cy, cz, radius, phi1, theta0, u0, v1, colour);
                vertices += 4;
            }
        }
        return vertices;
    }

    /** At the default detail. */
    public static int emit(BufferBuilder builder, Vec3 camera, Vec3 centre, double radius,
                           int colour) {
        return emit(builder, camera, centre, radius, RINGS, SECTORS, colour);
    }

    private static void corner(BufferBuilder builder, float cx, float cy, float cz, double radius,
                               float phi, float theta, float u, float v, int colour) {
        // Polar angle from +Y, so the seam and the poles land where a star's axis would.
        float sinPhi = Mth.sin(phi);
        float nx = sinPhi * Mth.cos(theta);
        float ny = Mth.cos(phi);
        float nz = sinPhi * Mth.sin(theta);

        builder.vertex(cx + (float) (nx * radius),
                        cy + (float) (ny * radius),
                        cz + (float) (nz * radius))
                .uv(u, v)
                .color((colour >> 16) & 0xFF, (colour >> 8) & 0xFF,
                        colour & 0xFF, (colour >>> 24) & 0xFF)
                // On a sphere centred at the origin the outward normal is the unit position, so
                // this is the exact geometric normal rather than an approximation of it.
                .normal(nx, ny, nz)
                .endVertex();
    }
}
