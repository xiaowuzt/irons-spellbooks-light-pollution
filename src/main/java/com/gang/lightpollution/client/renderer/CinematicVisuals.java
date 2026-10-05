package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.SpellConfig;
import com.gang.lightpollution.api.PinwheelParams;
import com.gang.lightpollution.entity.*;
import com.gang.lightpollution.fx.*;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;

/** Client-only composition and timing. The damage-reference *Shape curves are never rewritten. */
public final class CinematicVisuals {
    private static final java.util.Map<Object, Float> API_SEEDS = new java.util.WeakHashMap<>();
    private static int nextSeed;

    private CinematicVisuals() { }

    public record Stages(float age, int lifetime, int formEnd, int release, int fadeStart) {
        public float formed() { return VisualEnvelope.smooth(age / Math.max(1, formEnd)); }
        public float flash(float ticks) { return VisualEnvelope.impulse(age, release, ticks); }
        public float collapse(float ticks) {
            return VisualEnvelope.smooth(VisualEnvelope.progress(age, release, release + ticks));
        }
        public float tail() { return VisualEnvelope.tail(age, lifetime, 30); }
    }

    public static Stages tidal(TidalDisruptionSource source, float partial) {
        float age = source.getVisualAgeTicks(partial);
        if (source instanceof TidalDisruptionEntity) return configured("tidalDisruption", age, 3);
        var p = source.shapeParams();
        return new Stages(age, p.lifetimeTicks(), TidalDisruptionShape.STRETCH_END_TICK,
                p.flareStartTick(), p.flareStartTick());
    }

    public static Stages helix(HelixNebulaSource source, float partial) {
        float age = source.getVisualAgeTicks(partial);
        if (source instanceof HelixNebulaEntity) return configured("helixNebula", age, 2);
        int life = source.shapeParams().lifetimeTicks();
        return new Stages(age, life, HelixNebulaShape.IGNITION_END_TICK,
                life <= 0 ? Integer.MAX_VALUE : Math.max(1, life - 60),
                life <= 0 ? Integer.MAX_VALUE : Math.max(1, life - 60));
    }

    public static Stages crab(CrabNebulaSource source, float partial) {
        float age = source.getVisualAgeTicks(partial);
        if (source instanceof CrabNebulaEntity) return configured("crabNebula", age, 2);
        int life = source.shapeParams().lifetimeTicks();
        int end = life <= 0 ? Integer.MAX_VALUE : Math.min(CrabNebulaShape.WIND_END_TICK, life);
        return new Stages(age, life, CrabNebulaShape.FORM_END_TICK, end, end);
    }

    public static Stages pinwheel(PinwheelSource source, float partial) {
        float age = source.getVisualAgeTicks(partial);
        // resolveFlare() is called at phase TWO. Phase three is not a damage event here.
        if (source instanceof PinwheelEntity) return configured("pinwheel", age, 2);
        int life = source.shapeParams().lifetimeTicks();
        int end = life <= 0 ? Integer.MAX_VALUE : Math.min(PinwheelShape.SPIN_END_TICK, life);
        return new Stages(age, life, PinwheelShape.SPIN_UP_END_TICK, end, end);
    }

    private static Stages configured(String id, float age, int eventPhase) {
        return new Stages(age, SpellConfig.lifetimeTicks(id), SpellConfig.phaseTick(id, 1),
                SpellConfig.phaseTick(id, eventPhase), SpellConfig.phaseTick(id, 2));
    }

    public static int constellationLifetime(ConstellationSource source) {
        return source instanceof ConstellationEntity ? SpellConfig.lifetimeTicks("constellation")
                : source.shapeParams().lifetimeTicks();
    }

    public static float burnPulse(ConstellationSource source, float partial) {
        float age = source.getVisualAgeTicks(partial);
        if (age <= ConstellationShape.GATHER_END_TICK) return 0.0F;
        int interval = source instanceof ConstellationEntity
                ? SpellConfig.damageIntervalTicks("constellation") : 10;
        return VisualEnvelope.pulse(age, 0.0F, 1.0F, Math.max(1, interval));
    }

    public static Vec3 tidalStar(TidalDisruptionSource source, float partial) {
        Stages t = tidal(source, partial);
        // The stellar remnant becomes the trailing end of the SAME authoritative stream.
        double fraction = 0.80D + 0.20D * t.formed();
        return TidalDisruptionShape.streamPoint(source.shapeParams(), source.centre(partial),
                t.age(), fraction);
    }

    public static Vec3 tidalHotspot(TidalDisruptionSource source, float partial) {
        return TidalDisruptionShape.streamPoint(source.shapeParams(), source.centre(partial),
                source.getVisualAgeTicks(partial), 0.14D);
    }

    public static Vec3 pinwheelSwing(PinwheelParams params, float age) {
        Vec3 normal = PinwheelShape.planeNormal(params);
        Vec3 u = planeU(normal);
        return u.scale(Math.cos(PinwheelShape.rotation(age)) * 1.8D * params.scale())
                .add(normal.cross(u).scale(Math.sin(PinwheelShape.rotation(age)) * 1.8D * params.scale()));
    }

    public static Vec3 planeU(Vec3 normal) {
        Vec3 u = normal.cross(new Vec3(0, 1, 0));
        return (u.lengthSqr() < 1.0e-8 ? normal.cross(new Vec3(1, 0, 0)) : u).normalize();
    }

    /** Stable on camera motion and distinct for concurrent casts; no Java object identity hashes. */
    public static float seed(Object source, Vec3 centre) {
        if (source instanceof net.minecraft.world.entity.Entity e) return e.getId() % 8192;
        // Moving an API handle must not boil/reseed its material. Weak keys do not retain removed effects.
        return API_SEEDS.computeIfAbsent(source, ignored -> (float) (nextSeed = (nextSeed + 137) % 8192));
    }

    /** Must be set on EVERY draw: ShaderInstances are shared by all spell instances. */
    public static void strand(ShaderInstance shader, float age, float seed, Vec3 relativeCentre) {
        uniform(shader, "EffectTime", age * 0.05F);
        uniform(shader, "EffectSeed", seed);
        if (shader.getUniform("EffectOrigin") != null) {
            shader.getUniform("EffectOrigin").set((float) relativeCentre.x,
                    (float) relativeCentre.y, (float) relativeCentre.z);
        }
    }

    public static void uniform(ShaderInstance shader, String name, float value) {
        if (shader.getUniform(name) != null) shader.getUniform(name).set(value);
    }
}
