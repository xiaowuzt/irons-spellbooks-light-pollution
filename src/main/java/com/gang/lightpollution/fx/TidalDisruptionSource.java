package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.TidalDisruptionParams;
import net.minecraft.world.phys.Vec3;

/**
 * Anything the tidal disruption renderer can draw.
 *
 * <p>Two things implement this: the spell's anchor entity, and an instance another mod asked for
 * through the API. The renderer sees only this, which is what lets one renderer serve both without
 * knowing that either exists.</p>
 *
 * <p>The method names are the ones the entity already had, so it satisfies this interface without
 * gaining a single line. That was deliberate — the point of this step is to introduce the seam, not
 * to churn the code on either side of it.</p>
 */
public interface TidalDisruptionSource {
    /** Where the hole is, interpolated for the frame. */
    Vec3 centre(float partialTick);

    /** How far into its life it is, in ticks, interpolated for the frame. */
    float getVisualAgeTicks(float partialTick);

    /** Overall visibility, 0 to 1, including any fade in or out. */
    float brightness(float partialTick);

    /** The fallback flare, 0 to 1. */
    float flare(float partialTick);

    /** The form knobs the shared shape maths needs. */
    TidalDisruptionParams shapeParams();
}
