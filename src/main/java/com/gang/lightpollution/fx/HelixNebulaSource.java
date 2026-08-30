package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.HelixNebulaParams;
import net.minecraft.world.phys.Vec3;

/**
 * Anything the helix nebula renderer can draw: the spell's anchor entity, or an instance another mod
 * asked for through the API.
 *
 * <p>Method names match the ones the entity already had, so it satisfies this without gaining a line.
 * The shell radius is deliberately <em>not</em> here — it is a pure function of the params and the
 * age, so it belongs to {@link HelixNebulaShape} and having it on both would be two definitions.</p>
 */
public interface HelixNebulaSource {
    /** Where the white dwarf is, interpolated for the frame. */
    Vec3 centre(float partialTick);

    /** How far into its life it is, in ticks, interpolated for the frame. */
    float getVisualAgeTicks(float partialTick);

    /** Overall visibility, 0 to 1, including any fade in or out. */
    float brightness(float partialTick);

    /** The form knobs the shared shape maths needs. */
    HelixNebulaParams shapeParams();
}
