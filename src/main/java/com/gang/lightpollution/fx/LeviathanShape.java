package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.LeviathanParams;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shape and timeline of a leviathan's approach, rear and bite, as pure functions.
 *
 * <p>Nothing here touches an entity, a level or a tick counter.</p>
 */
public final class LeviathanShape {
    /** Ticks by which the body has fully arrived. */
    public static final int APPROACH_END_TICK = 70;
    /** Ticks by which it has filled out. */
    public static final int FLESH_END_TICK = 95;
    /** Ticks by which it has finished rearing. */
    public static final int REAR_END_TICK = 120;
    /** Ticks at which the jaws close. */
    public static final int BITE_TICK = 128;
    /** Ticks at which it starts coming apart. */
    public static final int UNRAVEL_START_TICK = 140;

    /** Segments along the spine. */
    public static final int SPINE_SEGMENTS = 48;
    /** How long the body is, in blocks. */
    public static final float BODY_LENGTH = 90.0F;
    /** How far the body swings sideways at the tail, in blocks. */
    public static final float SWING_AMPLITUDE = 14.0F;
    /** How far it rises and falls, in blocks. */
    public static final float VERTICAL_AMPLITUDE = 3.0F;
    /** Undulation waves visible along the body at once. */
    public static final float UNDULATION_WAVES = 1.6F;
    /** How high the body swims above the anchor, in blocks. */
    public static final float SWIM_HEIGHT = 16.0F;
    /** Radius of the body at its thickest, in blocks. */
    public static final float BODY_RADIUS = 2.0F;
    /** How much taller than wide a body section is. */
    public static final float SECTION_HEIGHT_RATIO = 1.05F;
    /** How much the underside is flattened. */
    public static final float SECTION_VENTRAL_TAPER = 0.30F;
    /** How high the front third rears, in blocks. */
    public static final float REAR_HEIGHT = 22.0F;

    /**
     * How much of the swing amplitude the head keeps.
     *
     * <p>Small but not zero, so the head is not pinned dead still.</p>
     */
    public static final float HEAD_AMPLITUDE_SHARE = 0.08F;
    /** How far the body banks into its own turns, in radians. */
    public static final float BANK_ANGLE = 0.38F;

    private LeviathanShape() {
    }

    /** How far the body has arrived, 0 to 1. */
    public static float extended(float ageTicks) {
        if (ageTicks >= APPROACH_END_TICK) {
            return 1.0F;
        }
        return smoothstep(ageTicks / APPROACH_END_TICK);
    }

    /** How far the body has filled out, 0 to 1. */
    public static float flesh(float ageTicks) {
        if (ageTicks <= APPROACH_END_TICK * 0.5F) {
            return 0.0F;
        }
        return Mth.clamp((ageTicks - APPROACH_END_TICK * 0.5F)
                / (FLESH_END_TICK - APPROACH_END_TICK * 0.5F), 0.0F, 1.0F);
    }

    /** How far it has reared, 0 to 1. */
    public static float rear(float ageTicks) {
        if (ageTicks <= FLESH_END_TICK) {
            return 0.0F;
        }
        if (ageTicks >= REAR_END_TICK) {
            return 1.0F;
        }
        return smoothstep((ageTicks - FLESH_END_TICK) / (REAR_END_TICK - FLESH_END_TICK));
    }

    /** How far into the strike it is, 0 to 1. Accelerating: a strike is a snap, not a lean. */
    public static float strike(float ageTicks) {
        if (ageTicks <= REAR_END_TICK) {
            return 0.0F;
        }
        if (ageTicks >= BITE_TICK) {
            return 1.0F;
        }
        float raw = (ageTicks - REAR_END_TICK) / (BITE_TICK - REAR_END_TICK);
        return raw * raw;
    }

    /** How far the jaws are open, 0 to 1. */
    public static float jawOpen(float ageTicks) {
        if (ageTicks <= FLESH_END_TICK) {
            return 0.0F;
        }
        if (ageTicks < REAR_END_TICK) {
            // Opens as it rears.
            return smoothstep((ageTicks - FLESH_END_TICK) / (REAR_END_TICK - FLESH_END_TICK));
        }
        if (ageTicks < BITE_TICK) {
            // Snaps shut. Fast, because the snap is the beat the whole thing is for.
            return 1.0F - smoothstep((ageTicks - REAR_END_TICK) / (BITE_TICK - REAR_END_TICK));
        }
        return 0.0F;
    }

    /** How far it has come apart, 0 to 1. */
    public static float unravel(float ageTicks, int lifetimeTicks) {
        if (ageTicks <= UNRAVEL_START_TICK) {
            return 0.0F;
        }
        return Mth.clamp((ageTicks - UNRAVEL_START_TICK)
                / (float) (lifetimeTicks - UNRAVEL_START_TICK), 0.0F, 1.0F);
    }

    /** How bright it is. Above 1 on the bite, deliberately. */
    public static float brightness(float ageTicks, int lifetimeTicks) {
        if (ageTicks < APPROACH_END_TICK) {
            return 0.35F + extended(ageTicks) * 0.65F;
        }
        if (ageTicks < BITE_TICK) {
            return 1.0F;
        }
        if (ageTicks < UNRAVEL_START_TICK) {
            return 1.0F + (BITE_TICK + 12.0F - ageTicks) * 0.09F;
        }
        return Math.max(0.0F, 1.0F - unravel(ageTicks, lifetimeTicks));
    }

