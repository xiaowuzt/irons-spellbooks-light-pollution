package com.gang.lightpollution.client.renderer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Builds closed tubes along a path, with a true surface normal per vertex and a
 * cross-section that does not have to be a circle.
 *
 * <p>Three things here exist because of specific failures. The section is
 * pluggable because a snake is not a round pipe — its belly is a flat scute plate
 * about half the body width, and a circular tube can never read as one. The frame
 * can be driven by an explicit roll because parallel transport leaves "which way
 * is up" drifting along the path, which would spiral that flat belly onto the
 * flanks. And the normal is the real geometric normal rather than the offset
 * direction, because for a non-circular section those are different, and because a
 * per-vertex facing term interpolates linearly across each quad and creases at
 * every edge — which is what makes a 16-sided tube look like 16 flat strips.</p>
 *
 * <p>Vertex layout is {@code POSITION_TEX_COLOR_NORMAL}. UV0.x is distance along
 * the tube and may exceed 1 so the shader can tile along it; UV0.y is the fraction
 * around the circumference. Colour is (aux, mode, intensity, fade).</p>
 *
 * <p>Paths are expected in <em>camera-relative</em> space — subtract the camera
 * before calling. Nothing here touches the camera, so the vertices come out in
 * whatever space the path was given in.</p>
 */
public final class TubeMeshBuilder {
    private TubeMeshBuilder() {
    }

    /**
     * A cross-section outline. Writes the unit offset at {@code angle} into
     * {@code out}, where {@code out.x} rides the ring's right axis and
     * {@code out.y} its up axis. Angle 0 is the right flank and {@code PI / 2} is
     * the dorsum.
     *
     * <p>{@code alongUnit} is how far along the span this ring sits, 0 to 1. It is
     * there so one section can morph — the snake's head is a broad flat spade and
     * its body a rounded trapezoid, and those have to blend into each other rather
     * than meet at a step.</p>
     */
    @FunctionalInterface
    public interface Section {
        void offset(float angle, float alongUnit, Vector2f out);
    }

    /** A plain circle, for anything that really is a pipe. */
    public static final Section CIRCLE =
            (angle, alongUnit, out) -> out.set(Mth.cos(angle), Mth.sin(angle));

    /**
     * A snake's section: a rounded trapezoid — rounded dorsum narrowing to a flat
     * ventral plate.
     *
     * @param heightRatio section height as a fraction of its width; terrestrial
     *                    snakes measure 1.0 to 1.2
     * @param ventralTaper how much narrower the belly is than the back, 0 to 1
     */
    public static Section roundedTrapezoid(float heightRatio, float ventralTaper) {
        return (angle, alongUnit, out) ->
                trapezoid(angle, heightRatio, ventralTaper, out);
    }

