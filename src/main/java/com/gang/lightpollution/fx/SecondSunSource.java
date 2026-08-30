package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.SecondSunParams;

/**
 * Anything the second sun renderer can draw: the spell's anchor entity, or an instance another mod
 * asked for through the API.
 *
 * <p>No {@code centre} here, unlike every other source. This one is drawn as sky — placed along a
 * bearing at whatever distance the renderer picks — so it has no position in the world, and giving it
 * one would be a field nothing reads.</p>
 */
public interface SecondSunSource {
    /** How far into its life it is, in ticks, interpolated for the frame. */
    float getVisualAgeTicks(float partialTick);

    /** Overall visibility, including the nova's overshoot above 1. */
    float brightness(float partialTick);

    /** The form knobs the shared shape maths needs. */
    SecondSunParams shapeParams();
}