    /** The bite's flash, 0 to 1. */
    public static float biteFlash(float ageTicks) {
        float since = ageTicks - BITE_TICK;
        if (since < 0.0F || since > 14.0F) {
            return 0.0F;
        }
        return 1.0F - since / 14.0F;
    }

    /**
     * A point along the spine.
     *
     * @param t 0 at the snout, 1 at the tail tip
     */
    public static Vec3 spinePoint(LeviathanParams params, Vec3 anchor, float t, float ageTicks) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        float sc = params.scale();
        float bearing = params.bearingRadians();
        float dirX = Mth.cos(bearing);
        float dirZ = Mth.sin(bearing);
        float sideX = -dirZ;
        float sideZ = dirX;

        float along = -clamped * BODY_LENGTH * sc;
        // Negative, so the head starts behind the caster and travels forward past the aimed point.
        // With this added instead of subtracted the head began in front of the target and moved
        // backward, which put the tail at the leading end — the body arrived tail first.
        along -= (1.0F - extended(ageTicks)) * BODY_LENGTH * sc * 1.6F;

        // The wave travels tail-ward: subtracting time from the position term is what makes the
        // body slither rather than sway.
        float wave = wavePhase(params, clamped, ageTicks);

        // Lateral swing. Amplitude grows toward the tail as s^1.5, which is the measured envelope
        // for anguilliform swimming, plus a small constant so the head is not pinned dead still.
        // The old clamp(t * 2.2) saturated a third of the way along and was flat after that, so the
        // back two thirds of the body all swung by the same amount — a flag, not an animal.
        float envelope = amplitudeEnvelope(clamped);
        float swing = Mth.sin(wave) * SWING_AMPLITUDE * sc * envelope;

        // A smaller vertical wave a quarter cycle out of phase. Without this the body is a flat
        // ribbon of motion however round the mesh is.
        float bob = Mth.sin(wave + Mth.HALF_PI) * VERTICAL_AMPLITUDE * sc * envelope;

        float rear = rear(ageTicks);
        // Only the front third rears and strikes; the rest holds its swimming height, which is what
        // makes the motion read as a strike rather than the whole animal moving up and down.
        float headBias = Math.max(0.0F, 1.0F - clamped * 3.0F);
        float lift = REAR_HEIGHT * sc * rear * headBias;
        // Brings the jaws all the way down onto the aimed point.
        float plunge = strike(ageTicks) * (SWIM_HEIGHT + REAR_HEIGHT * rear) * sc * headBias;

        return anchor.add(dirX * along + sideX * swing,
                SWIM_HEIGHT * sc + lift + bob - plunge,
                dirZ * along + sideZ * swing);
    }

    /**
     * Radius of the body at a point along it, in blocks.
     *
     * <p>Piecewise rather than a single curve because the features are real anatomy: a rostral tip, a
     * skull broader than the neck behind it, the neck pinch that makes a head read as a head, a
     * plateau, then a tail that whips to a point.</p>
     *
     * @param t 0 at the snout, 1 at the tail tip
     */
    public static float bodyRadius(LeviathanParams params, float t, float ageTicks,
                                   int lifetimeTicks) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        float profile;
        if (clamped < 0.02F) {
            profile = 0.30F + clamped / 0.02F * 0.25F;
        } else if (clamped < 0.055F) {
            profile = 0.55F + Mth.sin((clamped - 0.02F) / 0.035F * Mth.PI) * 0.40F;
        } else if (clamped < 0.10F) {
            profile = 0.95F - (clamped - 0.055F) / 0.045F * 0.27F;
        } else if (clamped < 0.15F) {
            profile = 0.68F + (clamped - 0.10F) / 0.05F * 0.32F;
        } else if (clamped < 0.55F) {
            // A few percent of drift so it is not a machined cylinder.
            profile = 1.0F - 0.03F * Mth.sin((clamped - 0.15F) / 0.40F * Mth.PI);
        } else if (clamped < 0.85F) {
            float back = (clamped - 0.55F) / 0.30F;
            profile = 1.0F - back * back * (3.0F - 2.0F * back) * 0.45F;
        } else {
            // The tail, 15% of total length. Power falloff, so it thins slowly at first.
            float tail = (clamped - 0.85F) / 0.15F;
            profile = 0.55F * (1.0F - (float) Math.pow(tail, 2.2D)) + 0.02F;
        }
        return BODY_RADIUS * params.scale() * profile
                * (1.0F - unravel(ageTicks, lifetimeTicks) * 0.6F);
    }

    /** How far the body has banked at a point along it, in radians. */
    public static float roll(LeviathanParams params, float t, float ageTicks) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        return Mth.cos(wavePhase(params, clamped, ageTicks)) * BANK_ANGLE
                * amplitudeEnvelope(clamped);
    }

    private static float amplitudeEnvelope(float clamped) {
        return HEAD_AMPLITUDE_SHARE
                + (1.0F - HEAD_AMPLITUDE_SHARE) * (float) Math.pow(clamped, 1.5D);
    }

    private static float wavePhase(LeviathanParams params, float t, float ageTicks) {
        float phase = FxHash.unit(params.seed(), 0, 0x9E3779B9L) * Mth.TWO_PI;
        return t * Mth.PI * UNDULATION_WAVES * 2.0F - ageTicks * 0.16F + phase;
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }
}
