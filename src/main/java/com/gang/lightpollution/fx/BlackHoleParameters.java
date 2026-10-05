package com.gang.lightpollution.fx;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** Shared immutable snapshot. Distances: blocks (= metres); time: ticks, 20 ticks/s.
 * Only server snapshots define gameplay. Client quality/exposure never feed this record. */
public record BlackHoleParameters(UUID bhInstanceId, Vec3 bhCenter, float bhHorizonRadius,
        float bhInfluenceRadius, Vec3 bhDiskNormal, long bhStartTick, int bhLifetimeTicks,
        float bhAgeTicks, float bhEnvelope, float bhTimeScale, int bhSeed) {
    /** Geometric mass GM/c^2 in blocks, not a Newtonian gameplay force or SI kilograms. */
    public float bhGeometricMass() { return bhHorizonRadius * .5F; }
    public float bhTimeSeconds() { return bhAgeTicks / 20.0F; }
    public BlackHoleParameters at(Vec3 center, float horizon, float influence) {
        return new BlackHoleParameters(bhInstanceId, center, horizon, influence, bhDiskNormal,
                bhStartTick, bhLifetimeTicks, bhAgeTicks, bhEnvelope, bhTimeScale, bhSeed);
    }
}
