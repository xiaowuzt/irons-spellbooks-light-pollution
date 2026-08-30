package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.FuneralNovaParams;
import com.gang.lightpollution.api.FxHandle;
import com.gang.lightpollution.client.renderer.GeminiKillEffectVisualInstance;
import net.minecraft.world.phys.Vec3;

/**
 * One funeral nova another mod asked for, rather than one a spell caused.
 *
 * <p>Keeps its own age, unlike {@link EclipseSeveranceInstance}. That one reads a monotonic clock it
 * started itself; this state machine has no clock at all — it is stepped by whoever owns it calling
 * {@code advanceTo}, which the spell's renderer does from the anchor entity's tick age. With no entity
 * to read, the age has to live here.</p>
 *
 * <p>This one can be moved, unlike the sweep: the stages are drawn relative to the centre each frame
 * and the state machine has a setter for it.</p>
 */
final class FuneralNovaInstance implements FuneralNovaSource, FxHandle, FxRegistry.Advanceable {
    private final GeminiKillEffectVisualInstance visual;
    private int ticks;
    private boolean removed;

    FuneralNovaInstance(Vec3 at, FuneralNovaParams params) {
        this.visual = new GeminiKillEffectVisualInstance(at, params.seed());
    }

    @Override
    public GeminiKillEffectVisualInstance visual() {
        return visual;
    }

    /** Seconds since it started. 20 ticks per second. */
    float ageSeconds() {
        return ticks / 20.0F;
    }

    @Override
    public boolean advance() {
        ticks++;
        visual.advanceTo(ageSeconds());
        return isAlive();
    }

    @Override
    public void setPosition(Vec3 position) {
        visual.setPosition(position);
    }

    @Override
    public void remove() {
        this.removed = true;
    }

    @Override
    public boolean isAlive() {
        return !removed && ticks < FuneralNovaParams.DURATION_TICKS;
    }
}
