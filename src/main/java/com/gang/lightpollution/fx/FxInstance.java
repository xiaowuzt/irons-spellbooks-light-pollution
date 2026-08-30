package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.FxHandle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The part of an API-requested effect that has nothing to do with which effect it is.
 *
 * <p>Where it is, how old it is, whether it is still wanted, and how it fades in and out. Each effect
 * differs only in the params it carries and how long its fade in should be, so the third copy of this
 * bookkeeping was the point to stop copying.</p>
 *
 * <p>An instance is both the thing being drawn and the handle the caller holds, which is why they are
 * one object: moving it is a field write rather than a lookup.</p>
 *
 * <p>Ages in ticks like a spell's anchor entity, but off its own counter rather than the level's game
 * time. A caller may create one at any moment, and anchoring to game time would make the first frame
 * land at an arbitrary point in the animation.</p>
 */
abstract class FxInstance implements FxHandle, FxRegistry.Advanceable {
    /** Ticks to fade out over, at the end of a bounded lifetime. */
    private static final int FADE_OUT = 40;

    private final int fadeInTicks;
    private final int lifetimeTicks;
    private volatile Vec3 at;
    private int ticks;
    private int previousTicks;
    private boolean removed;

    FxInstance(Vec3 at, int fadeInTicks, int lifetimeTicks) {
        this.at = at;
        this.fadeInTicks = fadeInTicks;
        this.lifetimeTicks = lifetimeTicks;
    }

    @Override
    public final boolean advance() {
        previousTicks = ticks;
        ticks++;
        return isAlive();
    }

    @Override
    public final void setPosition(Vec3 position) {
        this.at = position;
    }

    @Override
    public final void remove() {
        this.removed = true;
    }

    @Override
    public final boolean isAlive() {
        if (removed) {
            return false;
        }
        return lifetimeTicks <= 0 || ticks < lifetimeTicks;
    }

    /**
     * Where it is.
     *
     * <p>No interpolation of the position itself: a caller that moves this every frame has already
     * decided where it is this frame, and lerping toward a value it set last tick would lag it.</p>
     */
    public final Vec3 centre(float partialTick) {
        return at;
    }

    public final float getVisualAgeTicks(float partialTick) {
        return Mth.lerp(partialTick, previousTicks, ticks);
    }

    /**
     * A generic fade in, hold, fade out.
     *
     * <p>Not final, because an effect whose own timeline decides its brightness should say so — the
     * second sun overshoots above 1 during its nova, and clamping that to a generic envelope would
     * throw away the flare.</p>
     */
    public float brightness(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        float in = Mth.clamp(age / fadeInTicks, 0.0F, 1.0F);
        if (lifetimeTicks <= 0) {
            return in;
        }
        float remaining = lifetimeTicks - age;
        return in * Mth.clamp(remaining / FADE_OUT, 0.0F, 1.0F);
    }
}