    /**
     * Writes a rounded trapezoid outline. Shared so a morphing section can call it
     * for both of the shapes it blends between.
     */
    public static void trapezoid(float angle, float heightRatio, float ventralTaper,
                                 Vector2f out) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        // A superellipse: exponent 2 is a circle, higher squares it off. The dorsum
        // gets a mild exponent and the venter a high one, which is what flattens the
        // belly into a plate.
        float exponent = sin >= 0.0F ? 2.2F : 3.6F;
        float sum = (float) (Math.pow(Math.abs(cos), exponent)
                + Math.pow(Math.abs(sin), exponent));
        float scale = sum < 1.0E-6F ? 1.0F : (float) Math.pow(sum, -1.0F / exponent);
        float x = cos * scale;
        float y = sin * scale;
        if (sin < 0.0F) {
            x *= 1.0F - ventralTaper * (-sin);
        }
        // Shifted up so the widest point sits above the mid-height, as a real
        // snake's does.
        out.set(x, y * heightRatio + 0.06F * heightRatio);
    }

    /**
     * A ring of the tube. {@code tangent} is stored because the true normal needs
     * both directions across the surface, not just the outward offset.
     */
    public record Ring(Vec3 centre, float radius, Vector3f right, Vector3f up,
                       Vector3f tangent) {
    }

    /**
     * Resolves the frame for a path.
     *
     * <p>With {@code rolls} null the reference axis is carried forward from ring to
     * ring, which is right for anything with no inherent up — a branch, a root.
     * With {@code rolls} supplied the frame is rebuilt each ring from world up and
     * then rotated by that ring's roll, so an animal's belly stays down however the
     * path bends.</p>
     *
     * @param path  points along the tube, at least two
     * @param radii radius at each point, same length as {@code path}
     * @param rolls roll about the tangent at each point in radians, or null
     */
    public static Ring[] frames(Vec3[] path, float[] radii, @Nullable float[] rolls) {
        Ring[] rings = new Ring[path.length];
        Vector3f carried = null;

        for (int index = 0; index < path.length; index++) {
            Vec3 ahead = index + 1 < path.length ? path[index + 1] : path[index];
            Vec3 behind = index > 0 ? path[index - 1] : path[index];
            Vector3f tangent = new Vector3f(
                    (float) (ahead.x - behind.x),
                    (float) (ahead.y - behind.y),
                    (float) (ahead.z - behind.z));
            if (tangent.lengthSquared() < 1.0E-10F) {
                tangent.set(0.0F, 1.0F, 0.0F);
            }
            tangent.normalize();

            Vector3f right;
            if (rolls != null) {
                right = new Vector3f(0.0F, 1.0F, 0.0F).cross(tangent);
                if (right.lengthSquared() < 1.0E-8F) {
                    // Travelling straight up or down, so world up gives no
                    // reference. Keep the previous ring's rather than snapping.
                    right = carried != null
                            ? new Vector3f(carried)
                            : new Vector3f(1.0F, 0.0F, 0.0F);
                }
            } else if (carried == null) {
                // Seed off whichever world axis is least aligned with the tangent,
                // so the first cross product is well conditioned.
                Vector3f seed = Math.abs(tangent.y) < 0.9F
                        ? new Vector3f(0.0F, 1.0F, 0.0F)
                        : new Vector3f(1.0F, 0.0F, 0.0F);
                right = new Vector3f(seed).cross(tangent);
            } else {
                right = new Vector3f(carried).cross(tangent).cross(tangent).negate();
                if (right.lengthSquared() < 1.0E-10F) {
                    Vector3f seed = Math.abs(tangent.y) < 0.9F
                            ? new Vector3f(0.0F, 1.0F, 0.0F)
                            : new Vector3f(1.0F, 0.0F, 0.0F);
                    right = new Vector3f(seed).cross(tangent);
                }
            }
            if (right.lengthSquared() < 1.0E-10F) {
                right.set(1.0F, 0.0F, 0.0F);
            }
            right.normalize();
            Vector3f up = new Vector3f(tangent).cross(right).normalize();

            if (rolls != null && index < rolls.length) {
                float roll = rolls[index];
                float cos = Mth.cos(roll);
                float sin = Mth.sin(roll);
                Vector3f rolledRight = new Vector3f(
                        right.x * cos + up.x * sin,
                        right.y * cos + up.y * sin,
                        right.z * cos + up.z * sin);
                Vector3f rolledUp = new Vector3f(
                        up.x * cos - right.x * sin,
                        up.y * cos - right.y * sin,
                        up.z * cos - right.z * sin);
                right = rolledRight.normalize();
                up = rolledUp.normalize();
            }
            carried = new Vector3f(right);

            rings[index] = new Ring(path[index], radii[index], right, up, tangent);
        }
        return rings;
    }

    /** Parallel-transported frame, for parts with no inherent up. */
    public static Ring[] frames(Vec3[] path, float[] radii) {
        return frames(path, radii, null);
    }

    /**
     * Resolves the frame for a closed loop, returning one more ring than points so the last
     * duplicates the first and the tube joins with no cap.
     *
     * <p>Two things have to be true for a loop to close invisibly, and neither comes free.</p>
     *
     * <p>The tangents at the join must be computed from the cyclic neighbours, not one-sidedly,
     * or the two coincident rings face slightly different ways and the seam shows as a kink. That
     * is what the padding is for: the path is wrapped with its own tail and head so the shared
     * frame code sees real neighbours on both sides of every point.</p>
     *
     * <p>And parallel transport does not come back to where it started. Carrying a reference axis
     * once around a closed curve rotates it by the loop's holonomy, which for these curves is a
     * sizeable fraction of a turn. Snapping the last ring onto the first would dump that entire
     * rotation into the final segment as a visible pinch, so it is spread evenly across every ring
     * instead — each one rolled back by its share.</p>
     *
     * @param loop  points around the loop, each distinct; the closing point is added here rather
     *              than being expected in the input
     * @param radii radius at each point, same length as {@code loop}
     */
    public static Ring[] closedFrames(Vec3[] loop, float[] radii) {
        int count = loop.length;
        if (count < 3) {
            return frames(loop, radii, null);
        }

        // Wrapped by one at the start and two at the end, so indices 1 through count+1 all have a
        // genuine neighbour either side. Index count+1 is the loop's start point again, and it ends
        // up with a tangent identical to index 1 — which is what makes the join exact.
        Vec3[] padded = new Vec3[count + 3];
        float[] paddedRadii = new float[count + 3];
        padded[0] = loop[count - 1];
        paddedRadii[0] = radii[count - 1];
        for (int index = 0; index < count; index++) {
            padded[index + 1] = loop[index];
            paddedRadii[index + 1] = radii[index];
        }
        padded[count + 1] = loop[0];
        paddedRadii[count + 1] = radii[0];
        padded[count + 2] = loop[1];
        paddedRadii[count + 2] = radii[1];

        Ring[] all = frames(padded, paddedRadii, null);
        Ring[] rings = new Ring[count + 1];
        System.arraycopy(all, 1, rings, 0, count + 1);

        float drift = signedAngle(rings[0].right(), rings[count].right(), rings[0].tangent());
        for (int index = 0; index <= count; index++) {
            rings[index] = rolled(rings[index], -drift * index / count);
        }
        return rings;
    }

    /** Rotate a ring's frame about its own tangent, matching the roll convention in frames. */
    private static Ring rolled(Ring ring, float angle) {
        if (angle == 0.0F) {
            return ring;
        }
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        Vector3f right = new Vector3f(
                ring.right().x * cos + ring.up().x * sin,
                ring.right().y * cos + ring.up().y * sin,
                ring.right().z * cos + ring.up().z * sin).normalize();
        Vector3f up = new Vector3f(
                ring.up().x * cos - ring.right().x * sin,
                ring.up().y * cos - ring.right().y * sin,
                ring.up().z * cos - ring.right().z * sin).normalize();
        return new Ring(ring.centre(), ring.radius(), right, up, ring.tangent());
    }

    /** The angle that rotates {@code from} onto {@code to} about {@code axis}, signed. */
    private static float signedAngle(Vector3f from, Vector3f to, Vector3f axis) {
        Vector3f a = new Vector3f(from).normalize();
        Vector3f b = new Vector3f(to).normalize();
        float cos = Mth.clamp(a.dot(b), -1.0F, 1.0F);
        float sin = new Vector3f(a).cross(b).dot(axis);
        return (float) Math.atan2(sin, cos);
    }

    /**
     * Writes the tube's surface. Returns the vertex count added.
     *
     * @param sides     divisions around the circumference
     * @param section   cross-section outline
     * @param mode      shader branch selector, 0 to 1
     * @param aux       spare channel; the tree puts leaf hue here, the snake 0
     * @param intensity brightness, 0 to 1 after the shader's own scaling
     * @param alpha     packed 0-255 fade
     * @param vStart    UV0.x at the first ring
     * @param vEnd      UV0.x at the last ring; may exceed 1 to tile
     */
    public static int emit(BufferBuilder builder, Ring[] rings, int sides,
                           Section section, float mode, float aux, float intensity,
                           int alpha, float vStart, float vEnd) {
        if (rings.length < 2 || sides < 3) {
            return 0;
        }
        Scratch scratch = new Scratch();
        int vertices = 0;

        for (int segment = 0; segment + 1 < rings.length; segment++) {
            Ring a = rings[segment];
            Ring b = rings[segment + 1];
            float sa = segment / (float) (rings.length - 1);
            float sb = (segment + 1) / (float) (rings.length - 1);
            float va = Mth.lerp(sa, vStart, vEnd);
            float vb = Mth.lerp(sb, vStart, vEnd);

            for (int side = 0; side < sides; side++) {
                float angle0 = Mth.TWO_PI * side / sides;
                float angle1 = Mth.TWO_PI * (side + 1) / sides;
                float u0 = side / (float) sides;
                float u1 = (side + 1) / (float) sides;

                corner(builder, a, angle0, sa, section, u0, va, mode, aux, intensity,
                        alpha, scratch);
                corner(builder, b, angle0, sb, section, u0, vb, mode, aux, intensity,
                        alpha, scratch);
                corner(builder, b, angle1, sb, section, u1, vb, mode, aux, intensity,
                        alpha, scratch);
                corner(builder, a, angle1, sa, section, u1, va, mode, aux, intensity,
                        alpha, scratch);
                vertices += 4;
            }
        }
        return vertices;
    }

    /**
     * Caps the end of a tube with a cone, so a tail or a branch tip comes to a
     * point instead of showing a hollow opening.
     */
    public static int emitCap(BufferBuilder builder, Ring ring, Vec3 tip, int sides,
                              Section section, float mode, float aux, float intensity,
                              int alpha, float v) {
        if (sides < 3) {
            return 0;
        }
        Ring point = new Ring(tip, 0.0F, ring.right(), ring.up(), ring.tangent());
        Scratch scratch = new Scratch();
        int vertices = 0;

        for (int side = 0; side < sides; side++) {
            float angle0 = Mth.TWO_PI * side / sides;
            float angle1 = Mth.TWO_PI * (side + 1) / sides;
            float u0 = side / (float) sides;
            float u1 = (side + 1) / (float) sides;

            corner(builder, ring, angle0, 1.0F, section, u0, v, mode, aux, intensity,
                    alpha, scratch);
            corner(builder, point, angle0, 1.0F, section, u0, v, mode, aux, intensity,
                    alpha, scratch);
            corner(builder, point, angle1, 1.0F, section, u1, v, mode, aux, intensity,
                    alpha, scratch);
            corner(builder, ring, angle1, 1.0F, section, u1, v, mode, aux, intensity,
                    alpha, scratch);
            vertices += 4;
        }
        return vertices;
    }

    /** Reusable working vectors, so emitting a corner allocates nothing. */
    private static final class Scratch {
        private final Vector2f section = new Vector2f();
        private final Vector3f position = new Vector3f();
        private final Vector3f normal = new Vector3f();
        private final Vector3f before = new Vector3f();
        private final Vector3f after = new Vector3f();
    }

    /**
     * Resolves the world offset of a section point from its ring.
     */
    private static void surfacePoint(Ring ring, float angle, float alongUnit,
                                     Section section, Vector2f scratch, Vector3f out) {
        section.offset(angle, alongUnit, scratch);
        float x = scratch.x * ring.radius();
        float y = scratch.y * ring.radius();
        out.set(ring.right().x * x + ring.up().x * y,
                ring.right().y * x + ring.up().y * y,
                ring.right().z * x + ring.up().z * y);
    }

    /**
     * One corner, with the true outward normal.
     *
     * <p>The normal is the cross product of the two surface tangents rather than
     * the outward offset. For a circle those coincide; for the snake's flat belly
     * they do not, and using the offset would light the belly plate as though it
     * were curved.</p>
     *
     * <p>Every vector it needs is passed in. A tree is about seven thousand quads,
     * so allocating even one temporary per corner here would be a hundred thousand
     * short-lived objects a frame.</p>
     */
    private static void corner(BufferBuilder builder, Ring ring, float angle,
                               float alongUnit, Section section, float u, float v,
                               float mode, float aux, float intensity, int alpha,
                               Scratch scratch) {
        Vector3f position = scratch.position;
        Vector3f normal = scratch.normal;
        surfacePoint(ring, angle, alongUnit, section, scratch.section, position);

        // Tangent around the circumference, by finite difference on the outline.
        float step = 0.02F;
        surfacePoint(ring, angle - step, alongUnit, section, scratch.section,
                scratch.before);
        surfacePoint(ring, angle + step, alongUnit, section, scratch.section,
                scratch.after);
        Vector3f around = scratch.after.sub(scratch.before);

        if (around.lengthSquared() < 1.0E-12F || ring.radius() < 1.0E-5F) {
            // Degenerate at a cap tip: fall back to the outward offset.
            normal.set(position).normalize();
        } else {
            normal.set(around).cross(ring.tangent());
            if (normal.lengthSquared() < 1.0E-12F) {
                normal.set(position);
            }
            normal.normalize();
            // Make sure it points away from the axis rather than into it.
            if (normal.dot(position) < 0.0F) {
                normal.negate();
            }
        }

        builder.vertex(
                        (float) ring.centre().x + position.x,
                        (float) ring.centre().y + position.y,
                        (float) ring.centre().z + position.z)
                .uv(v, u)
                .color(Math.round(Mth.clamp(aux, 0.0F, 1.0F) * 255.0F),
                        Math.round(Mth.clamp(mode, 0.0F, 1.0F) * 255.0F),
                        Math.round(Mth.clamp(intensity, 0.0F, 1.0F) * 255.0F),
                        alpha)
                .normal(normal.x, normal.y, normal.z)
                .endVertex();
    }
}
