package com.gang.lightpollution.performance;

import com.gang.lightpollution.api.CrabNebulaParams;
import com.gang.lightpollution.api.MagnetarParams;
import com.gang.lightpollution.api.MicroquasarParams;
import com.gang.lightpollution.api.PinwheelParams;
import com.gang.lightpollution.fx.CrabNebulaShape;
import com.gang.lightpollution.fx.MagnetarShape;
import com.gang.lightpollution.fx.MicroquasarShape;
import com.gang.lightpollution.fx.PinwheelShape;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Objects;

/** Pure geometry cached only for one damage pulse. Targets and hit results are never retained. */
public final class PulseShapeSamples<P> {
    @FunctionalInterface
    private interface Point<P> {
        Vec3 sample(P params, int index);
    }

    @FunctionalInterface
    private interface Reach<P> {
        double squared(P params, int index);
    }

    private final int size;
    private final Point<P> sampler;
    private final Reach<P> reach;
    private final double constantReachSquared;
    private P parameters;
    private Vec3[] points;
    private double[] reachSquared;

    private PulseShapeSamples(int size, Point<P> sampler, double constantReachSquared, Reach<P> reach) {
        this.size = size;
        this.sampler = sampler;
        this.constantReachSquared = constantReachSquared;
        this.reach = reach;
    }

    /** Original traversal order, including its early exit, expressed as a linear sample index. */
    public int firstHit(P params, Vec3 target) {
        if (points == null) {
            points = new Vec3[size];
            if (reach != null) reachSquared = new double[size];
        } else if (!Objects.equals(parameters, params)) {
            Arrays.fill(points, null);
        }
        parameters = params;
        for (int i = 0; i < size; i++) {
            Vec3 point = points[i];
            if (point == null) {
                if (reach != null) reachSquared[i] = reach.squared(params, i);
                point = sampler.sample(params, i);
                points[i] = point;
            }
            if (point.distanceToSqr(target) <= (reach == null ? constantReachSquared : reachSquared[i])) {
                return i;
            }
        }
        return -1;
    }

    int cachedPointCount() {
        int count = 0;
        if (points != null) for (Vec3 point : points) if (point != null) count++;
        return count;
    }

    public static PulseShapeSamples<CrabNebulaParams> crab(Vec3 centre, float age, double touchSquared) {
        return new PulseShapeSamples<>(CrabNebulaShape.FILAMENTS * 49,
                (params, i) -> CrabNebulaShape.filamentPoint(params, centre, i / 49,
                        (i % 49) / 48.0D, age), touchSquared, null);
    }

    public static PulseShapeSamples<MagnetarParams> magnetar(Vec3 centre, float wound, double touchSquared) {
        return new PulseShapeSamples<>(MagnetarShape.FIELD_LINES * 17,
                (params, i) -> MagnetarShape.fieldPoint(params, centre, i / 17,
                        (i % 17 + 1) / 18.0D, wound), touchSquared, null);
    }

    public static PulseShapeSamples<PinwheelParams> pinwheel(Vec3 centre, double rotation, double touchRadius) {
        return new PulseShapeSamples<>(PinwheelShape.ARMS * 46,
                (params, i) -> PinwheelShape.armPoint(params, centre, i / 46,
                        (i % 46 + 1) / 46.0D, rotation), 0,
                (params, i) -> {
                    double radius = touchRadius + PinwheelShape.armWidth(params, (i % 46 + 1) / 46.0D);
                    return radius * radius;
                });
    }

    public static PulseShapeSamples<MicroquasarParams> microquasar(Vec3 centre, float age) {
        return new PulseShapeSamples<>(2 * MicroquasarShape.BULLETS_PER_JET,
                (params, i) -> MicroquasarShape.bulletPosition(params, centre, age,
                        i < MicroquasarShape.BULLETS_PER_JET, i % MicroquasarShape.BULLETS_PER_JET + 1),
                MicroquasarShape.BEAM_RADIUS * MicroquasarShape.BEAM_RADIUS, null);
    }
}
