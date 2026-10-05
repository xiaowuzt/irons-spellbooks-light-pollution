package com.gang.lightpollution.fx;

import net.minecraft.world.entity.LivingEntity;

/** Implemented by world anchors, not renderers. UUID guards entity-id reuse on reconnect. */
public interface BlackHoleInstance {
    BlackHoleParameters bhParameters(float partialTick);
    boolean bhIsDisplay();
    boolean bhIsRoot();
    boolean bhHasGravity();
    boolean bhOwnedBy(LivingEntity caster);
}
