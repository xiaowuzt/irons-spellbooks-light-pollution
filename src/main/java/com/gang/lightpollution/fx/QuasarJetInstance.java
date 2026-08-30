package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.QuasarJetParams;
import net.minecraft.world.phys.Vec3;

/** One quasarjet another mod asked for, rather than one a spell caused. */
final class QuasarJetInstance extends FxInstance implements QuasarJetSource {
    /** Ticks to fade in over. */
    private static final int FADE_IN = 10;

    private final QuasarJetParams params;

    QuasarJetInstance(Vec3 at, QuasarJetParams params) {
        super(at, FADE_IN, params.lifetimeTicks());
        this.params = params;
    }

    @Override
    public QuasarJetParams shapeParams() {
        return params;
    }

    @Override
    public float launched(float partialTick) {
        return QuasarJetShape.launched(getVisualAgeTicks(partialTick));
    }
}
