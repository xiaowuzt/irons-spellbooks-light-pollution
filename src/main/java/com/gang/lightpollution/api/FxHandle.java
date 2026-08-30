package com.gang.lightpollution.api;

import net.minecraft.world.phys.Vec3;

/**
 * A handle to one effect this mod is drawing on behalf of another mod.
 *
 * <p>Held by whoever asked for the effect, so they can move it or take it away again. Everything
 * else — which render stage to draw at, how to survive a shader pack's compositing, which shader and
 * buffer to use — stays on this side, because that is the part which took several attempts to get
 * right and should not be re-derived by every caller.</p>
 *
 * <p>Client-side only. These effects are drawn, not simulated, so nothing here reaches the server. A
 * mod that wants other players to see an effect has to sync it itself; this cannot do that for you,
 * and pretending otherwise would produce something that works in single player and silently fails in
 * multiplayer.</p>
 */
public interface FxHandle {
    /** Move the effect. Cheap; safe to call every frame. */
    void setPosition(Vec3 at);

    /** Stop drawing it. Idempotent. */
    void remove();

    /**
     * Whether it is still being drawn.
     *
     * <p>False once {@link #remove} has been called, once its lifetime has run out, or once the
     * level it belongs to has been unloaded.</p>
     */
    boolean isAlive();
}
