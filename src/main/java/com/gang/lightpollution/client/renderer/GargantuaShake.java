package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.entity.GargantuaEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Camera shake for Gargantua's collapse.
 *
 * <p>Its own class rather than a method on a world renderer, because Gargantua has no
 * world renderer — every pixel of it comes from a screen-space pass, so there is no
 * geometry class for this to live on.</p>
 */
public final class GargantuaShake {
    /** Ticks the shake lasts after the collapse. */
    private static final float SHAKE_TICKS = 22.0F;
    /**
     * Peak amplitude, in degrees. The strongest in the set: this is the only effect
     * whose whole build-up is eleven seconds of something visibly getting away from
     * you, so the release has to land harder than anything else.
     */
    private static final float SHAKE_STRENGTH = 7.0F;
    /** Distance at which the collapse can no longer be felt, in blocks. */
    private static final double SHAKE_RANGE = 90.0D;

    private GargantuaShake() {
    }

    /** Shake from the collapse, 0 when nothing is collapsing. */
    public static float currentShake(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0.0F;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        float shake = 0.0F;
        for (GargantuaEntity entity : SpellLightEmitter.collectGargantuas()) {
            float since = entity.getVisualAgeTicks(partialTick)
                    - GargantuaEntity.BLAST_TICK;
            if (since < 0.0F || since > SHAKE_TICKS) {
                continue;
            }
            float decay = 1.0F - since / SHAKE_TICKS;
            double distance = Math.sqrt(
                    camera.distanceToSqr(entity.centre(partialTick)));
            float reach = (float) Mth.clamp(1.0D - distance / SHAKE_RANGE, 0.0D, 1.0D);
            shake = Math.max(shake, SHAKE_STRENGTH * decay * decay * reach);
        }
        return shake;
    }
}
