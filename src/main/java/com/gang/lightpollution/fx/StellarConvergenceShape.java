package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.StellarConvergenceParams;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shape and timeline of a stellar convergence, as pure functions.
 *
 * <p>Nothing here touches an entity, a level or a tick counter.</p>
 */
public final class StellarConvergenceShape {
    /** Stars in the net. */
    public static final int STAR_COUNT = 9;
    /** Ticks between one star lighting and the next. */
    public static final int STAR_STAGGER_TICKS = 8;
    /** Ticks by which every star is lit. */
    public static final int LIT_END_TICK = STAR_COUNT * STAR_STAGGER_TICKS;
    /** Ticks at which the net starts contracting. */
    public static final int WEAVE_START_TICK = 90;
    /** Ticks at which the column fires. */
    public static final int BEAM_START_TICK = 150;
    /** Ticks at which it bursts. */
    public static final int BURST_TICK = 190;
    /** Ticks at which the tail fade begins. */
    public static final int FADE_START_TICK = 220;

    /** Radius of the shell the stars sit on, in blocks. */
    public static final float SHELL_RADIUS = 22.0F;
    /** Height of the shell, in blocks. */
    public static final float SHELL_HEIGHT = 26.0F;
    /** Radius of a star, in blocks. */
    public static final float STAR_RADIUS = 1.9F;
    /** Radius of the column, in blocks. */
    public static final double COLUMN_RADIUS = 6.0D;

    /** How far in the net pulls the stars before firing, as a fraction of the shell. */
    public static final double WEAVE_CONTRACTION = 0.62D;

    private StellarConvergenceShape() {
    }

    /** When a star lights, in ticks. */
    public static int litTick(int star) {
        return star * STAR_STAGGER_TICKS;
    }

    /**
     * Where a star is.
     *
     * <p>Seats are a golden-angle lattice, which gives even coverage of the sphere for any count.
     * Only the upper half is used: stars below the centre would be underground.</p>
     */
    public static Vec3 starPosition(StellarConvergenceParams params, Vec3 centre, int star,
                                    float ageTicks) {
        float latitude = 1.0F - 2.0F * (star + 0.5F) / STAR_COUNT;
        float ringRadius = Mth.sqrt(Math.max(0.0F, 1.0F - latitude * latitude));
        float angle = star * 2.39996323F
                + FxHash.unit(params.seed(), star, 0x9E3779B9L) * Mth.TWO_PI * 0.15F
                // The slow drift. This is what makes the coloured shadows move.
                + ageTicks * 0.004F;

        float shell = SHELL_RADIUS * params.scale();
        float y = Math.abs(latitude) * 0.75F;
        Vec3 seat = centre.add(Mth.cos(angle) * ringRadius * shell, y * shell,
                Mth.sin(angle) * ringRadius * shell);

        int lit = litTick(star);
        if (ageTicks < lit) {
            return seat;
        }
        if (ageTicks < BEAM_START_TICK) {
            // Arriving: eases in from further out along its own bearing.
            float settle = smoothstep(Math.min((ageTicks - lit) / 26.0F, 1.0F));
            Vec3 far = centre.add(seat.subtract(centre).scale(2.1D));
            Vec3 arrival = far.lerp(seat, settle);
            if (ageTicks < WEAVE_START_TICK) {
                return arrival;
            }
            // The net contracts, drawing them inward before they fire.
            float pull = smoothstep((ageTicks - WEAVE_START_TICK)
                    / (float) (BEAM_START_TICK - WEAVE_START_TICK));
            return arrival.lerp(centre.add(seat.subtract(centre).scale(WEAVE_CONTRACTION)), pull);
        }
        return centre.add(seat.subtract(centre).scale(WEAVE_CONTRACTION));
    }

    /**
     * The colour of one star, as RGB in 0 to 1.
     *
     * <p>Evenly spaced hues, so nine distinct coloured shadows overlap rather than nine of the same
     * tint.</p>
     */
    public static float[] starColour(int star) {
        return hueToRgb(star / (float) STAR_COUNT);
    }

    /** How bright a star is. Above 1 while it pours into the column, deliberately. */
    public static float starBrightness(int star, float ageTicks) {
        int lit = litTick(star);
        if (ageTicks < lit) {
            return 0.0F;
        }
        if (ageTicks < BEAM_START_TICK) {
            return Math.min(1.0F, (ageTicks - lit) / 14.0F);
        }
        if (ageTicks < BURST_TICK) {
            return 1.0F + (ageTicks - BEAM_START_TICK) * 0.02F;
        }
        return Math.max(0.0F, 1.6F - (ageTicks - BURST_TICK) * 0.06F);
    }

    /** How far the net has contracted, 0 to 1. */
    public static float weaveProgress(float ageTicks) {
        if (ageTicks < WEAVE_START_TICK) {
            return 0.0F;
        }
        if (ageTicks >= BURST_TICK) {
            return Math.max(0.0F, 1.0F - (ageTicks - BURST_TICK) / 20.0F);
        }
        return Mth.clamp((ageTicks - WEAVE_START_TICK)
                / (float) (BEAM_START_TICK - WEAVE_START_TICK), 0.0F, 1.0F);
    }

    /** How strong the column is. Flares on the burst, then dies. */
    public static float columnStrength(float ageTicks) {
        if (ageTicks < BEAM_START_TICK) {
            return 0.0F;
        }
        if (ageTicks < BURST_TICK) {
            return smoothstep((ageTicks - BEAM_START_TICK) / 12.0F);
        }
        return Math.max(0.0F, 2.4F - (ageTicks - BURST_TICK) * 0.09F);
    }

    /** The burst's flash, 0 to 1. */
    public static float burstFlash(float ageTicks) {
        float since = ageTicks - BURST_TICK;
        if (since < 0.0F || since > 16.0F) {
            return 0.0F;
        }
        return 1.0F - since / 16.0F;
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    private static float[] hueToRgb(float hue) {
        float h = (hue % 1.0F) * 6.0F;
        float x = 1.0F - Math.abs(h % 2.0F - 1.0F);
        return switch ((int) h) {
            case 0 -> new float[] {1.0F, x, 0.0F};
            case 1 -> new float[] {x, 1.0F, 0.0F};
            case 2 -> new float[] {0.0F, 1.0F, x};
            case 3 -> new float[] {0.0F, x, 1.0F};
            case 4 -> new float[] {x, 0.0F, 1.0F};
            default -> new float[] {1.0F, 0.0F, x};
        };
    }
}
